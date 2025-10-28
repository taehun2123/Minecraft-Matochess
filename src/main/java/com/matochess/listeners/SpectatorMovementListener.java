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

/**
 * 게임 중 옵저버 모드 플레이어의 이동을 제한하는 리스너
 * 플레이어가 자신의 8x6 보드판 영역을 벗어나지 못하도록 합니다
 */
public class SpectatorMovementListener implements Listener {

    private final MatoChessPlugin plugin;

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

        // 플레이어의 아레나 가져오기
        Arena arena = game.getPlayerArena(player.getUniqueId());
        if (arena == null) {
            return;
        }

        // 이동 후 위치 확인
        Location to = event.getTo();
        if (to == null) {
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
            player.sendMessage("§c보드판 영역을 벗어날 수 없습니다!");
        }
    }
}
