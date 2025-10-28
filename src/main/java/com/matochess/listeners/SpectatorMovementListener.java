package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.data.Arena;
import com.matochess.game.GameInstance;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.UUID; // UUID import 추가

/**
 * 게임 중 옵저버 모드 플레이어의 이동을 제한하는 리스너
 * 플레이어가 자신의 8x6 보드판 영역을 벗어나지 못하도록 합니다
 */
public class SpectatorMovementListener implements Listener {

    private final MatoChessPlugin plugin;
    // 경고 메시지 스팸 방지용 쿨타임
    private final java.util.Map<java.util.UUID, Long> lastWarningTime = new java.util.HashMap<>();
    private static final long WARNING_COOLDOWN = 3000L; // 3초

    public SpectatorMovementListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어 이동 이벤트
     * 게임 중 옵저버 모드 플레이어가 보드판 밖으로 나가려 하면 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 옵저버 모드가 아니면 무시
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }

        // 게임 중인 플레이어인지 확인
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) {
            return;
        }

        // ----------------------------------------------------
        // 체크할 아레나의 주인을 결정합니다.
        // ----------------------------------------------------
        UUID currentBoardOwnerId = player.getUniqueId(); // 기본값: 자기 자신

        UUID spectatingTargetId = plugin.getInventoryGUIManager().getSpectatingTarget(player.getUniqueId());

        if (spectatingTargetId != null) {
            // 관전 중이라면, 아레나 주인은 관전 대상입니다.
            currentBoardOwnerId = spectatingTargetId;
        }

        // 결정된 주인의 아레나 가져오기
        Arena arena = game.getPlayerArena(currentBoardOwnerId);
        if (arena == null) {
            return;
        }

        // 이동 후 위치 확인
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // 블록 단위 이동이 없으면 (시점 변경만 있으면) 무시
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() &&
                to.getBlockY() == from.getBlockY() &&
                to.getBlockZ() == from.getBlockZ()) {
            return;
        }

        // 보드판 경계 계산 (약간 여유 추가)
        Location pos1 = arena.getPos1();
        Location pos2 = arena.getPos2();

        double minX = Math.min(pos1.getX(), pos2.getX()) - 5; // 5블록 여유
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 5;
        double minY = Math.min(pos1.getY(), pos2.getY()) - 2; // 아래로 2블록
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 20; // 위로 20블록
        double minZ = Math.min(pos1.getZ(), pos2.getZ()) - 5;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 5;

        // 경계 밖으로 나가려 하면 차단
        double x = to.getX();
        double y = to.getY();
        double z = to.getZ();

        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            event.setCancelled(true);
// 경고 메시지 스팸 방지 적용
            long now = System.currentTimeMillis();
            if (now - lastWarningTime.getOrDefault(player.getUniqueId(), 0L) > WARNING_COOLDOWN) {
                player.sendMessage("§c경고! 배치판 영역을 벗어날 수 없습니다!");
                lastWarningTime.put(player.getUniqueId(), now);
            }
        }
    }
}
