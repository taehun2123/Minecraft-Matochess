package com.matochess.board.management;

import com.matochess.MatoChessPlugin;
import com.matochess.board.*;
import com.matochess.data.BoardData;
import com.matochess.data.RelativeBlock;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Core manager for dynamic board instances
 * - On-demand loading/unloading
 * - Unlimited scaling with spiral pattern
 * - Performance optimized with chunking and rate limiting
 */
public class BoardInstanceManager {

    private final MatoChessPlugin plugin;
    private final SpiralBoardLocationProvider locationProvider;
    private final BoardTemplateManager boardTemplateManager;

    // Runtime state
    private final Map<UUID, BoardInstance> loadedBoards;
    private final Map<UUID, CompletableFuture<BoardInstance>> loadingBoards;

    // Configuration
    private final int BOARD_SPACING;
    private final int UNLOAD_DELAY;
    private final int MAX_CONCURRENT_LOADS;
    private final int BLOCKS_PER_TICK;

    private final Semaphore loadSemaphore;
    private final ExecutorService executor;

    public BoardInstanceManager(MatoChessPlugin plugin, BoardTemplateManager boardTemplateManager) {
        this.plugin = plugin;
        this.boardTemplateManager = boardTemplateManager;
        this.loadedBoards = new ConcurrentHashMap<>();
        this.loadingBoards = new ConcurrentHashMap<>();

        // Load configuration
        this.BOARD_SPACING = plugin.getConfig().getInt("board.spacing", 300);
        this.UNLOAD_DELAY = plugin.getConfig().getInt("board.unload-delay", 300);
        this.MAX_CONCURRENT_LOADS = plugin.getConfig().getInt("board.max-concurrent-loads", 3);
        this.BLOCKS_PER_TICK = plugin.getConfig().getInt("board.blocks-per-tick", 150);

        this.loadSemaphore = new Semaphore(MAX_CONCURRENT_LOADS);
        this.executor = Executors.newFixedThreadPool(4);

        // Initialize world and location provider
        World world = getOrCreateArenaWorld();
        this.locationProvider = new SpiralBoardLocationProvider(world, BOARD_SPACING);

        // Start cleanup task
        startCleanupTask();

        plugin.getLogger().info("BoardInstanceManager initialized (spacing: " + BOARD_SPACING + " blocks)");
    }

