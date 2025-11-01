package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.data.PlayerProfile;
import com.matochess.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * BT (보드 포인트) 아이템 수집 리스너
 * 플레이어가 특정 아이템을 우클릭하면 BT를 획득합니다
 */
public class BTItemListener implements Listener {

    private final MatoChessPlugin plugin;

    public BTItemListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 우클릭만 처리
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) {
            return;
        }

        // 설정된 BT 아이템인지 확인
        if (!isBTItem(item)) {
            return;
        }

        // config에서 BT 획득량 가져오기
        int btAmount = plugin.getConfig().getInt("board-points.item.amount", 10);

        if (btAmount <= 0) {
            return;
        }

        // 플레이어 프로필 로드
        plugin.getDataManager().loadProfile(player.getUniqueId(), player.getName()).thenAccept(profile -> {
            if (profile == null) {
                return;
            }

            // BT 추가
            profile.addBoardPoints(btAmount);

            // 프로필 저장
            plugin.getDataManager().saveProfile(profile);

            // 손에서 아이템 제거
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            // 플레이어에게 알림
            player.sendMessage("§a+ " + btAmount + " BT §7(총: §e" + profile.getBoardPoints() + " BT§7)");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        });

        event.setCancelled(true);
    }

    /**
     * BT 아이템인지 확인 (NBT 완전 일치)
     */
    private boolean isBTItem(ItemStack item) {
        // config에서 저장된 Base64 아이템 데이터 가져오기
        String savedBase64 = plugin.getConfig().getString("board-points.item.data");

        // 설정된 아이템이 없으면 false
        if (savedBase64 == null || savedBase64.isEmpty()) {
            return false;
        }

        // Base64에서 ItemStack 역직렬화
        ItemStack configuredItem = ItemSerializer.itemFromBase64(savedBase64);

        if (configuredItem == null) {
            return false;
        }

        // NBT를 포함한 완전한 비교
        return ItemSerializer.isSimilar(item, configuredItem);
    }
}
