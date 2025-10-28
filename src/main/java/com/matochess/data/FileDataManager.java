package com.matochess.data;

import com.matochess.MatoChessPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages player profile data using YAML files
 * Alternative to DatabaseManager for easier setup
 */
public class FileDataManager {

    private final MatoChessPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, PlayerProfile> profileCache;

    public FileDataManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        this.profileCache = new HashMap<>();
    }

    /**
     * Initialize file data manager
     */
    public boolean initialize() {
        try {
            // Create data folder if not exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Create players.yml if not exists
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }

            // Load configuration
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            plugin.getLogger().info("File data manager initialized successfully.");
            return true;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize file data manager", e);
            return false;
        }
    }

    /**
     * Load player profile from file or create new one
     */
    public CompletableFuture<PlayerProfile> loadProfile(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (profileCache.containsKey(playerId)) {
                return profileCache.get(playerId);
            }

            String path = "players." + playerId.toString();

            if (dataConfig.contains(path)) {
                // Load existing profile
                PlayerProfile profile = new PlayerProfile(playerId,
                    dataConfig.getString(path + ".name", playerName));

                profile.setTier(Tier.valueOf(dataConfig.getString(path + ".tier", "COPPER")));
                profile.setDivision(dataConfig.getInt(path + ".division", 5));
                profile.setRatingPoints(dataConfig.getInt(path + ".rating-points", 0));
                profile.setGamesPlayed(dataConfig.getInt(path + ".games-played", 0));
                profile.setWins(dataConfig.getInt(path + ".wins", 0));
                profile.setTop4(dataConfig.getInt(path + ".top4", 0));
                profile.setTotalPlacement(dataConfig.getInt(path + ".total-placement", 0));
                profile.setFirstPlayed(dataConfig.getLong(path + ".first-played", System.currentTimeMillis()));
                profile.setLastPlayed(dataConfig.getLong(path + ".last-played", System.currentTimeMillis()));

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
        });
    }

    /**
     * Save player profile to file
     */
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String path = "players." + profile.getPlayerId().toString();

            dataConfig.set(path + ".name", profile.getPlayerName());
            dataConfig.set(path + ".tier", profile.getTier().name());
            dataConfig.set(path + ".division", profile.getDivision());
            dataConfig.set(path + ".rating-points", profile.getRatingPoints());
            dataConfig.set(path + ".games-played", profile.getGamesPlayed());
            dataConfig.set(path + ".wins", profile.getWins());
            dataConfig.set(path + ".top4", profile.getTop4());
            dataConfig.set(path + ".total-placement", profile.getTotalPlacement());
            dataConfig.set(path + ".first-played", profile.getFirstPlayed());
            dataConfig.set(path + ".last-played", profile.getLastPlayed());

            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player profile: " + profile.getPlayerId(), e);
            }

            // Update cache
            profileCache.put(profile.getPlayerId(), profile);
        });
    }

    /**
     * Get top players by total rating
     */
    public CompletableFuture<List<PlayerProfile>> getLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerProfile> leaderboard = new ArrayList<>();

            ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
            if (playersSection == null) {
                return leaderboard;
            }

            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidString);
                    String path = "players." + uuidString;

                    PlayerProfile profile = new PlayerProfile(playerId,
                        dataConfig.getString(path + ".name", "Unknown"));

                    profile.setTier(Tier.valueOf(dataConfig.getString(path + ".tier", "COPPER")));
                    profile.setDivision(dataConfig.getInt(path + ".division", 5));
                    profile.setRatingPoints(dataConfig.getInt(path + ".rating-points", 0));
                    profile.setGamesPlayed(dataConfig.getInt(path + ".games-played", 0));
                    profile.setWins(dataConfig.getInt(path + ".wins", 0));
                    profile.setTop4(dataConfig.getInt(path + ".top4", 0));
                    profile.setTotalPlacement(dataConfig.getInt(path + ".total-placement", 0));
                    profile.setFirstPlayed(dataConfig.getLong(path + ".first-played", 0));
                    profile.setLastPlayed(dataConfig.getLong(path + ".last-played", 0));

                    leaderboard.add(profile);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in players.yml: " + uuidString);
                }
            }

            // Sort by total rating
            leaderboard.sort((p1, p2) -> Integer.compare(p2.getTotalRating(), p1.getTotalRating()));

            // Limit results
            if (leaderboard.size() > limit) {
                leaderboard = leaderboard.subList(0, limit);
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
     * Reload data from file
     */
    public void reload() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        profileCache.clear();
        plugin.getLogger().info("Player data reloaded from file.");
    }

    /**
     * Save all cached profiles
     */
    public void saveAll() {
        for (PlayerProfile profile : profileCache.values()) {
            saveProfile(profile);
        }
        plugin.getLogger().info("All player profiles saved.");
    }

    /**
     * Close and cleanup
     */
    public void close() {
        saveAll();
        plugin.getLogger().info("File data manager closed.");
    }
}
