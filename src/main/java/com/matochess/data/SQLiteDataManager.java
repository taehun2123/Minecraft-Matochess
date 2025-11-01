package com.matochess.data;

import com.matochess.MatoChessPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 데이터 연결관리 및 SQLite를 사용하여 Player의 데이터를 보관합니다.
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
     * 초기 데이터베이스 연결과 테이블 생성
     */
    public boolean initialize() {
        try {
            // 기존 데이터폴더가 존재하지 않는다면 데이터폴더를 생성합니다.
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // SQLite JDBC 연결
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // 외부키 활성화
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            // 테이블 생성합니다.
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
     * 테이블이 존재하지 않을 경우 테이블을 생성합니다.
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
                board_points INTEGER NOT NULL DEFAULT 0,
                first_played INTEGER NOT NULL,
                last_played INTEGER NOT NULL
            );
        """;

        String createPlayerBoardsTable = """
            CREATE TABLE IF NOT EXISTS player_boards (
                player_id TEXT PRIMARY KEY,
                world_name TEXT NOT NULL DEFAULT 'matochessWorld',
                position_x INTEGER NOT NULL,
                position_y INTEGER NOT NULL DEFAULT 100,
                position_z INTEGER NOT NULL,
                position_index INTEGER NOT NULL,
                active_template_id TEXT NOT NULL DEFAULT 'default',
                owned_templates TEXT,
                is_loaded INTEGER NOT NULL DEFAULT 0,
                last_loaded INTEGER,
                last_unloaded INTEGER,
                created_at INTEGER NOT NULL
            );
        """;

        String createBoardTemplatesTable = """
            CREATE TABLE IF NOT EXISTS board_templates (
                template_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT,
                price INTEGER NOT NULL DEFAULT 0,
                block_data_json TEXT NOT NULL,
                preview_material TEXT DEFAULT 'WHITE_WOOL',
                category TEXT DEFAULT 'BASIC',
                created_at INTEGER NOT NULL
            );
        """;

        String createLocationRegistryTable = """
            CREATE TABLE IF NOT EXISTS board_location_registry (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                next_index INTEGER NOT NULL DEFAULT 0,
                total_allocated INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER NOT NULL
            );
        """;

        String createShopItemsTable = """
            CREATE TABLE IF NOT EXISTS shop_items (
                template_id TEXT PRIMARY KEY,
                item_base64 TEXT NOT NULL,
                slot INTEGER NOT NULL,
                price INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """;

        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_tier
            ON matochess_players(tier, division, rating_points);

            CREATE INDEX IF NOT EXISTS idx_player_name
            ON matochess_players(player_name);

            CREATE INDEX IF NOT EXISTS idx_board_position
            ON player_boards(position_x, position_z);

            CREATE INDEX IF NOT EXISTS idx_board_loaded
            ON player_boards(is_loaded);

            CREATE INDEX IF NOT EXISTS idx_board_index
            ON player_boards(position_index);

            CREATE INDEX IF NOT EXISTS idx_template_category
            ON board_templates(category);

            CREATE INDEX IF NOT EXISTS idx_shop_slot
            ON shop_items(slot);
        """;

        String initializeRegistry = """
            INSERT OR IGNORE INTO board_location_registry (id, next_index, total_allocated, last_updated)
            VALUES (1, 0, 0, strftime('%s', 'now'));
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createPlayerBoardsTable);
            stmt.execute(createBoardTemplatesTable);
            stmt.execute(createLocationRegistryTable);
            stmt.execute(createShopItemsTable);
            stmt.execute(createIndexes);
            stmt.execute(initializeRegistry);
            plugin.getLogger().info("Database tables created successfully.");
        }
    }

    /**
     * 데이터베이스에서 플레이어 프로필을 불러옵니다. 없으면 생성합니다.
     */
    public CompletableFuture<PlayerProfile> loadProfile(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // 캐시 확인
            if (profileCache.containsKey(playerId)) {
                return profileCache.get(playerId);
            }

            try {
                // 데이터베이스에서 부터 불러옵니다.
                String selectQuery = "SELECT * FROM matochess_players WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
                    stmt.setString(1, playerId.toString());

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        // 존재하는 프로필을 로딩합니다.
                        PlayerProfile profile = new PlayerProfile(playerId, rs.getString("player_name"));
                        profile.setTier(Tier.valueOf(rs.getString("tier")));
                        profile.setDivision(rs.getInt("division"));
                        profile.setRatingPoints(rs.getInt("rating_points"));
                        profile.setGamesPlayed(rs.getInt("games_played"));
                        profile.setWins(rs.getInt("wins"));
                        profile.setTop4(rs.getInt("top4"));
                        profile.setTotalPlacement(rs.getInt("total_placement"));
                        profile.setBoardPoints(rs.getInt("board_points"));
                        profile.setFirstPlayed(rs.getLong("first_played"));
                        profile.setLastPlayed(rs.getLong("last_played"));

                        // 닉네임이 바뀌었다면 바뀐 닉네임으로 적용합니다.
                        if (!profile.getPlayerName().equals(playerName)) {
                            profile.setPlayerName(playerName);
                            saveProfile(profile);
                        }

                        profileCache.put(playerId, profile);
                        return profile;
                    } else {
                        // 새 프로필을 생성합니다.
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
     * 데이터베이스로 플레이어 데이터를 저장합니다.
     */
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                String upsertQuery = """
                    INSERT INTO matochess_players
                    (player_id, player_name, tier, division, rating_points, games_played,
                     wins, top4, total_placement, board_points, first_played, last_played)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_id) DO UPDATE SET
                    player_name = excluded.player_name,
                    tier = excluded.tier,
                    division = excluded.division,
                    rating_points = excluded.rating_points,
                    games_played = excluded.games_played,
                    wins = excluded.wins,
                    top4 = excluded.top4,
                    total_placement = excluded.total_placement,
                    board_points = excluded.board_points,
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
                    stmt.setInt(10, profile.getBoardPoints());
                    stmt.setLong(11, profile.getFirstPlayed());
                    stmt.setLong(12, profile.getLastPlayed());

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
     * 탑 플레이어 조회
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
                        profile.setBoardPoints(rs.getInt("board_points"));
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
     * 캐시된 프로필을 조회합니다. (비동기)
     */
    public PlayerProfile getCachedProfile(UUID playerId) {
        return profileCache.get(playerId);
    }

    /**
     * 데이터베이스 연결을 닫습니다.
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

    // ==================== 보드 관리 메소드들 ====================

    /**
     * 플레이어 보드 데이터를 연결합니다. (동기적으로 로딩됨)
     */
    public BoardData getPlayerBoardSync(UUID playerId) {
        try {
            String query = "SELECT * FROM player_boards WHERE player_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    BoardData data = new BoardData(
                        playerId,
                        rs.getString("world_name"),
                        rs.getInt("position_x"),
                        rs.getInt("position_y"),
                        rs.getInt("position_z"),
                        rs.getInt("position_index"),
                        rs.getString("active_template_id")
                    );

                    // Parse owned templates from JSON
                    String ownedJson = rs.getString("owned_templates");
                    if (ownedJson != null && !ownedJson.isEmpty()) {
                        List<String> owned = parseOwnedTemplates(ownedJson);
                        data.setOwnedTemplates(owned);
                    }

                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load board for " + playerId, e);
        }
        return null;
    }

    /**
     * 새 플레이어 보드 데이터를 입력합니다.
     */
    public void createPlayerBoard(BoardData data) {
        try {
            String query = """
                INSERT INTO player_boards
                (player_id, world_name, position_x, position_y, position_z, position_index,
                 active_template_id, owned_templates, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, data.getPlayerId().toString());
                stmt.setString(2, data.getWorldName());
                stmt.setInt(3, data.getPositionX());
                stmt.setInt(4, data.getPositionY());
                stmt.setInt(5, data.getPositionZ());
                stmt.setInt(6, data.getPositionIndex());
                stmt.setString(7, data.getActiveTemplateId());
                stmt.setString(8, serializeOwnedTemplates(data.getOwnedTemplates()));
                stmt.setLong(9, System.currentTimeMillis() / 1000);

                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create board for " + data.getPlayerId(), e);
        }
    }

    /**
     * 보드 로드 상태를 업데이트 합니다.
     */
    public void updateBoardLoadStatus(UUID playerId, boolean loaded, long timestamp) {
        try {
            String query = loaded
                ? "UPDATE player_boards SET is_loaded = 1, last_loaded = ? WHERE player_id = ?"
                : "UPDATE player_boards SET is_loaded = 0, last_unloaded = ? WHERE player_id = ?";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setLong(1, timestamp);
                stmt.setString(2, playerId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update load status for " + playerId, e);
        }
    }

    /**
     * 다음 보드의 인텍스를 갱신합니다. (thread-safe)
     */
    public synchronized int getAndIncrementNextIndex() {
        try {
            String selectQuery = "SELECT next_index FROM board_location_registry WHERE id = 1";
            String updateQuery = """
                UPDATE board_location_registry
                SET next_index = next_index + 1,
                    total_allocated = total_allocated + 1,
                    last_updated = strftime('%s', 'now')
                WHERE id = 1
            """;

            int nextIndex;
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(selectQuery);
                if (rs.next()) {
                    nextIndex = rs.getInt("next_index");
                } else {
                    nextIndex = 0;
                }
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(updateQuery);
            }

            return nextIndex;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get next board index", e);
            return 0;
        }
    }

    /**
     * 플레이어의 활성화된 템플릿을 세팅합니다.
     */
    public CompletableFuture<Void> setActiveTemplate(UUID playerId, String templateId) {
        return CompletableFuture.runAsync(() -> {
            try {
                String query = "UPDATE player_boards SET active_template_id = ? WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, templateId);
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to set active template", e);
            }
        });
    }

    /**
     * 플레이어에게로 템플릿을 부여합니다.
     */
    public CompletableFuture<Void> addOwnedTemplate(UUID playerId, String templateId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Get current owned templates
                BoardData data = getPlayerBoardSync(playerId);
                if (data == null) return;

                data.addOwnedTemplate(templateId);

                // Update database
                String query = "UPDATE player_boards SET owned_templates = ? WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, serializeOwnedTemplates(data.getOwnedTemplates()));
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add owned template", e);
            }
        });
    }

    /**
     * 플레이어가 템플릿을 가지고 있는지 조회
     */
    public CompletableFuture<Boolean> hasTemplate(UUID playerId, String templateId) {
        return CompletableFuture.supplyAsync(() -> {
            BoardData data = getPlayerBoardSync(playerId);
            return data != null && data.ownsTemplate(templateId);
        });
    }

    /**
     * 데이터베이스에 템플릿을 저장합니다.
     */
    public void saveTemplate(com.matochess.board.BoardTemplate template) {
        try {
            String query = """
                INSERT OR REPLACE INTO board_templates
                (template_id, name, description, price, block_data_json, preview_material, category, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, template.getTemplateId());
                stmt.setString(2, template.getName());
                stmt.setString(3, template.getDescription());
                stmt.setInt(4, template.getPrice());
                stmt.setString(5, serializeBlockData(template.getBlockData()));
                stmt.setString(6, template.getPreviewMaterial().name());
                stmt.setString(7, template.getCategory());
                stmt.setLong(8, System.currentTimeMillis() / 1000);

                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save template " + template.getTemplateId(), e);
        }
    }

    /**
     * 데이터베이스로부터 모든 템플릿 데이터를 불러옵니다.
     */
    public CompletableFuture<List<com.matochess.board.BoardTemplate>> loadAllTemplates() {
        return CompletableFuture.supplyAsync(() -> {
            List<com.matochess.board.BoardTemplate> templates = new ArrayList<>();

            try {
                String query = "SELECT * FROM board_templates";
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery(query);

                    while (rs.next()) {
                        // Note: We can't fully reconstruct templates from DB without proper serialization
                        // For now, we'll skip custom templates - they should be registered via TemplateManager
                        // This is a placeholder for future implementation
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load templates", e);
            }

            return templates;
        });
    }

    /**
     * 총 할당된 템플릿의 수를 불러옵니다. (int)
     */
    public int getTotalAllocatedBoards() {
        try {
            String query = "SELECT total_allocated FROM board_location_registry WHERE id = 1";
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                if (rs.next()) {
                    return rs.getInt("total_allocated");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get total allocated boards", e);
        }
        return 0;
    }

    // JSON 직렬화를 위한 헬퍼 메소드들
    private String serializeOwnedTemplates(List<String> templates) {
        return String.join(",", templates);
    }

    private List<String> parseOwnedTemplates(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>(List.of("default"));
        }
        return new ArrayList<>(Arrays.asList(json.split(",")));
    }

    private String serializeBlockData(List<RelativeBlock> blocks) {
        // Simple serialization - just store count for now
        // Full implementation would use JSON or NBT
        return String.valueOf(blocks.size());
    }

    // ========== Shop Items 관리 ==========

    /**
     * 상점 아이템 저장 (관리자가 설정한 NBT 아이템)
     */
    public CompletableFuture<Void> saveShopItem(BoardShopItemData shopItem) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO shop_items (template_id, item_base64, slot, price, created_at, updated_at)
                VALUES (?, ?, ?, ?, COALESCE((SELECT created_at FROM shop_items WHERE template_id = ?), strftime('%s', 'now')), strftime('%s', 'now'))
            """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, shopItem.getTemplateId());
                pstmt.setString(2, shopItem.getItemBase64());
                pstmt.setInt(3, shopItem.getSlot());
                pstmt.setInt(4, shopItem.getPrice());
                pstmt.setString(5, shopItem.getTemplateId()); // for COALESCE

                pstmt.executeUpdate();
                plugin.getLogger().info("Shop item saved: " + shopItem.getTemplateId() + " at slot " + shopItem.getSlot());

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save shop item: " + shopItem.getTemplateId(), e);
            }
        });
    }

    /**
     * 모든 상점 아이템 로드
     */
    public CompletableFuture<List<BoardShopItemData>> loadAllShopItems() {
        return CompletableFuture.supplyAsync(() -> {
            List<BoardShopItemData> items = new ArrayList<>();
            String sql = "SELECT template_id, item_base64, slot, price FROM shop_items ORDER BY slot ASC";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String templateId = rs.getString("template_id");
                    String itemBase64 = rs.getString("item_base64");
                    int slot = rs.getInt("slot");
                    int price = rs.getInt("price");

                    items.add(new BoardShopItemData(templateId, itemBase64, slot, price));
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load shop items", e);
            }

            return items;
        });
    }

    /**
     * 특정 상점 아이템 로드
     */
    public CompletableFuture<BoardShopItemData> loadShopItem(String templateId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT template_id, item_base64, slot, price FROM shop_items WHERE template_id = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, templateId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String itemBase64 = rs.getString("item_base64");
                        int slot = rs.getInt("slot");
                        int price = rs.getInt("price");

                        return new BoardShopItemData(templateId, itemBase64, slot, price);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load shop item: " + templateId, e);
            }

            return null;
        });
    }

    /**
     * 상점 아이템 삭제
     */
    public CompletableFuture<Void> deleteShopItem(String templateId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM shop_items WHERE template_id = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, templateId);
                int affected = pstmt.executeUpdate();

                if (affected > 0) {
                    plugin.getLogger().info("Shop item deleted: " + templateId);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete shop item: " + templateId, e);
            }
        });
    }

    /**
     * 상점 아이템 존재 여부 확인
     */
    public CompletableFuture<Boolean> shopItemExists(String templateId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM shop_items WHERE template_id = ? LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, templateId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check shop item existence: " + templateId, e);
                return false;
            }
        });
    }
}
