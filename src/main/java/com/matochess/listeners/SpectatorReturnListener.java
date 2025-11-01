package com.matochess.listeners; // 적절한 패키지 이름으로 변경하세요

import com.matochess.MatoChessPlugin;
import com.matochess.gui.InventoryGUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SpectatorReturnListener implements Listener {

    private final MatoChessPlugin plugin;

    public SpectatorReturnListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // InventoryGUIManager 인스턴스 가져오기
        InventoryGUIManager guiManager = plugin.getInventoryGUIManager();

        // 클릭한 플레이어가 관전 중이 아니라면 처리할 필요가 없음
        if (!guiManager.isSpectating(event.getWhoClicked().getUniqueId())) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // 클릭한 아이템이 null이거나, SPECTRAL_ARROW가 아니면 무시
        if (clickedItem == null || clickedItem.getType() != Material.SPECTRAL_ARROW) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        // ItemMeta가 없거나 이름이 "§e내 배치판으로 돌아가기"가 아니면 무시
        if (meta == null || !meta.hasDisplayName() ||
                !meta.getDisplayName().equals("§e내 배치판으로 돌아가기")) {
            return;
        }

        // 핫바의 9번 슬롯(인벤토리 슬롯 8)만 처리해야 합니다.
        // 관전 모드에서는 핫바의 9번 슬롯에 돌아가기 버튼을 넣었기 때문에,
        // 이 슬롯에서 클릭이 일어났는지 확인합니다.
        if (event.getSlot() == 8) {
            event.setCancelled(true); // 클릭 이벤트 취소 (아이템 이동 방지)

            // 관전 모드 종료 로직 실행
            guiManager.stopSpectating(player);
        }
    }
}