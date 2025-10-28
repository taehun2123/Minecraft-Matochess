package com.matochess.game;

import com.matochess.MatoChessPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active games
 * Handles game creation, lifecycle, and player management
 */
public class GameManager {

    private final MatoChessPlugin plugin;
    private final Map<UUID, GameInstance> activeGames;
    private final Map<UUID, UUID> playerToGame; // Maps player UUID to game UUID

    public GameManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.activeGames = new ConcurrentHashMap<>();
        this.playerToGame = new ConcurrentHashMap<>();
    }

    /**
     * Create a new game instance
     */
    public GameInstance createGame(List<UUID> playerIds) {
        UUID gameId = UUID.randomUUID();
        GameInstance game = new GameInstance(plugin, gameId, playerIds);

        activeGames.put(gameId, game);

        // Map players to game
        for (UUID playerId : playerIds) {
            playerToGame.put(playerId, gameId);
        }

        plugin.getLogger().info("Created new game: " + gameId + " with " + playerIds.size() + " players");
        return game;
    }

    /**
     * Get game by ID
     */
    public GameInstance getGame(UUID gameId) {
        return activeGames.get(gameId);
    }

    /**
     * Get game that a player is in
     */
    public GameInstance getPlayerGame(UUID playerId) {
        UUID gameId = playerToGame.get(playerId);
        return gameId != null ? activeGames.get(gameId) : null;
    }

    /**
     * Remove a game
     */
    public void removeGame(UUID gameId) {
        GameInstance game = activeGames.remove(gameId);
        if (game != null) {
            // Remove player mappings
            for (UUID playerId : game.getPlayerIds()) {
                playerToGame.remove(playerId);
            }
            plugin.getLogger().info("Removed game: " + gameId);
        }
    }

    /**
     * Check if player is in a game
     */
    public boolean isPlayerInGame(UUID playerId) {
        return playerToGame.containsKey(playerId);
    }

    /**
     * Get all active games
     */
    public Collection<GameInstance> getActiveGames() {
        return activeGames.values();
    }

    /**
     * Shutdown all games
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down " + activeGames.size() + " active games...");
        for (GameInstance game : activeGames.values()) {
            game.endGame();
        }
        activeGames.clear();
        playerToGame.clear();
    }
}