    /**
     * Load a player's board (async)
     * @param playerId Player UUID
     * @return CompletableFuture with BoardInstance
     */
    public CompletableFuture<BoardInstance> loadPlayerBoard(UUID playerId) {
        // Already loaded
        if (loadedBoards.containsKey(playerId)) {
            return CompletableFuture.completedFuture(loadedBoards.get(playerId));
        }

        // Currently loading
        if (loadingBoards.containsKey(playerId)) {
            return loadingBoards.get(playerId);
        }

        CompletableFuture<BoardInstance> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limit concurrent loads
                loadSemaphore.acquire();

                // 1. Get or create board data from DB
                BoardData boardData = plugin.getDataManager().getPlayerBoardSync(playerId);

                if (boardData == null) {
                    // First time - allocate new position
                    boardData = allocateNewBoard(playerId);
                }

                // 2. Get template
                BoardTemplate template = boardTemplateManager.getTemplate(boardData.getActiveTemplateId());

                // 3. Create instance
                BoardInstance instance = new BoardInstance(playerId, boardData, template);

                // 4. Build board (sync on main thread)
                CompletableFuture<Void> buildFuture = new CompletableFuture<>();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        buildBoard(instance, buildFuture);
                    } catch (Exception e) {
                        buildFuture.completeExceptionally(e);
                    }
                });

                buildFuture.join(); // Wait for build to complete

                // 5. Register as loaded
                loadedBoards.put(playerId, instance);

                // 6. Update DB
                plugin.getDataManager().updateBoardLoadStatus(playerId, true, System.currentTimeMillis() / 1000);

                plugin.getLogger().info("Loaded board for player " + playerId + " at index " + boardData.getPositionIndex());

                return instance;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load board for " + playerId + ": " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                loadSemaphore.release();
            }
        }, executor);

        loadingBoards.put(playerId, future);

        future.whenComplete((instance, error) -> {
            loadingBoards.remove(playerId);
            if (error != null) {
                plugin.getLogger().severe("Error loading board for " + playerId + ": " + error.getMessage());
            }
        });

        return future;
    }

    /**
     * Allocate a new board position for first-time player
     */
    private BoardData allocateNewBoard(UUID playerId) {
        // Get next index from DB (thread-safe increment)
        int nextIndex = plugin.getDataManager().getAndIncrementNextIndex();

        Location location = locationProvider.calculateSpiralPosition(nextIndex);

        BoardData data = new BoardData(
            playerId,
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            nextIndex,
            "default"
        );

        plugin.getDataManager().createPlayerBoard(data);

        plugin.getLogger().info(String.format(
            "Allocated board #%d for player %s at (%d, %d, %d)",
            nextIndex, playerId, location.getBlockX(), location.getBlockY(), location.getBlockZ()
        ));

        return data;
    }

    /**
     * Build board physically in the world
     */
    private void buildBoard(BoardInstance instance, CompletableFuture<Void> completionFuture) {
        Location base = instance.getBaseLocation();
        BoardTemplate template = instance.getTemplate();
        List<RelativeBlock> blocks = template.getBlockData();

        plugin.getLogger().info("Building board at " + base.getBlockX() + ", " + base.getBlockZ() +
            " (" + blocks.size() + " blocks)");

        // 1. Load required chunks
        Set<Chunk> requiredChunks = getRequiredChunks(base, 32, 24);
        for (Chunk chunk : requiredChunks) {
            chunk.load(true);
            chunk.setForceLoaded(true);
            instance.addLoadedChunk(chunk);
        }

        // 2. Place blocks gradually (TPS protection)
        placeBlocksGradually(blocks, base, () -> {
            try {
                instance.setLoaded(true);
                placeBoundaryWalls(instance);
                completionFuture.complete(null);
            } catch (Exception ex) {
                completionFuture.completeExceptionally(ex);
                plugin.getLogger().log(Level.SEVERE, "Failed to finalize board build", ex);
            }
        });
    }

    /**
     * Place blocks gradually to avoid TPS lag
     */
    private void placeBlocksGradually(List<RelativeBlock> blocks, Location base, Runnable onComplete) {
        if (blocks.isEmpty()) {
            onComplete.run();
            return;
        }

        AtomicInteger index = new AtomicInteger(0);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                int start = index.get();
                int end = Math.min(start + BLOCKS_PER_TICK, blocks.size());

                for (int i = start; i < end; i++) {
                    RelativeBlock rb = blocks.get(i);
                    Location loc = base.clone().add(rb.getX(), rb.getY(), rb.getZ());
                    Block block = loc.getBlock();

                    block.setType(rb.getMaterial(), false); // false = no physics update

                    if (rb.getBlockData() != null) {
                        block.setBlockData(rb.getBlockData(), false);
                    }
                }

                index.set(end);

                if (end >= blocks.size()) {
                    cancel();
                    onComplete.run();
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L); // Every tick
    }

    private void placeBoundaryWalls(BoardInstance instance) {
        Location base = instance.getBaseLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        int startX = base.getBlockX();
        int startY = base.getBlockY();
        int startZ = base.getBlockZ();

        int width = 32;
        int length = 24;
        int wallHeight = 4;

        for (int y = 1; y <= wallHeight; y++) {
            int currentY = startY + y;

            for (int x = 0; x < width; x++) {
                int worldX = startX + x;
                world.getBlockAt(worldX, currentY, startZ).setType(Material.BARRIER, false);
                world.getBlockAt(worldX, currentY, startZ + length - 1).setType(Material.BARRIER, false);
            }

            for (int z = 0; z < length; z++) {
                int worldZ = startZ + z;
                world.getBlockAt(startX, currentY, worldZ).setType(Material.BARRIER, false);
                world.getBlockAt(startX + width - 1, currentY, worldZ).setType(Material.BARRIER, false);
            }
        }

        int roofY = startY + wallHeight + 1;
        for (int x = 0; x < width; x++) {
            int worldX = startX + x;
            for (int z = 0; z < length; z++) {
                int worldZ = startZ + z;
                world.getBlockAt(worldX, roofY, worldZ).setType(Material.BARRIER, false);
            }
        }
    }

    /**
     * Schedule board unload after delay
     */
    public void scheduleUnload(UUID playerId) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Don't unload if player is in game
            if (plugin.getGameManager().isPlayerInGame(playerId)) {
                return;
            }

            unloadPlayerBoard(playerId);
        }, 20L * UNLOAD_DELAY);
    }

    /**
     * Unload a player's board
     */
    private void unloadPlayerBoard(UUID playerId) {
        BoardInstance instance = loadedBoards.remove(playerId);

        if (instance == null) return;

        Location base = instance.getBaseLocation();

        plugin.getLogger().info("Unloading board for " + playerId);

        Bukkit.getScheduler().runTask(plugin, () -> {
            // 1. Remove entities
            clearEntities(base, 32, 24);

            // 2. Clear blocks
            clearBlocks(base, 32, 24);

            // 3. Unload chunks
            for (Chunk chunk : instance.getLoadedChunks()) {
                chunk.setForceLoaded(false);
            }
            instance.clearLoadedChunks();

            // 4. Update DB
            plugin.getDataManager().updateBoardLoadStatus(
                playerId, false, System.currentTimeMillis() / 1000
            );
        });
    }

    /**
     * Clear entities in board area
     */
    private void clearEntities(Location base, int width, int length) {
        World world = base.getWorld();
        Location center = base.clone().add(width / 2.0, 5, length / 2.0);

        world.getNearbyEntities(center, width / 2.0 + 5, 10, length / 2.0 + 5).forEach(entity -> {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        });
    }

    /**
     * Clear blocks in board area (gradually)
     */
    private void clearBlocks(Location base, int width, int length) {
        List<Location> blocksToRemove = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = -1; y < 10; y++) {
                    blocksToRemove.add(base.clone().add(x, y, z));
                }
            }
        }

        AtomicInteger index = new AtomicInteger(0);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                int start = index.get();
                int end = Math.min(start + 200, blocksToRemove.size());

                for (int i = start; i < end; i++) {
                    blocksToRemove.get(i).getBlock().setType(Material.AIR, false);
                }

                index.set(end);

                if (end >= blocksToRemove.size()) {
                    cancel();
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Get required chunks for a board
     */
    private Set<Chunk> getRequiredChunks(Location base, int width, int length) {
        Set<Chunk> chunks = new HashSet<>();
        World world = base.getWorld();

        int minChunkX = base.getBlockX() >> 4;
        int maxChunkX = (base.getBlockX() + width) >> 4;
        int minChunkZ = base.getBlockZ() >> 4;
        int maxChunkZ = (base.getBlockZ() + length) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(world.getChunkAt(cx, cz));
            }
        }

        return chunks;
    }

    /**
     * Periodic cleanup task for inactive boards
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis() / 1000;
            List<UUID> toUnload = new ArrayList<>();

            for (Map.Entry<UUID, BoardInstance> entry : loadedBoards.entrySet()) {
                UUID playerId = entry.getKey();

                // Not in game and inactive for UNLOAD_DELAY seconds
                if (!plugin.getGameManager().isPlayerInGame(playerId)) {
                    long lastLoaded = entry.getValue().getLastLoadTime();
                    if (now - lastLoaded > UNLOAD_DELAY) {
                        toUnload.add(playerId);
                    }
                }
            }

            for (UUID playerId : toUnload) {
                unloadPlayerBoard(playerId);
            }

            if (!toUnload.isEmpty()) {
                plugin.getLogger().info("Auto-unloaded " + toUnload.size() + " inactive boards");
            }

        }, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
    }

    /**
     * Get or create arena world
     */
    private World getOrCreateArenaWorld() {
        String worldName = plugin.getConfig().getString("board.world-name", "matochessWorld");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().info("Creating arena world: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");

            world = Bukkit.createWorld(creator);
            world.setKeepSpawnInMemory(false);
            world.setAutoSave(false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setSpawnFlags(false, false);

            plugin.getLogger().info("Arena world created successfully");
        } else {
            // 이미 존재하는 월드도 전투에 적합하도록 설정을 보정
            world.setKeepSpawnInMemory(false);
            world.setAutoSave(false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setSpawnFlags(false, false);
        }

        // 일반 몹 자연 스폰은 막으면서 커스텀 몹은 허용
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setMonsterSpawnLimit(0);
        world.setAmbientSpawnLimit(0);
        world.setWaterAnimalSpawnLimit(0);
        world.setAnimalSpawnLimit(0);

        return world;
    }

    /**
     * Shutdown - unload all boards
     */
    public void shutdown() {
        plugin.getLogger().info("Unloading all boards...");

        List<UUID> allPlayers = new ArrayList<>(loadedBoards.keySet());
        for (UUID playerId : allPlayers) {
            unloadPlayerBoard(playerId);
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Getters
    public BoardInstance getLoadedBoard(UUID playerId) {
        return loadedBoards.get(playerId);
    }

    public boolean isBoardLoaded(UUID playerId) {
        return loadedBoards.containsKey(playerId);
    }

    public int getLoadedBoardCount() {
        return loadedBoards.size();
    }

    public SpiralBoardLocationProvider getLocationProvider() {
        return locationProvider;
    }
}
