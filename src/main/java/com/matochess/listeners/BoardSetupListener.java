package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 체스판 템플릿 설정 리스너
 * 관리자가 우클릭으로 8x6 영역을 지정합니다
 */
public class BoardSetupListener implements Listener {

    private final MatoChessPlugin plugin;

    // 설정 모드 플레이어 추적
    private final Map<UUID, Boolean> setupMode;

    // 첫 번째 위치 저장
    private final Map<UUID, Location> firstPosition;

    public BoardSetupListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.setupMode = new HashMap<>();
        this.firstPosition = new HashMap<>();
    }

    /**
     * 플레이어를 설정 모드로 전환
     */
    public void enableSetupMode(Player player) {
        setupMode.put(player.getUniqueId(), true);
        firstPosition.remove(player.getUniqueId());
        player.sendMessage("§a체스판 설정 모드 활성화!");
        player.sendMessage("§e8x6 체스판의 한쪽 모서리를 우클릭하세요");
    }

    /**
     * 플레이어의 설정 모드 해제
     */
    public void disableSetupMode(Player player) {
        setupMode.remove(player.getUniqueId());
        firstPosition.remove(player.getUniqueId());
    }

    /**
     * 우클릭 이벤트 처리
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 설정 모드가 아니면 무시
        if (!setupMode.getOrDefault(playerId, false)) {
            return;
        }

        // 우클릭만 처리
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 오프핸드 이벤트 무시 (메인핸드만 처리)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        event.setCancelled(true);

        Location clickedLocation = event.getClickedBlock().getLocation();

        // 첫 번째 위치 설정
        if (!firstPosition.containsKey(playerId)) {
            firstPosition.put(playerId, clickedLocation);
            player.sendMessage("§a첫 번째 모서리 설정 완료!");
            player.sendMessage("§e대각선 반대편 모서리를 우클릭하세요");
            return;
        }

        // 두 번째 위치 설정
        Location pos1 = firstPosition.get(playerId);
        Location pos2 = clickedLocation;

        // 같은 월드인지 확인
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage("§c두 위치는 같은 월드에 있어야 합니다!");
            return;
        }

        // 크기 계산 및 검증 (X, Z만 사용 - 바닥 평면)
        int width = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int length = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;

        player.sendMessage("§e체스판 크기: §6" + width + "x" + length + " §e(바닥 평면)");

        // 8x6 크기 권장
        if (width != 8 || length != 6) {
            player.sendMessage("§c경고: 권장 크기는 8x6입니다! (현재: " + width + "x" + length + ")");
            player.sendMessage("§e계속하려면 다시 우클릭하세요. 취소하려면 §c/mcadmin cancelboard");
            // 경고만 하고 계속 진행 가능
        }

        // config에 저장 (Y 좌표는 같은 높이로 저장)
        int baseY = pos1.getBlockY();
        plugin.getConfig().set("arena.board-template.world", pos1.getWorld().getName());
        plugin.getConfig().set("arena.board-template.pos1.x", pos1.getBlockX());
        plugin.getConfig().set("arena.board-template.pos1.y", baseY);
        plugin.getConfig().set("arena.board-template.pos1.z", pos1.getBlockZ());
        plugin.getConfig().set("arena.board-template.pos2.x", pos2.getBlockX());
        plugin.getConfig().set("arena.board-template.pos2.y", baseY);
        plugin.getConfig().set("arena.board-template.pos2.z", pos2.getBlockZ());
        plugin.saveConfig();

        player.sendMessage("§a체스판 템플릿 설정 완료! (바닥 높이: Y=" + baseY + ")");
        player.sendMessage("§e이제 §6/mcadmin setworld §e명령어로 체스판을 생성하세요");

        // 설정 모드 해제
        disableSetupMode(player);
    }

    /**
     * 설정 모드 확인
     */
    public boolean isInSetupMode(UUID playerId) {
        return setupMode.getOrDefault(playerId, false);
    }
}
