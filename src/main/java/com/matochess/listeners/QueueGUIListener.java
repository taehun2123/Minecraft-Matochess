package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 큐 선택 GUI 이벤트 리스너
 */
public class QueueGUIListener implements Listener {

    private final MatoChessPlugin plugin;

    public QueueGUIListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // 큐 선택 GUI가 아니면 무시
        if (!title.equals("§6§l큐 선택")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // 슬롯 2-6 범위 확인
        if (slot < 2 || slot > 6) {
            return;
        }

        // 방 번호 계산 (슬롯 2 = 방 0, 슬롯 6 = 방 4)
        int roomNumber = slot - 2;

        // 인벤토리 닫기
        player.closeInventory();

        // 큐 참가 시도
        plugin.getMatchmakingManager().joinQueue(player, roomNumber);
    }
}
