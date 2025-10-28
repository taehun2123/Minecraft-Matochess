package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePhase;
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
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Bukkit;


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
     * 게임 중인 플레이어가 E키를 누르면 54칸 통합 GUI를 자동으로 열어줌
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) return;

        GamePlayer gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) return;

        // 기본 인벤토리(PLAYER)를 열려고 할 때 54칸 통합 GUI로 대체
        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            event.setCancelled(true);
            // 1틱 후에 통합 GUI 열기 (이벤트 충돌 방지)
            Bukkit.getScheduler().runTask(plugin, () -> {
                guiManager.setupGameInventory(player, gamePlayer);
            });
        }
    }

    /**
     * 인벤토리 닫기 이벤트
     * 통합 GUI를 닫아도 자동으로 다시 열지 않음 (플레이어가 E키를 다시 눌러야 함)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) return;

        String title = event.getView().getTitle();

        // 통합 GUI를 닫은 경우, 다음에 E키를 누르면 다시 통합 GUI가 열림
        // 별도의 처리 불필요 - onInventoryOpen에서 자동으로 처리됨
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

        // 플레이어 목록 GUI
        if (title.contains("플레이어 목록")) {
            handleSpectateListClick(player, event);
            return;
        }

        // 54칸 통합 GUI (마토체스)
        if (title.contains("마토체스")) {
            event.setCancelled(true); // 모든 클릭 취소 (드래그 방지)
            handleIntegratedGUIClick(player, gamePlayer, slot, event);
            return;
        }
    }

    /**
     * 54칸 통합 GUI 클릭 처리
     */
    private void handleIntegratedGUIClick(Player player, GamePlayer gamePlayer, int slot, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();

        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        GamePhase currentPhase = (game != null) ? game.getCurrentPhase() : null;

        // 배치판 슬롯 (1~8, 10~17, 19~26칸)
        if (isBoardSlot(slot)) {
            // 전투 단계에서는 배치 금지
            if (currentPhase != null && currentPhase.isCombat()) {
                player.sendMessage("§c전투 중에는 병력을 배치할 수 없습니다!");
                return;
            }
            handleBoardSlotClick(player, gamePlayer, slot, item);
        }
        // 정보 패널 (9, 18, 27칸) - 클릭 무시
        else if (slot == 8 || slot == 17 || slot == 26) {
            // 정보 표시만, 클릭 불가
        }
        // 벤치 슬롯 (28~35칸)
        else if (slot >= 27 && slot <= 34) {
            // 전투 단계에서는 벤치 유닛 선택 금지 (판매는 가능)
            if (currentPhase != null && currentPhase.isCombat() && event.isLeftClick()) {
                player.sendMessage("§c전투 중에는 병력을 배치할 수 없습니다!");
                return;
            }
            handleBenchSlotClick(player, gamePlayer, slot, item, event, currentPhase);
        }
        // 레벨 정보 (36칸) - 클릭 무시
        else if (slot == 35) {
            // 정보 표시만
        }
        // 필러 (37~45칸) - 클릭 무시
        else if (slot >= 36 && slot <= 44) {
            // 필러
        }
        // 경험치 업 버튼 (46칸)
        else if (slot == 45) {
            handleXPBuy(player, gamePlayer);
        }
        // 리롤 버튼 (47칸)
        else if (slot == 46) {
            handleReroll(player, gamePlayer);
        }
        // 상점 유닛 (48~52칸)
        else if (slot >= 47 && slot <= 51) {
            int shopIndex = slot - 47;
            handleUnitPurchase(player, gamePlayer, shopIndex);
        }
        // 빈 칸 (53칸) - 클릭 무시
        else if (slot == 52) {
            // 빈 칸
        }
        // 관전 버튼 (54칸)
        else if (slot == 53) {
            if (guiManager.isSpectating(player.getUniqueId())) {
                guiManager.stopSpectating(player);
            } else {
                guiManager.openSpectateList(player);
            }
        }
    }

    /**
     * 배치판 슬롯인지 확인
     */
    private boolean isBoardSlot(int slot) {
        // 1~8, 10~17, 19~26칸
        return (slot >= 0 && slot <= 7) ||
               (slot >= 9 && slot <= 16) ||
               (slot >= 18 && slot <= 25);
    }

    /**
     * 배치판 슬롯 클릭 처리
     */
    private void handleBoardSlotClick(Player player, GamePlayer gamePlayer, int slot, ItemStack item) {
        // 슬롯을 Position으로 변환
        Position pos = slotToPosition(slot);
        if (pos == null) return;

        Unit unitAtPos = gamePlayer.getBoard().get(pos);

        if (unitAtPos != null) {
            // 배치된 유닛 클릭 - 벤치로 이동
            if (gamePlayer.addUnitToBench(unitAtPos)) {
                gamePlayer.removeUnitFromBoard(pos);
                player.sendMessage("§a유닛을 벤치로 이동했습니다!");
                guiManager.setupGameInventory(player, gamePlayer);
                // 스코어보드 업데이트 (시너지 변경)
                GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
                if (game != null) {
                    game.updateAllScoreboards();
                }
            } else {
                player.sendMessage("§c벤치가 가득 찼습니다!");
            }
        } else {
            // 빈 칸 클릭 - 선택된 유닛 배치
            Unit selectedUnit = guiManager.getSelectedUnitForPlacement(player.getUniqueId());
            if (selectedUnit != null) {
                // 벤치에서 제거하고 보드에 배치
                if (gamePlayer.removeUnitFromBench(selectedUnit)) {
                    if (gamePlayer.placeUnit(selectedUnit, pos)) {
                        player.sendMessage("§a유닛을 배치했습니다!");
                        guiManager.clearSelectedUnitForPlacement(player.getUniqueId());
                        guiManager.setupGameInventory(player, gamePlayer);
                        // 스코어보드 업데이트 (시너지 변경)
                        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
                        if (game != null) {
                            game.updateAllScoreboards();
                        }
                    } else {
                        // 배치 실패 - 벤치에 다시 추가
                        gamePlayer.addUnitToBench(selectedUnit);
                        player.sendMessage("§c배치할 수 없습니다! (최대 유닛 수 초과)");
                    }
                }
            } else {
                player.sendMessage("§e먼저 벤치에서 유닛을 선택하세요!");
            }
        }
    }

    /**
     * 벤치 슬롯 클릭 처리
     */
    private void handleBenchSlotClick(Player player, GamePlayer gamePlayer, int slot, ItemStack item, InventoryClickEvent event, GamePhase currentPhase) {
        if (item == null || item.getType() == Material.AIR || !NBTUtils.isUnitItem(item, plugin.getKey())) {
            return;
        }

        // 유닛 정보 가져오기
        UUID unitInstanceId = NBTUtils.getUnitInstanceId(item, plugin.getKey());

        // 벤치에서 해당 유닛 찾기
        Unit selectedUnit = gamePlayer.getBench().stream()
                .filter(unit -> unit.getInstanceId().equals(unitInstanceId))
                .findFirst()
                .orElse(null);

        if (selectedUnit != null) {
            // Shift + 우클릭: 즉시 판매 (전투 중에도 가능)
            if (event.isRightClick() && event.isShiftClick()) {
                int goldGained = gamePlayer.sellUnit(selectedUnit);
                player.sendMessage("§a유닛을 판매했습니다! §6+" + goldGained + "G");
                guiManager.setupGameInventory(player, gamePlayer);
                // 스코어보드 업데이트 (시너지 변경)
                GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
                if (game != null) {
                    game.updateAllScoreboards();
                }
            }
            // 좌클릭: 배치용으로 선택 (전투 중에는 이미 막힘)
            else if (event.isLeftClick()) {
                guiManager.selectUnitForPlacement(player.getUniqueId(), selectedUnit);
                player.sendMessage("§e" + selectedUnit.getName() + " §7선택! 빈 배치판 칸을 클릭하세요.");
            }
        }
    }

    /**
     * 슬롯을 Position으로 변환
     */
    private Position slotToPosition(int slot) {
        int row, col;

        if (slot >= 0 && slot <= 7) {
            // 1줄 (슬롯 0~7 = 1~8칸)
            row = 0;
            col = slot;
        } else if (slot >= 9 && slot <= 16) {
            // 2줄 (슬롯 9~16 = 10~17칸)
            row = 1;
            col = slot - 9;
        } else if (slot >= 18 && slot <= 25) {
            // 3줄 (슬롯 18~25 = 19~26칸)
            row = 2;
            col = slot - 18;
        } else {
            return null;
        }

        return new Position(col, row);
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
     * 관전 목록 클릭
     */
    private void handleSpectateListClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwningPlayer() == null) return;

        UUID targetId = meta.getOwningPlayer().getUniqueId();

        // 🚨 핵심 해결책: 인벤토리를 닫거나 다른 GUI를 열기 전에 커서의 아이템을 비운다.
        // 현재 커서에 관전 플레이어 목록 아이콘이 들려있을 수 있다.
        if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
            event.setCursor(new ItemStack(Material.AIR));
        }

        player.closeInventory();

        guiManager.startSpectating(player, targetId);
    }

    /**
     * 드래그 이벤트
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // 54칸 통합 GUI와 플레이어 목록 GUI에서는 드래그 금지
        String title = event.getView().getTitle();
        if (title.contains("마토체스") || title.contains("플레이어 목록")) {
            event.setCancelled(true);
        }
    }
}
