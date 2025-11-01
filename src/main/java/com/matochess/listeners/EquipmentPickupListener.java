package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.game.GameInstance;
import com.matochess.data.GamePlayer;
import com.matochess.utils.NBTUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 장비 아이템 전용 드랍/회수 로직을 처리하는 리스너
 */
public class EquipmentPickupListener implements Listener {

    private final MatoChessPlugin plugin;

    public EquipmentPickupListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEquipmentPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();

        if (!NBTUtils.isEquipmentItem(stack, plugin.getKey())) {
            return;
        }

        event.setCancelled(true);

        boolean stored = plugin.getInventoryGUIManager().tryAddEquipmentItemToInventory(player, stack);
        if (!stored) {
            player.sendMessage("§c장비를 보관할 공간(9~35번 슬롯)을 비워주세요.");
            itemEntity.setPickupDelay(20);
            return;
        }

        itemEntity.remove();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);

        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game != null) {
            if (game.isTrackedGroundDrop(player.getUniqueId(), itemEntity)) {
                game.markGroundDropCollected(player.getUniqueId(), itemEntity);
            }
            GamePlayer gamePlayer = game.getPlayer(player.getUniqueId());
            if (gamePlayer != null) {
                plugin.getInventoryGUIManager().refreshGameInventory(player, gamePlayer);
                game.updateGameDisplays();
            }
        }

        String displayName = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
            ? stack.getItemMeta().getDisplayName()
            : "장비";
        player.sendMessage("§d장비를 획득했습니다: §f" + displayName);
    }
}
