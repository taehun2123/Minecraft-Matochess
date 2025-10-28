package com.matochess.gui;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.data.Position;
import com.matochess.data.Unit;
import com.matochess.game.ShopManager;
import com.matochess.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * 새로운 인벤토리 기반 GUI 시스템
 * E키로 열리는 통합 배치/상점 인벤토리
 */
public class InventoryGUIManager {

    private final MatoChessPlugin plugin;
    private final ShopManager shopManager;

    // 플레이어별 상점 유닛
    private final Map<UUID, List<Unit>> playerShops;

    // 플레이어별 상점 열림/닫힘 상태
    private final Map<UUID, Boolean> shopOpenState;

    // 관전 모드 플레이어 추적
    private final Map<UUID, UUID> spectatingPlayers; // 관전자 -> 관전대상

    // 배치를 위해 선택된 유닛 추적
    private final Map<UUID, Unit> selectedUnits; // 플레이어 -> 선택한 유닛

    // 판매를 위해 선택된 유닛 추적
    private final Map<UUID, Unit> selectedUnitsForSale; // 플레이어 -> 판매할 유닛

    // 슬롯 상수
    private static final int HOTBAR_XP_SLOT = 0;     // 1번 칸
    private static final int HOTBAR_REROLL_SLOT = 1; // 2번 칸
    private static final int HOTBAR_SHOP_START = 2;  // 3번 칸 (인덱스 2)
    private static final int HOTBAR_SHOP_END = 6;    // 7번 칸 (인덱스 6)
    private static final int HOTBAR_SPECTATE = 8;    // 9번 칸

    private static final int INV_BENCH_START = 17;   // 벤치 시작 (1줄)
    private static final int INV_BENCH_END = 26;     // 벤치 끝 (1줄, 9칸)
    private static final int INV_SELL_START = 27;    // 판매 영역 시작 (2번째 줄)
    private static final int INV_SELL_END = 36;      // 판매 영역 끝 (2번째 줄, 9칸)

