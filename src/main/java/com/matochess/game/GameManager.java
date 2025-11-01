package com.matochess.game;

import com.matochess.MatoChessPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모든 활성화된 게임을 관리합니다.
 * 게임 생성, 생명주기, 플레이어 게임 참가를 관리합니다.
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
     * 새 게임 인스턴스 생성
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
     * ID로 게임 찾기
     */
    public GameInstance getGame(UUID gameId) {
        return activeGames.get(gameId);
    }

    /**
     * 플레이어가 진행 중인 게임 찾기
     */
    public GameInstance getPlayerGame(UUID playerId) {
        UUID gameId = playerToGame.get(playerId);
        return gameId != null ? activeGames.get(gameId) : null;
    }

    /**
     * 게임 삭제
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
     * 게임에 특정 플레이어가 있는지 확인
     */
    public boolean isPlayerInGame(UUID playerId) {
        return playerToGame.containsKey(playerId);
    }

    /**
     * 모든 활성화된 게임 얻기
     */
    public Collection<GameInstance> getActiveGames() {
        return activeGames.values();
    }

    /**
     * 모든 게임 종료
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
