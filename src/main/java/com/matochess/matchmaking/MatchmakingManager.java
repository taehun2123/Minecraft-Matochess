package com.matochess.matchmaking;

import com.matochess.MatoChessPlugin;
import com.matochess.game.GameInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 플레이어 매치메이킹 큐 관리
 * 5개의 독립적인 방으로 운영됩니다
 */
public class MatchmakingManager {

    private final MatoChessPlugin plugin;

    // 각 방별 큐 (방 번호 0-4)
    private final Map<Integer, Queue<UUID>> queues;

    // 플레이어가 속한 방 번호 (플레이어 -> 방 번호)
    private final Map<UUID, Integer> playerRooms;

    public MatchmakingManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.queues = new ConcurrentHashMap<>();
        this.playerRooms = new ConcurrentHashMap<>();

        // 5개 방 초기화
        int maxRooms = plugin.getConfig().getInt("matchmaking.max-rooms", 5);
        for (int i = 0; i < maxRooms; i++) {
            queues.put(i, new ConcurrentLinkedQueue<>());
        }
    }

    /**
     * 게임 시작에 필요한 최소 인원 수 가져오기
     */
    private int getMinPlayers() {
        boolean testMode = plugin.getConfig().getBoolean("game.test-mode", false);
        if (testMode) {
            return plugin.getConfig().getInt("game.test-min-players", 2);
        }
        return plugin.getConfig().getInt("game.min-players", 4);
    }

    /**
     * 게임 최대 인원 수 가져오기
     */
    private int getMaxPlayers() {
        return plugin.getConfig().getInt("game.max-players", 8);
    }

    /**
     * 플레이어를 특정 방의 큐에 추가
     */
    public boolean joinQueue(Player player, int roomNumber) {
        UUID playerId = player.getUniqueId();

        // 방 번호 유효성 검증
        if (roomNumber < 0 || roomNumber >= queues.size()) {
            player.sendMessage("§c잘못된 방 번호입니다!");
            return false;
        }

        // 이미 큐에 있는지 확인
        if (playerRooms.containsKey(playerId)) {
            player.sendMessage("§c이미 매치메이킹 큐에 참가하고 있습니다!");
            return false;
        }

        // 이미 게임 중인지 확인
        if (plugin.getGameManager().isPlayerInGame(playerId)) {
            player.sendMessage("§c이미 게임에 참가하고 있습니다!");
            return false;
        }

        Queue<UUID> roomQueue = queues.get(roomNumber);

        // 방이 가득 찼는지 확인
        if (roomQueue.size() >= getMaxPlayers()) {
            player.sendMessage("§c해당 방이 가득 찼습니다!");
            return false;
        }

        // 플레이어 데이터 백업
        plugin.getPlayerDataManager().backupPlayerData(player);

        // 인벤토리 초기화
        plugin.getPlayerDataManager().clearPlayerInventory(player);

        // 로비 스폰으로 텔레포트
        teleportToLobby(player);

        // 큐에 추가
        roomQueue.offer(playerId);
        playerRooms.put(playerId, roomNumber);

        int minPlayers = getMinPlayers();
        int currentPlayers = roomQueue.size();

        player.sendMessage("§a§l=== 매치메이킹 큐 참가 ===");
        player.sendMessage("§e방 번호: §6#" + (roomNumber + 1));
        player.sendMessage("§e현재 인원: §6" + currentPlayers + " §7/ §6" + getMaxPlayers() + "명");
        player.sendMessage("§e시작 인원: §6" + minPlayers + "명");
        player.sendMessage("");
        player.sendMessage("§7큐에서 나가려면: §c/queue leave");

        // 게임 시작 가능한지 확인
        checkQueue(roomNumber);

        return true;
    }

    /**
     * 플레이어를 큐에서 제거
     */
    public boolean leaveQueue(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerRooms.containsKey(playerId)) {
            player.sendMessage("§c매치메이킹 큐에 참가하고 있지 않습니다!");
            return false;
        }

        int roomNumber = playerRooms.get(playerId);
        Queue<UUID> roomQueue = queues.get(roomNumber);

        // 큐에서 제거
        roomQueue.remove(playerId);
        playerRooms.remove(playerId);

        // 플레이어 데이터 복구
        plugin.getPlayerDataManager().restorePlayerData(player);

        player.sendMessage("§c매치메이킹 큐에서 나갔습니다.");
        return true;
    }

    /**
     * 로비 스폰으로 텔레포트
     */
    private void teleportToLobby(Player player) {
        String worldName = plugin.getConfig().getString("arena.lobby-spawn.world", "world");
        double x = plugin.getConfig().getDouble("arena.lobby-spawn.x", 0);
        double y = plugin.getConfig().getDouble("arena.lobby-spawn.y", 64);
        double z = plugin.getConfig().getDouble("arena.lobby-spawn.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("arena.lobby-spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("arena.lobby-spawn.pitch", 0);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("로비 월드를 찾을 수 없습니다: " + worldName);
            return;
        }

        Location lobbyLoc = new Location(world, x, y, z, yaw, pitch);
        player.teleport(lobbyLoc);
    }

    /**
     * 특정 방의 게임 시작 가능 여부 확인
     */
    private void checkQueue(int roomNumber) {
        Queue<UUID> roomQueue = queues.get(roomNumber);
        int minPlayers = getMinPlayers();

        if (roomQueue.size() >= minPlayers) {
            startGame(roomNumber);
        }
    }

    /**
     * 특정 방의 큐에 있는 플레이어들로 새 게임 시작
     */
    private void startGame(int roomNumber) {
        Queue<UUID> roomQueue = queues.get(roomNumber);
        List<UUID> playerIds = new ArrayList<>();

        int minPlayers = getMinPlayers();
        int maxPlayers = getMaxPlayers();

        // 큐에서 플레이어 가져오기 (최소~최대 인원 사이)
        int playersToGet = Math.min(roomQueue.size(), maxPlayers);

        for (int i = 0; i < playersToGet && !roomQueue.isEmpty(); i++) {
            UUID playerId = roomQueue.poll();
            if (playerId != null) {
                playerRooms.remove(playerId);

                // 플레이어가 여전히 온라인인지 확인
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    playerIds.add(playerId);
                } else {
                    // 플레이어 오프라인, 데이터 정리
                    plugin.getPlayerDataManager().removeBackupData(playerId);
                    i--;
                }
            }
        }

        // 최소 인원이 충족되는지 확인
        if (playerIds.size() < minPlayers) {
            // 인원 부족, 큐에 다시 추가
            for (UUID playerId : playerIds) {
                roomQueue.offer(playerId);
                playerRooms.put(playerId, roomNumber);
            }
            return;
        }

        // 플레이어들에게 알림
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§a§l=== 게임 발견 ===");
                player.sendMessage("§e게임 시작... §7(" + playerIds.size() + "명)");
            }
        }

        // 게임 생성 및 시작
        GameInstance game = plugin.getGameManager().createGame(playerIds);
        Bukkit.getScheduler().runTaskLater(plugin, game::startGame, 60L); // 3초 지연
    }

    /**
     * 플레이어가 큐에 있는지 확인
     */
    public boolean isInQueue(UUID playerId) {
        return playerRooms.containsKey(playerId);
    }

    /**
     * 특정 방의 큐 크기
     */
    public int getRoomQueueSize(int roomNumber) {
        Queue<UUID> roomQueue = queues.get(roomNumber);
        return roomQueue != null ? roomQueue.size() : 0;
    }

    /**
     * 전체 큐 방 수
     */
    public int getRoomCount() {
        return queues.size();
    }

    /**
     * 플레이어가 속한 방 번호 (-1 if not in queue)
     */
    public int getPlayerRoom(UUID playerId) {
        return playerRooms.getOrDefault(playerId, -1);
    }
}