    public InventoryGUIManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = new ShopManager(plugin);
        this.playerShops = new HashMap<>();
        this.shopOpenState = new HashMap<>();
        this.spectatingPlayers = new HashMap<>();
        this.selectedUnits = new HashMap<>();
        this.selectedUnitsForSale = new HashMap<>();
    }

    /**
     * 플레이어 상점 리셋 (새 라운드 시작 시)
     */
    public void resetPlayerShop(UUID playerId) {
        playerShops.remove(playerId);
    }

    /**
     * 판매용 유닛 선택
     */
    public void selectUnitForSale(UUID playerId, Unit unit) {
        selectedUnitsForSale.put(playerId, unit);
    }

    /**
     * 판매용으로 선택된 유닛 가져오기
     */
    public Unit getSelectedUnitForSale(UUID playerId) {
        return selectedUnitsForSale.get(playerId);
    }

    /**
     * 판매용 유닛 선택 해제
     */
    public void clearSelectedUnitForSale(UUID playerId) {
        selectedUnitsForSale.remove(playerId);
    }

    /**
     * 플레이어에게 메인 게임 인벤토리 설정
     */
    public void setupGameInventory(Player player, GamePlayer gamePlayer) {
        player.getInventory().clear();

        // 핫바 설정
        setupHotbar(player, gamePlayer, true);

        // 벤치 유닛 표시 (인벤토리 중간 줄)
        setupBenchSlots(player, gamePlayer);

        // 판매 슬롯 표시 (인벤토리 하단 줄)
        setupSellSlots(player);

        player.updateInventory();
    }

    /**
     * 핫바 설정
     */
    private void setupHotbar(Player player, GamePlayer gamePlayer, boolean shopOpen) {
        Inventory inv = player.getInventory();

        // 1칸: 경험치 업 버튼
        ItemStack xpButton = createXPButton(gamePlayer);
        inv.setItem(HOTBAR_XP_SLOT, xpButton);

        // 2칸: 리롤 버튼
        ItemStack rerollButton = createRerollButton(gamePlayer);
        inv.setItem(HOTBAR_REROLL_SLOT, rerollButton);

        // 3~7칸: 상점 유닛 또는 빈 칸
        if (shopOpen) {
            List<Unit> shopUnits = playerShops.computeIfAbsent(player.getUniqueId(),
                k -> shopManager.generateShop(gamePlayer.getLevel()));

            for (int i = 0; i < 5; i++) {
                int slot = HOTBAR_SHOP_START + i;
                if (i < shopUnits.size() && shopUnits.get(i) != null) {
                    Unit unit = shopUnits.get(i);
                    ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                    // 가격 정보 추가
                    ItemMeta meta = unitItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add("");
                    lore.add("§6구매 비용: " + unit.getCost() + "G");
                    lore.add("§e클릭하여 구매");
                    meta.setLore(lore);
                    unitItem.setItemMeta(meta);

                    inv.setItem(slot, unitItem);
                } else {
                    inv.setItem(slot, null);
                }
            }
        } else {
            // 상점 닫힘 상태 - 빈 칸
            for (int i = HOTBAR_SHOP_START; i <= HOTBAR_SHOP_END; i++) {
                inv.setItem(i, null);
            }
        }


        // 9칸: 다른 플레이어 배치판 보기
        ItemStack spectateButton = createSpectateButton();
        inv.setItem(HOTBAR_SPECTATE, spectateButton);
    }

    /**
     * 경험치 업 버튼 생성
     */
    private ItemStack createXPButton(GamePlayer gamePlayer) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a경험치 구매");

        List<String> lore = new ArrayList<>();
        lore.add("§7현재 레벨: §e" + gamePlayer.getLevel());
        lore.add("§7경험치: §a" + gamePlayer.getExperience() + " §7/ §a?"); // TODO: 최대 경험치
        lore.add("");

        int xpCost = plugin.getConfig().getInt("game.xp-cost-gold", 4);
        int xpGain = plugin.getConfig().getInt("game.xp-per-purchase", 2);

        lore.add("§e클릭 시:");
        lore.add("§7  - 경험치 §a+" + xpGain);
        lore.add("§7  - 골드 소모 §6-" + xpCost + "G");
        lore.add("");
        lore.add("§7현재 골드: §6" + gamePlayer.getGold() + "G");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 리롤 버튼 생성
     */
    private ItemStack createRerollButton(GamePlayer gamePlayer) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6상점 새로고침");

        List<String> lore = new ArrayList<>();
        int rerollCost = shopManager.getRerollCost();

        lore.add("§7상점 유닛을 새로 뽑습니다");
        lore.add("");
        lore.add("§e클릭 시:");
        lore.add("§7  - 상점 유닛 §a새로고침");
        lore.add("§7  - 골드 소모 §6-" + rerollCost + "G");
        lore.add("");
        lore.add("§7현재 골드: §6" + gamePlayer.getGold() + "G");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }


    /**
     * 관전 버튼 생성
     */
    private ItemStack createSpectateButton() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d다른 플레이어 보기");

        List<String> lore = new ArrayList<>();
        lore.add("§7클릭하여 다른 플레이어의");
        lore.add("§7배치판을 확인합니다");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 벤치 슬롯 설정 (인벤토리 중간 줄)
     */
    private void setupBenchSlots(Player player, GamePlayer gamePlayer) {
        Inventory inv = player.getInventory();
        List<Unit> bench = gamePlayer.getBench();

        for (int i = 0; i < 9; i++) {
            int slot = INV_BENCH_START + i;
            if (i < bench.size() && bench.get(i) != null) {
                Unit unit = bench.get(i);
                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                ItemMeta meta = unitItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§7위쪽 배치판으로 드래그하여 배치");
                lore.add("§7아래쪽 판매 슬롯으로 드래그하여 판매");
                meta.setLore(lore);
                unitItem.setItemMeta(meta);

                inv.setItem(slot, unitItem);
            } else {
                inv.setItem(slot, null);
            }
        }
    }

    /**
     * 판매 슬롯 설정 (인벤토리 하단 줄)
     */
    private void setupSellSlots(Player player) {
        Inventory inv = player.getInventory();

        // 판매 슬롯 전체를 판매 아이콘으로 채움
        ItemStack sellIcon = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = sellIcon.getItemMeta();
        meta.setDisplayName("§c§l판매 영역");
        List<String> lore = new ArrayList<>();
        lore.add("§7벤치 유닛을 우클릭하고");
        lore.add("§7이 영역을 클릭하여 판매");
        meta.setLore(lore);
        sellIcon.setItemMeta(meta);

        for (int i = INV_SELL_START; i <= INV_SELL_END; i++) {
            inv.setItem(i, sellIcon);
        }
    }

    /**
     * 배치판 GUI 열기 (상자 형태, 상단)
     */
    public void openBoardGUI(Player player, GamePlayer gamePlayer) {
        // 54칸 더블 체스트 (8x6 = 48칸 사용)
        Inventory boardInv = Bukkit.createInventory(null, 27, "§2§l배치판");

        // 현재 보드 상태 표시 (8x6)
        Map<Position, Unit> board = gamePlayer.getBoard();

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 8; x++) {
                int slot = y * 9 + x; // 9열씩, x 위치
                Position pos = new Position(x, y);

                Unit unit = board.get(pos);
                if (unit != null) {
                    ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                    ItemMeta meta = unitItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add("");
                    lore.add("§7위치: §e" + x + ", " + y);
                    lore.add("§7클릭하여 벤치로 이동");
                    meta.setLore(lore);
                    unitItem.setItemMeta(meta);

                    boardInv.setItem(slot, unitItem);
                } else {
                    // 빈 슬롯 표시
                    ItemStack emptySlot = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                    ItemMeta meta = emptySlot.getItemMeta();
                    meta.setDisplayName("§7빈 칸");
                    emptySlot.setItemMeta(meta);
                    boardInv.setItem(slot, emptySlot);
                }
            }
        }

        // 8번째 열은 구분선 (사용 안 함)
        for (int i = 8; i < 54; i += 9) {
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = separator.getItemMeta();
            meta.setDisplayName(" ");
            separator.setItemMeta(meta);
            boardInv.setItem(i, separator);
        }

        player.openInventory(boardInv);
    }

    /**
     * 상점 토글
     */
    public void toggleShop(Player player, GamePlayer gamePlayer) {
        UUID playerId = player.getUniqueId();
        boolean currentState = shopOpenState.getOrDefault(playerId, true);
        shopOpenState.put(playerId, !currentState);

        setupGameInventory(player, gamePlayer);

        if (!currentState) {
            player.sendMessage("§a상점을 열었습니다!");
        } else {
            player.sendMessage("§c상점을 닫았습니다!");
        }
    }

    /**
     * 상점 새로고침
     */
    public void rerollShop(Player player, GamePlayer gamePlayer) {
        int cost = shopManager.getRerollCost();

        if (!gamePlayer.spendGold(cost)) {
            player.sendMessage("§c골드가 부족합니다! (필요: " + cost + "G)");
            return;
        }

        // 새 상점 생성
        List<Unit> newShop = shopManager.generateShop(gamePlayer.getLevel());
        playerShops.put(player.getUniqueId(), newShop);

        setupGameInventory(player, gamePlayer);
        player.sendMessage("§a상점을 새로고침했습니다!");
    }

    /**
     * 상점에서 유닛 구매
     */
    public boolean purchaseUnit(Player player, GamePlayer gamePlayer, int shopIndex) {
        UUID playerId = player.getUniqueId();
        List<Unit> shopUnits = playerShops.get(playerId);

        // 상점 유효성 검증
        if (shopUnits == null || shopIndex < 0 || shopIndex >= shopUnits.size()) {
            player.sendMessage("§c잘못된 상점 슬롯입니다!");
            return false;
        }

        Unit unit = shopUnits.get(shopIndex);
        if (unit == null) {
            player.sendMessage("§c해당 슬롯에 유닛이 없습니다!");
            return false;
        }

        // 골드 확인
        if (gamePlayer.getGold() < unit.getCost()) {
            player.sendMessage("§c골드가 부족합니다! (필요: " + unit.getCost() + "G, 보유: " + gamePlayer.getGold() + "G)");
            return false;
        }

        // 벤치 공간 확인
        if (gamePlayer.getBench().size() >= 9) {
            player.sendMessage("§c벤치가 가득 찼습니다!");
            return false;
        }

        // 유닛 구매
        gamePlayer.spendGold(unit.getCost());
        gamePlayer.addUnitToBench(unit);

        // 상점에서 제거
        shopUnits.set(shopIndex, null);

        player.sendMessage("§a유닛을 구매했습니다! §7(" + unit.getName() + " - §6" + unit.getCost() + "G§7)");

        // 인벤토리 새로고침
        setupGameInventory(player, gamePlayer);

        return true;
    }

    /**
     * 관전 모드 플레이어 목록 GUI 열기
     */
    public void openSpectateList(Player player) {
        var game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) return;

        Map<UUID, com.matochess.data.GamePlayer> players = game.getPlayers();

        // 플레이어 머리 목록 GUI
        int size = Math.min(54, ((players.size() - 1) / 9 + 1) * 9);
        Inventory spectateInv = Bukkit.createInventory(null, size, "§d§l플레이어 목록");

        int slot = 0;
        for (com.matochess.data.GamePlayer gp : players.values()) {
            if (gp.getPlayerId().equals(player.getUniqueId())) continue; // 자기 자신 제외

            Player target = gp.getPlayer();
            if (target == null) continue;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7레벨: §e" + gp.getLevel());
            lore.add("§7체력: §c" + gp.getHealth() + " HP");
            lore.add("");
            lore.add("§a클릭하여 배치판 보기");
            meta.setLore(lore);

            skull.setItemMeta(meta);
            spectateInv.setItem(slot++, skull);
        }

        player.openInventory(spectateInv);
    }

    /**
     * 관전 모드 시작
     */
    public void startSpectating(Player spectator, UUID targetId) {
        spectatingPlayers.put(spectator.getUniqueId(), targetId);

        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            // 타겟 위치로 텔레포트
            spectator.teleport(target.getLocation());
            spectator.sendMessage("§a" + target.getName() + "의 배치판을 보고 있습니다.");

            // 핫바 9번 칸에 돌아가기 버튼 설정
            ItemStack returnButton = new ItemStack(Material.ARROW);
            ItemMeta meta = returnButton.getItemMeta();
            meta.setDisplayName("§e내 배치판으로 돌아가기");
            returnButton.setItemMeta(meta);
            spectator.getInventory().setItem(8, returnButton);
        }
    }

    /**
     * 관전 모드 종료
     */
    public void stopSpectating(Player player) {
        spectatingPlayers.remove(player.getUniqueId());

        var game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game != null) {
            var gamePlayer = game.getPlayer(player.getUniqueId());
            if (gamePlayer != null) {
                setupGameInventory(player, gamePlayer);
                player.sendMessage("§a내 배치판으로 돌아왔습니다.");
            }
        }
    }

    /**
     * 플레이어가 관전 중인지 확인
     */
    public boolean isSpectating(UUID playerId) {
        return spectatingPlayers.containsKey(playerId);
    }

    /**
     * 플레이어 상점 정리
     */
    public void clearPlayerShop(UUID playerId) {
        playerShops.remove(playerId);
        shopOpenState.remove(playerId);
        spectatingPlayers.remove(playerId);
    }
}
