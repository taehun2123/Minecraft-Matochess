package com.matochess.gui;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.data.Unit;
import com.matochess.game.ShopManager;
import com.matochess.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 모든 GUI 상호작용 관리
 * 상점, 벤치, 장비 보관함 GUI 생성 및 처리
 */
public class GUIManager {

    private final MatoChessPlugin plugin;
    private final ShopManager shopManager;

    // 플레이어 상점 저장
    private final Map<UUID, List<Unit>> playerShops;

    public GUIManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = new ShopManager(plugin);
        this.playerShops = new HashMap<>();
    }

    /**
     * 플레이어에게 상점 GUI 열기
     */
    public void openShop(Player player, GamePlayer gamePlayer) {
        Inventory shop = Bukkit.createInventory(null, 54, "§6§l상점 - 라운드 " +
            plugin.getGameManager().getPlayerGame(player.getUniqueId()).getCurrentRound());

        // 기존 상점 가져오기 또는 새로 생성
        List<Unit> units = playerShops.computeIfAbsent(player.getUniqueId(),
            k -> shopManager.generateShop(gamePlayer.getLevel()));

        // 슬롯 10-14에 유닛 표시
        for (int i = 0; i < units.size() && i < 5; i++) {
            Unit unit = units.get(i);
            if (unit != null) {
                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());
                shop.setItem(10 + i, unitItem);
            }
        }

        // 벤치 유닛 표시 (슬롯 36-44)
        List<Unit> bench = gamePlayer.getBench();
        for (int i = 0; i < bench.size() && i < 9; i++) {
            Unit unit = bench.get(i);
            if (unit != null) {
                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());
                ItemMeta meta = unitItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§eShift+클릭: §c판매 (+" + calculateSellPrice(unit) + "G)");
                meta.setLore(lore);
                unitItem.setItemMeta(meta);
                shop.setItem(36 + i, unitItem);
            }
        }

        // 벤치 라벨
        ItemStack benchLabel = new ItemStack(Material.CHEST);
        ItemMeta benchMeta = benchLabel.getItemMeta();
        benchMeta.setDisplayName("§e§l벤치");
        List<String> benchLore = new ArrayList<>();
        benchLore.add("§7유닛: §e" + bench.size() + " / 9");
        benchLore.add("§7Shift+클릭으로 판매");
        benchMeta.setLore(benchLore);
        benchLabel.setItemMeta(benchMeta);
        shop.setItem(27, benchLabel);

        // 정보 패널
        ItemStack infoItem = createInfoItem(gamePlayer);
        shop.setItem(4, infoItem);

        // 새로고침 버튼
        ItemStack rerollItem = new ItemStack(Material.ARROW);
        ItemMeta rerollMeta = rerollItem.getItemMeta();
        rerollMeta.setDisplayName("§6상점 새로고침");
        List<String> rerollLore = new ArrayList<>();
        rerollLore.add("§7비용: §6" + shopManager.getRerollCost() + "G");
        rerollLore.add("§e클릭하여 유닛 새로고침!");
        rerollMeta.setLore(rerollLore);
        rerollItem.setItemMeta(rerollMeta);
        shop.setItem(48, rerollItem);

        // 레벨업 버튼
        ItemStack levelUpItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta levelUpMeta = levelUpItem.getItemMeta();
        levelUpMeta.setDisplayName("§a경험치 구매");
        List<String> levelUpLore = new ArrayList<>();
        levelUpLore.add("§7비용: §6" + plugin.getConfig().getInt("game.xp-cost-gold", 4) + "G");
        levelUpLore.add("§7획득: §a" + plugin.getConfig().getInt("game.xp-per-purchase", 2) + " XP");
        levelUpLore.add("§e클릭하여 경험치 구매!");
        levelUpMeta.setLore(levelUpLore);
        levelUpItem.setItemMeta(levelUpMeta);
        shop.setItem(50, levelUpItem);

        player.openInventory(shop);
    }

    /**
     * 플레이어의 상점 새로고침
     */
    public void rerollShop(Player player, GamePlayer gamePlayer) {
        int cost = shopManager.getRerollCost();

        if (!gamePlayer.spendGold(cost)) {
            player.sendMessage("§c골드가 부족합니다! (필요: " + cost + "G)");
            return;
        }

        // 새로운 상점 생성
        List<Unit> newShop = shopManager.generateShop(gamePlayer.getLevel());
        playerShops.put(player.getUniqueId(), newShop);

        // GUI 새로고침
        player.closeInventory();
        openShop(player, gamePlayer);

        player.sendMessage("§a상점을 새로고침했습니다!");
    }

    /**
     * 상점에서 유닛 구매
     */
    public boolean buyUnit(Player player, GamePlayer gamePlayer, int shopSlot) {
        List<Unit> shop = playerShops.get(player.getUniqueId());
        if (shop == null || shopSlot >= shop.size()) {
            return false;
        }

        Unit unit = shop.get(shopSlot);
        if (unit == null) {
            return false;
        }

        // 플레이어가 충분한 골드를 가지고 있는지 확인
        if (!gamePlayer.spendGold(unit.getCost())) {
            player.sendMessage("§c골드가 부족합니다! (필요: " + unit.getCost() + "G)");
            return false;
        }

        // 벤치에 추가
        if (!gamePlayer.addUnitToBench(unit)) {
            player.sendMessage("§c벤치가 가득 찼습니다!");
            gamePlayer.addGold(unit.getCost()); // 환불
            return false;
        }

        // 상점에서 제거
        shop.set(shopSlot, null);

        // 자동 강화
        int upgraded = gamePlayer.autoUpgradeUnits();
        if (upgraded > 0) {
            player.sendMessage("§a" + upgraded + "개 유닛이 강화되었습니다!");
        }

        player.sendMessage("§a" + unit.getName() + " §e구매 완료!");
        return true;
    }

    /**
     * 장비 보관함 GUI 열기
     */
    public void openEquipmentStorage(Player player, GamePlayer gamePlayer) {
        Inventory storage = Bukkit.createInventory(null, 54, "§d§l장비 보관함");

        // 장비 아이템 추가
        List<ItemStack> equipmentItems = new ArrayList<>();
        // TODO: NBTUtils를 사용하여 장비를 아이템으로 변환

        for (int i = 0; i < equipmentItems.size() && i < 54; i++) {
            storage.setItem(i, equipmentItems.get(i));
        }

        player.openInventory(storage);
    }

    /**
     * 플레이어 통계를 보여주는 정보 아이템 생성
     */
    private ItemStack createInfoItem(GamePlayer gamePlayer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6§l" + gamePlayer.getPlayer().getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7레벨: §e" + gamePlayer.getLevel() + " §8(최대: " + gamePlayer.getMaxBoardUnits() + " 유닛)");
        lore.add("§7골드: §6" + gamePlayer.getGold() + "G");
        lore.add("§7경험치: §a" + gamePlayer.getExperience() + " §8(다음 레벨: " +
                 (gamePlayer.getLevel() < 8 ? "?" : "MAX") + ")");
        lore.add("§7체력: §c" + gamePlayer.getHealth() + " HP");
        lore.add("");
        lore.add("§7연승: §a" + gamePlayer.getWinStreak());
        lore.add("§7승리 라운드: §a" + gamePlayer.getRoundsWon());
        lore.add("§7패배 라운드: §c" + gamePlayer.getRoundsLost());

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 플레이어의 핫바를 벤치 유닛으로 업데이트
     */
    public void updateBench(Player player, GamePlayer gamePlayer) {
        // TODO: 벤치 유닛을 아이템으로 변환하고 플레이어 인벤토리 슬롯 0-8에 설정
    }

    /**
     * 유닛의 판매 가격 계산
     */
    private int calculateSellPrice(Unit unit) {
        int basePrice = unit.getCost();
        if (unit.getLevel() != com.matochess.data.UnitLevel.ONE) {
            return basePrice * unit.getLevel().getLevel();
        }
        return basePrice;
    }

    /**
     * 유닛 판매 및 골드 반환
     */
    public boolean sellUnit(Player player, GamePlayer gamePlayer, Unit unit) {
        int goldGained = gamePlayer.sellUnit(unit);
        if (goldGained > 0) {
            player.sendMessage("§a유닛을 판매했습니다! §6+" + goldGained + "G");
            return true;
        }
        player.sendMessage("§c유닛을 찾을 수 없습니다!");
        return false;
    }

    /**
     * 보드 배치 GUI 열기
     */
    public void openBoardGUI(Player player, GamePlayer gamePlayer) {
        // 8x3 배치판 GUI (27칸 = 3줄)
        Inventory boardGUI = Bukkit.createInventory(null, 27, "§2§l배치판 - 8x3");

        // 현재 보드 상태 표시 (플레이어의 3줄만)
        Map<com.matochess.data.Position, Unit> board = gamePlayer.getBoard();

        for (Map.Entry<com.matochess.data.Position, Unit> entry : board.entrySet()) {
            com.matochess.data.Position pos = entry.getKey();
            Unit unit = entry.getValue();

            // 플레이어는 Y=0,1,2 (앞 3줄)에만 배치 가능
            if (pos.getY() < 3 && pos.getX() < 8) {
                int slot = (pos.getY() * 9) + pos.getX();

                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                // 설명 추가
                ItemMeta meta = unitItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§7위치: §e(" + pos.getX() + ", " + pos.getY() + ")");
                lore.add("§7클릭하여 벤치로 이동");
                meta.setLore(lore);
                unitItem.setItemMeta(meta);

                boardGUI.setItem(slot, unitItem);
            }
        }

        // 빈 슬롯은 배치 가능한 공간임을 표시
        for (int i = 0; i < 27; i++) {
            if (boardGUI.getItem(i) == null) {
                int x = i % 9;
                int y = i / 9;

                // 8열까지만 사용 (9열째는 구분선)
                if (x < 8) {
                    ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                    ItemMeta emptyMeta = empty.getItemMeta();
                    emptyMeta.setDisplayName("§7빈 공간 (" + x + ", " + y + ")");
                    List<String> lore = new ArrayList<>();
                    lore.add("§e벤치 유닛을 클릭 후");
                    lore.add("§e이곳을 클릭하여 배치");
                    emptyMeta.setLore(lore);
                    empty.setItemMeta(emptyMeta);
                    boardGUI.setItem(i, empty);
                } else {
                    // 9열째는 구분선
                    ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta sepMeta = separator.getItemMeta();
                    sepMeta.setDisplayName("§7│");
                    separator.setItemMeta(sepMeta);
                    boardGUI.setItem(i, separator);
                }
            }
        }

        // 배치 정보를 타이틀에 표시 (인벤토리 크기 제한)
        player.openInventory(boardGUI);
    }

    // 배치를 위해 선택된 유닛 (보드 GUI에서만 사용)
    private final Map<UUID, Unit> selectedBoardUnits = new HashMap<>();

    /**
     * 보드 배치 클릭 처리 (8x3 배치판)
     */
    public void handleBoardClick(Player player, GamePlayer gamePlayer, int slot) {
        // 8x3 배치판 (27칸)
        if (slot < 0 || slot >= 27) return;

        int x = slot % 9;
        int y = slot / 9;

        // 9열째는 구분선
        if (x >= 8) return;

        com.matochess.data.Position position = new com.matochess.data.Position(x, y);

        // 이 위치에 유닛이 있는지 확인
        Unit unitAtPos = gamePlayer.getBoard().get(position);

        if (unitAtPos != null) {
            // 유닛 클릭 - 벤치로 이동
            if (gamePlayer.addUnitToBench(unitAtPos)) {
                gamePlayer.removeUnitFromBoard(position);
                player.sendMessage("§a유닛을 벤치로 이동했습니다!");

                // 인벤토리와 배치판 GUI 새로고침
                plugin.getInventoryGUIManager().setupGameInventory(player, gamePlayer);
                openBoardGUI(player, gamePlayer);
            } else {
                player.sendMessage("§c벤치가 가득 찼습니다!");
            }
        } else {
            // 빈 공간 클릭 - 선택된 유닛을 배치
            Unit selectedUnit = selectedBoardUnits.get(player.getUniqueId());
            if (selectedUnit != null) {
                // 벤치에서 제거하고 보드에 배치
                if (gamePlayer.removeUnitFromBench(selectedUnit)) {
                    if (gamePlayer.placeUnit(selectedUnit, position)) {
                        player.sendMessage("§a유닛을 배치했습니다!");
                        selectedBoardUnits.remove(player.getUniqueId());

                        // 인벤토리와 배치판 GUI 새로고침
                        plugin.getInventoryGUIManager().setupGameInventory(player, gamePlayer);
                        openBoardGUI(player, gamePlayer);
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
     * 벤치 유닛 선택 (배치판 열 때 호출)
     */
    public void selectUnitForPlacement(Player player, Unit unit) {
        selectedBoardUnits.put(player.getUniqueId(), unit);
        player.sendMessage("§a" + unit.getName() + " §e선택! 배치할 위치를 클릭하세요.");
    }
}
