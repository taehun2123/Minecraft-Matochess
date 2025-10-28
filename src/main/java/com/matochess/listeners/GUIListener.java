package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/**
 * GUI 상호작용 리스너
 */
public class GUIListener implements Listener {

    private final MatoChessPlugin plugin;

    public GUIListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        String title = event.getView().getTitle();

        // MatoChess GUI인지 확인
        if (title.contains("Shop") || title.contains("상점")) {
            event.setCancelled(true);
            handleShopClick((Player) event.getWhoClicked(), event);
        } else if (title.contains("Equipment") || title.contains("장비")) {
            event.setCancelled(true);
            handleEquipmentClick((Player) event.getWhoClicked(), event);
        } else if (title.contains("배치")) {
            event.setCancelled(true);
            handleBoardClick((Player) event.getWhoClicked(), event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();

        // MatoChess GUI에서 드래그 방지
        if (title.contains("Shop") || title.contains("상점") ||
            title.contains("Equipment") || title.contains("장비") ||
            title.contains("배치")) {
            event.setCancelled(true);
        }
    }

    /**
     * 상점 GUI 클릭 처리
     */
    private void handleShopClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();

        // 게임 플레이어 가져오기
        var game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) {
            player.sendMessage("§c게임을 찾을 수 없습니다!");
            player.closeInventory();
            return;
        }

        var gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            player.sendMessage("§c플레이어를 찾을 수 없습니다!");
            player.closeInventory();
            return;
        }

        // 클릭 처리
        if (slot >= 10 && slot <= 14) {
            // 유닛 구매
            int shopSlot = slot - 10;
            plugin.getGUIManager().buyUnit(player, gamePlayer, shopSlot);
            plugin.getGUIManager().openShop(player, gamePlayer); // 새로고침
        } else if (slot >= 36 && slot <= 44) {
            // 벤치 유닛 클릭
            int benchIndex = slot - 36;
            List<com.matochess.data.Unit> bench = gamePlayer.getBench();

            if (benchIndex < bench.size()) {
                com.matochess.data.Unit unit = bench.get(benchIndex);

                // Shift+클릭으로 판매
                if (event.isShiftClick()) {
                    plugin.getGUIManager().sellUnit(player, gamePlayer, unit);
                    plugin.getGUIManager().openShop(player, gamePlayer); // 새로고침
                } else {
                    player.sendMessage("§eShift+클릭으로 판매할 수 있습니다!");
                }
            }
        } else if (slot == 48) {
            // 상점 새로고침
            plugin.getGUIManager().rerollShop(player, gamePlayer);
        } else if (slot == 50) {
            // 경험치 구매
            int cost = plugin.getConfig().getInt("game.xp-cost-gold", 4);
            int xpGain = plugin.getConfig().getInt("game.xp-per-purchase", 2);

            if (gamePlayer.spendGold(cost)) {
                int[] xpRequired = new int[9];
                for (int i = 1; i <= 8; i++) {
                    xpRequired[i] = plugin.getConfig().getInt("game.xp-required." + i, i * 2);
                }

                if (gamePlayer.addExperience(xpGain, xpRequired)) {
                    player.sendMessage("§a레벨 업! 현재 레벨: " + gamePlayer.getLevel());
                } else {
                    player.sendMessage("§a경험치 +" + xpGain);
                }

                plugin.getGUIManager().openShop(player, gamePlayer); // 새로고침
            } else {
                player.sendMessage("§c골드가 부족합니다! (필요: " + cost + "G)");
            }
        }
    }

    /**
     * 장비 GUI 클릭 처리
     */
    private void handleEquipmentClick(Player player, InventoryClickEvent event) {
        // TODO: 장비 클릭 처리 구현
        player.sendMessage("§d장비 관리 (구현 예정)");
    }

    /**
     * 보드 배치 GUI 클릭 처리
     */
    private void handleBoardClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();

        // 게임 플레이어 가져오기
        var game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) {
            player.sendMessage("§c게임을 찾을 수 없습니다!");
            player.closeInventory();
            return;
        }

        var gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            player.sendMessage("§c플레이어를 찾을 수 없습니다!");
            player.closeInventory();
            return;
        }

        // 클릭 처리
        plugin.getGUIManager().handleBoardClick(player, gamePlayer, slot);
    }
}
