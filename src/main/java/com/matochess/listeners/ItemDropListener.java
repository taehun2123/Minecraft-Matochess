package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * 게임 중 아이템 버리기 방지 리스너
 */
public class ItemDropListener implements Listener {

    private final MatoChessPlugin plugin;

    public ItemDropListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 게임 중인 플레이어는 아이템을 버릴 수 없습니다
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // 게임 중인 플레이어인지 확인
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game != null) {
            event.setCancelled(true);
            player.sendMessage("§c게임 중에는 아이템을 버릴 수 없습니다!");
        }
    }
}
