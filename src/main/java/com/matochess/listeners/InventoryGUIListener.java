package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.data.Position;
import com.matochess.data.Unit;
import com.matochess.game.GameInstance;
import com.matochess.gui.InventoryGUIManager;
import com.matochess.utils.NBTUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * 새로운 인벤토리 기반 GUI 이벤트 리스너
 */
public class InventoryGUIListener implements Listener {

    private final MatoChessPlugin plugin;
    private final InventoryGUIManager guiManager;

    public InventoryGUIListener(MatoChessPlugin plugin, InventoryGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * 인벤토리 열기 이벤트 (E키 감지)
     * 게임 중인 플레이어가 E키를 누르면 통합 GUI가 이미 설정되어 있음
     * 별도의 처리 불필요 - setupGameInventory에서 이미 통합 UI 설정됨
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // E키로 인벤토리를 열 때는 이미 setupGameInventory로 설정된 통합 UI가 표시됨
        // 별도의 처리 불필요
    }

    /**
     * 인벤토리 클릭 이벤트
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) return;

        GamePlayer gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) return;

        String title = event.getView().getTitle();
        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();

        // 배치판 GUI
        if (title.contains("배치판")) {
            handleBoardGUIClick(player, gamePlayer, event);
            return;
        }

        // 플레이어 목록 GUI
        if (title.contains("플레이어 목록")) {
            handleSpectateListClick(player, event);
            return;
        }

        // 플레이어 자신의 인벤토리
        if (event.getClickedInventory() != null &&
            event.getClickedInventory().getType() == InventoryType.PLAYER) {

            // 핫바 클릭 (0-8)
            if (slot >= 0 && slot <= 8) {
                handleHotbarClick(player, gamePlayer, slot, event);
            }

            // 벤치 슬롯 (9-17, 1줄)
            else if (slot >= 9 && slot <= 17) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR && NBTUtils.isUnitItem(item, plugin.getKey())) {
                    // 유닛 정보 가져오기
                    UUID unitInstanceId = NBTUtils.getUnitInstanceId(item, plugin.getKey());

                    // 벤치에서 해당 유닛 찾기
                    Unit selectedUnit = null;
                    for (Unit unit : gamePlayer.getBench()) {
                        if (unit.getInstanceId().equals(unitInstanceId)) {
                            selectedUnit = unit;
                            break;
                        }
                    }

                    if (selectedUnit != null) {
                        if (event.isRightClick()) {
                            // 우클릭 - 판매용으로 선택
                            guiManager.selectUnitForSale(player.getUniqueId(), selectedUnit);
                            player.sendMessage("§e" + selectedUnit.getName() + " §7판매 선택! 빨간 유리를 클릭하세요.");
                        } else {
                            // 좌클릭 - 배치용으로 선택
                            plugin.getGUIManager().selectUnitForPlacement(player, selectedUnit);
                            // 배치 GUI 열기
                            plugin.getGUIManager().openBoardGUI(player, gamePlayer);
                        }
                    }
                }
            }

            // 판매 슬롯 (18-26, 2번째 줄)
            else if (slot >= 18 && slot <= 26) {
                handleSellSlotClick(player, gamePlayer, event);
            }
        }
    }

    /**
     * 핫바 클릭 처리
     */
    private void handleHotbarClick(Player player, GamePlayer gamePlayer, int slot, InventoryClickEvent event) {
        event.setCancelled(true); // 핫바 아이템은 이동 불가

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // 0번: 경험치 업
        if (slot == 0) {
            handleXPBuy(player, gamePlayer);
        }

        // 1번: 리롤
        else if (slot == 1) {
            handleReroll(player, gamePlayer);
        }

        // 2-6번: 상점 유닛 구매
        else if (slot >= 2 && slot <= 6) {
            int shopIndex = slot - 2;
            handleUnitPurchase(player, gamePlayer, shopIndex);
        }

        // 7번: 상점 토글
        else if (slot == 7) {
            guiManager.toggleShop(player, gamePlayer);
        }

        // 8번: 관전 또는 돌아가기
        else if (slot == 8) {
            if (guiManager.isSpectating(player.getUniqueId())) {
                guiManager.stopSpectating(player);
            } else {
                guiManager.openSpectateList(player);
            }
        }
    }

