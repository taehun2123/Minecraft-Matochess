package com.matochess.board;

import com.matochess.data.BoardData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Chunk;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a loaded player board instance in the world
 * Contains runtime state and location information
 */
public class BoardInstance {

    private final UUID playerId;
    private final BoardData data;
    private final BoardTemplate template;
    private final Location baseLocation;
    private boolean loaded;
    private long lastLoadTime;
    private final Set<Chunk> loadedChunks;

    public BoardInstance(UUID playerId, BoardData data, BoardTemplate template) {
        this.playerId = playerId;
        this.data = data;
        this.template = template;

        World world = Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            throw new IllegalStateException("World not found: " + data.getWorldName());
        }

        this.baseLocation = new Location(world, data.getPositionX(), data.getPositionY(), data.getPositionZ());
        this.loaded = false;
        this.lastLoadTime = System.currentTimeMillis() / 1000;
        this.loadedChunks = new HashSet<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public BoardData getData() {
        return data;
    }

    public BoardTemplate getTemplate() {
        return template;
    }

    public Location getBaseLocation() {
        return baseLocation.clone();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
        if (loaded) {
            this.lastLoadTime = System.currentTimeMillis() / 1000;
        }
    }

    public long getLastLoadTime() {
        return lastLoadTime;
    }

    public Set<Chunk> getLoadedChunks() {
        return new HashSet<>(loadedChunks);
    }

    public void addLoadedChunk(Chunk chunk) {
        loadedChunks.add(chunk);
    }

    public void clearLoadedChunks() {
        loadedChunks.clear();
    }

    /**
     * Get spawn location for player (center of board, slightly elevated)
     */
    public Location getPlayerSpawnLocation() {
        // Board is 32x24, so center is at 16, 12
        return baseLocation.clone().add(16, 2, 12);
    }

    /**
     * Get the 4 corners of this board (for boundary checks)
     */
    public Location getCorner1() {
        return baseLocation.clone();
    }

    public Location getCorner2() {
        return baseLocation.clone().add(32, 10, 24);
    }
}
