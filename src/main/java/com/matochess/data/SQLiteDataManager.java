package com.matochess.data;

import com.matochess.MatoChessPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages database connections and player profile data using SQLite
 * No external database server required - uses file-based storage
 */
public class SQLiteDataManager {

    private final MatoChessPlugin plugin;
    private Connection connection;
    private final Map<UUID, PlayerProfile> profileCache;
    private final File databaseFile;

    public SQLiteDataManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.profileCache = new HashMap<>();
        this.databaseFile = new File(plugin.getDataFolder(), "matochess.db");
    }

    /**
     * Initialize database connection and create tables
     */
    public boolean initialize() {
        try {
            // Create plugin folder if not exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create connection
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            // Create tables
            createTables();

            plugin.getLogger().info("SQLite database initialized successfully.");
            plugin.getLogger().info("Database location: " + databaseFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
            return false;
        }
    }

    /**
     * Create database tables if they don't exist
     */
    private void createTables() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS matochess_players (
                player_id TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                tier TEXT NOT NULL DEFAULT 'COPPER',
                division INTEGER NOT NULL DEFAULT 5,
                rating_points INTEGER NOT NULL DEFAULT 0,
                games_played INTEGER NOT NULL DEFAULT 0,
                wins INTEGER NOT NULL DEFAULT 0,
                top4 INTEGER NOT NULL DEFAULT 0,
                total_placement INTEGER NOT NULL DEFAULT 0,
                first_played INTEGER NOT NULL,
                last_played INTEGER NOT NULL
            );
        """;

        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_tier
            ON matochess_players(tier, division, rating_points);

            CREATE INDEX IF NOT EXISTS idx_player_name
            ON matochess_players(player_name);
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createIndexes);
            plugin.getLogger().info("Database tables created successfully.");
        }
    }

    /**
     * Load player profile from database or create new one
     */
    public CompletableFuture<PlayerProfile> loadProfile(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (profileCache.containsKey(playerId)) {
                return profileCache.get(playerId);
            }

            try {
                // Try to load from database
                String selectQuery = "SELECT * FROM matochess_players WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
                    stmt.setString(1, playerId.toString());

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        // Load existing profile
                        PlayerProfile profile = new PlayerProfile(playerId, rs.getString("player_name"));
                        profile.setTier(Tier.valueOf(rs.getString("tier")));
                        profile.setDivision(rs.getInt("division"));
                        profile.setRatingPoints(rs.getInt("rating_points"));
                        profile.setGamesPlayed(rs.getInt("games_played"));
                        profile.setWins(rs.getInt("wins"));
                        profile.setTop4(rs.getInt("top4"));
                        profile.setTotalPlacement(rs.getInt("total_placement"));
                        profile.setFirstPlayed(rs.getLong("first_played"));
                        profile.setLastPlayed(rs.getLong("last_played"));

                        // Update name if changed
                        if (!profile.getPlayerName().equals(playerName)) {
                            profile.setPlayerName(playerName);
                            saveProfile(profile);
                        }

                        profileCache.put(playerId, profile);
                        return profile;
                    } else {
                        // Create new profile
                        PlayerProfile profile = new PlayerProfile(playerId, playerName);
                        saveProfile(profile);
                        profileCache.put(playerId, profile);
                        return profile;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player profile: " + playerId, e);
                return new PlayerProfile(playerId, playerName);
            }
        });
    }

    /**
     * Save player profile to database
     */
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                String upsertQuery = """
                    INSERT INTO matochess_players
                    (player_id, player_name, tier, division, rating_points, games_played,
                     wins, top4, total_placement, first_played, last_played)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_id) DO UPDATE SET
                    player_name = excluded.player_name,
                    tier = excluded.tier,
                    division = excluded.division,
                    rating_points = excluded.rating_points,
                    games_played = excluded.games_played,
                    wins = excluded.wins,
                    top4 = excluded.top4,
                    total_placement = excluded.total_placement,
                    last_played = excluded.last_played
                """;

                try (PreparedStatement stmt = connection.prepareStatement(upsertQuery)) {
                    stmt.setString(1, profile.getPlayerId().toString());
                    stmt.setString(2, profile.getPlayerName());
                    stmt.setString(3, profile.getTier().name());
                    stmt.setInt(4, profile.getDivision());
                    stmt.setInt(5, profile.getRatingPoints());
                    stmt.setInt(6, profile.getGamesPlayed());
                    stmt.setInt(7, profile.getWins());
                    stmt.setInt(8, profile.getTop4());
                    stmt.setInt(9, profile.getTotalPlacement());
                    stmt.setLong(10, profile.getFirstPlayed());
                    stmt.setLong(11, profile.getLastPlayed());

                    stmt.executeUpdate();
                }

                // Update cache
                profileCache.put(profile.getPlayerId(), profile);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player profile: " + profile.getPlayerId(), e);
            }
        });
    }

    /**
     * Get top players by total rating
     */
    public CompletableFuture<List<PlayerProfile>> getLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerProfile> leaderboard = new ArrayList<>();

            try {
                String query = """
                    SELECT * FROM matochess_players
                    ORDER BY
                        CASE tier
                            WHEN 'ENDER' THEN 7
                            WHEN 'NETHERITE' THEN 6
                            WHEN 'DIAMOND' THEN 5
                            WHEN 'EMERALD' THEN 4
                            WHEN 'GOLD' THEN 3
                            WHEN 'SILVER' THEN 2
                            WHEN 'COPPER' THEN 1
                        END DESC,
                        division ASC,
                        rating_points DESC
                    LIMIT ?
                """;

                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setInt(1, limit);

                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("player_id"));
                        PlayerProfile profile = new PlayerProfile(playerId, rs.getString("player_name"));
                        profile.setTier(Tier.valueOf(rs.getString("tier")));
                        profile.setDivision(rs.getInt("division"));
                        profile.setRatingPoints(rs.getInt("rating_points"));
                        profile.setGamesPlayed(rs.getInt("games_played"));
                        profile.setWins(rs.getInt("wins"));
                        profile.setTop4(rs.getInt("top4"));
                        profile.setTotalPlacement(rs.getInt("total_placement"));
                        profile.setFirstPlayed(rs.getLong("first_played"));
                        profile.setLastPlayed(rs.getLong("last_played"));

                        leaderboard.add(profile);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch leaderboard", e);
            }

            return leaderboard;
        });
    }

    /**
     * Get cached profile (non-async)
     */
    public PlayerProfile getCachedProfile(UUID playerId) {
        return profileCache.get(playerId);
    }

    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }
}