    /**
     * 경험치 구매
     */
    private void handleXPBuy(Player player, GamePlayer gamePlayer) {
        int cost = plugin.getConfig().getInt("game.xp-cost-gold", 4);
        int xpGain = plugin.getConfig().getInt("game.xp-per-purchase", 2);

        if (!gamePlayer.spendGold(cost)) {
            player.sendMessage("§c골드가 부족합니다! (필요: " + cost + "G)");
            return;
        }

        // XP 추가 및 레벨업 확인
        int[] xpRequired = new int[9];
        for (int i = 1; i <= 8; i++) {
            xpRequired[i] = plugin.getConfig().getInt("game.xp-required." + i, i * 2);
        }

        if (gamePlayer.addExperience(xpGain, xpRequired)) {
            player.sendMessage("§a레벨 업! 현재 레벨: " + gamePlayer.getLevel());
        } else {
            player.sendMessage("§a경험치 +" + xpGain);
        }

        guiManager.setupGameInventory(player, gamePlayer);
    }

    /**
     * 상점 리롤
     */
    private void handleReroll(Player player, GamePlayer gamePlayer) {
        guiManager.rerollShop(player, gamePlayer);
    }

    /**
     * 상점에서 유닛 구매
     */
    private void handleUnitPurchase(Player player, GamePlayer gamePlayer, int shopIndex) {
        guiManager.purchaseUnit(player, gamePlayer, shopIndex);
    }

    /**
     * 판매 슬롯 클릭
     */
    private void handleSellSlotClick(Player player, GamePlayer gamePlayer, InventoryClickEvent event) {
        event.setCancelled(true);

        // 선택된 유닛 판매
        Unit unitToSell = guiManager.getSelectedUnitForSale(player.getUniqueId());

        if (unitToSell != null) {
            int goldGained = gamePlayer.sellUnit(unitToSell);
            player.sendMessage("§a유닛을 판매했습니다! §6+" + goldGained + "G");

            // 선택 해제
            guiManager.clearSelectedUnitForSale(player.getUniqueId());

            // 인벤토리 새로고침
            guiManager.setupGameInventory(player, gamePlayer);
        } else {
            player.sendMessage("§c먼저 벤치 유닛을 우클릭하여 선택하세요!");
        }
    }

    /**
     * 배치판 GUI 클릭
     */
    private void handleBoardGUIClick(Player player, GamePlayer gamePlayer, InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getSlot();

        // 8번째 열(구분선)은 클릭 무시
        if (slot % 9 == 8) return;

        // 48칸 이상은 무시
        int row = slot / 9;
        int col = slot % 9;
        if (row >= 6 || col >= 8) return;

        Position position = new Position(col, row);
        Unit unitAtPos = gamePlayer.getBoard().get(position);

        ItemStack cursor = event.getCursor();

        // 커서에 유닛이 있으면 배치
        if (cursor != null && cursor.getType() != Material.AIR &&
            NBTUtils.isUnitItem(cursor, plugin.getKey())) {

            UUID unitInstanceId = NBTUtils.getUnitInstanceId(cursor, plugin.getKey());

            // 벤치에서 유닛 찾기
            Unit unitToPlace = null;
            for (Unit unit : gamePlayer.getBench()) {
                if (unit.getInstanceId().equals(unitInstanceId)) {
                    unitToPlace = unit;
                    break;
                }
            }

            if (unitToPlace != null) {
                // 해당 위치가 비어있는지 확인
                if (unitAtPos == null) {
                    // 벤치에서 제거하고 보드에 배치
                    gamePlayer.getBench().remove(unitToPlace);
                    gamePlayer.placeUnit(unitToPlace, position);

                    player.sendMessage("§a유닛을 배치했습니다!");

                    // 커서 클리어
                    event.setCursor(null);

                    // GUI 새로고침
                    guiManager.openBoardGUI(player, gamePlayer);
                    guiManager.setupGameInventory(player, gamePlayer);
                } else {
                    player.sendMessage("§c해당 위치에 이미 유닛이 있습니다!");
                }
            }
        }

        // 보드의 유닛 클릭 - 벤치로 이동
        else if (unitAtPos != null) {
            if (gamePlayer.addUnitToBench(unitAtPos)) {
                gamePlayer.removeUnitFromBoard(position);
                player.sendMessage("§a유닛을 벤치로 이동했습니다!");

                // GUI 새로고침
                guiManager.openBoardGUI(player, gamePlayer);
                guiManager.setupGameInventory(player, gamePlayer);
            } else {
                player.sendMessage("§c벤치가 가득 찼습니다!");
            }
        }
    }

    /**
     * 관전 목록 클릭
     */
    private void handleSpectateListClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwningPlayer() == null) return;

        UUID targetId = meta.getOwningPlayer().getUniqueId();
        player.closeInventory();

        guiManager.startSpectating(player, targetId);
    }

    /**
     * 드래그 이벤트
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // 배치판이나 플레이어 목록 GUI에서는 드래그 금지
        String title = event.getView().getTitle();
        if (title.contains("배치판") || title.contains("플레이어 목록")) {
            event.setCancelled(true);
        }
    }
}
