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

    // 관전 모드 플레이어 추적
    private final Map<UUID, UUID> spectatingPlayers; // 관전자 -> 관전대상

    // 배치를 위해 선택된 유닛 추적
    private final Map<UUID, Unit> selectedUnits; // 플레이어 -> 선택한 유닛

    // 판매를 위해 선택된 유닛 추적
    private final Map<UUID, Unit> selectedUnitsForSale; // 플레이어 -> 판매할 유닛

    // 54칸 통합 GUI 슬롯 상수
    // 1~8칸, 10~17칸, 19~26칸: 배치판 (8x3 = 24칸)
    private static final int[] BOARD_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7,        // 1줄 (1~8칸)
        9, 10, 11, 12, 13, 14, 15, 16, // 2줄 (10~17칸)
        18, 19, 20, 21, 22, 23, 24, 25 // 3줄 (19~26칸)
    };
    private static final int INFO_HEALTH_SLOT = 8;     // 9칸: 체력 정보
    private static final int INFO_SYNERGY_SLOT = 17;   // 18칸: 시너지 정보
    private static final int INFO_RESOURCES_SLOT = 26; // 27칸: 재화 정보

    // 28~35칸: 대기 병력 (벤치)
    public static final int INV_BENCH_START = 27;
    public static final int INV_BENCH_END = 34;

    // 36칸: 레벨/경험치 정보
    private static final int INFO_LEVEL_SLOT = 35;

    // 37~45칸: 검정 유리판 (필러)
    private static final int FILLER_START = 36;
    private static final int FILLER_END = 44;

    // 46칸: 경험치 업
    private static final int BUTTON_XP_SLOT = 45;

    // 47칸: 리롤
    private static final int BUTTON_REROLL_SLOT = 46;

    // 48~52칸: 상점 유닛
    private static final int SHOP_START = 47;
    private static final int SHOP_END = 51;

    // 53칸: 비어있음
    private static final int EMPTY_SLOT = 52;

    // 54칸: 다른 플레이어 탐색
    private static final int BUTTON_SPECTATE_SLOT = 53;

    public InventoryGUIManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.shopManager = new ShopManager(plugin);
        this.playerShops = new HashMap<>();
        this.spectatingPlayers = new HashMap<>();
        this.selectedUnits = new HashMap<>();
        this.selectedUnitsForSale = new HashMap<>();
    }

    /**
     * 특정 플레이어가 현재 관전 중인 대상의 UUID를 반환합니다.
     * @param spectatorId 관전자 UUID
     * @return 관전 대상의 UUID. 관전 중이 아니면 null을 반환합니다.
     */
    public UUID getSpectatingTarget(UUID spectatorId) {
        // spectatingPlayers는 InventoryGUIManager에서 관리하는 Map<UUID, UUID>입니다.
        return spectatingPlayers.get(spectatorId);
    }

    /**
     * 플레이어 상점 리셋 (새 라운드 시작 시)
     */
    public void resetPlayerShop(UUID playerId) {
        playerShops.remove(playerId);
    }

    /*
     * == 배치용 유닛 추적 메서드 추가 ==
     */

    /**
     * 배치를 위해 유닛 선택
     */
    public void selectUnitForPlacement(UUID playerId, Unit unit) {
        selectedUnits.put(playerId, unit);
    }

    /**
     * 배치를 위해 선택된 유닛 가져오기
     */
    public Unit getSelectedUnitForPlacement(UUID playerId) {
        return selectedUnits.get(playerId);
    }

    /**
     * 배치를 위해 선택된 유닛 해제
     */
    public void clearSelectedUnitForPlacement(UUID playerId) {
        selectedUnits.remove(playerId);
    }

    /*
     * == 판매용 유닛 추적 메서드 (이젠 Shift 우클릭으로 즉시 판매되어 선택 로직은 단순화됨) ==
     */

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
     * 플레이어에게 메인 게임 54칸 통합 GUI 열기
     */
    public void setupGameInventory(Player player, GamePlayer gamePlayer) {
        // 54칸 GUI 생성
        Inventory gui = Bukkit.createInventory(null, 54, "§6§l마토체스 - " + player.getName());

        // 배치판 설정 (1~8, 10~17, 19~26칸)
        setupBoardSlots(gui, gamePlayer);

        // 정보 패널 설정 (9, 18, 27칸)
        setupInfoPanels(gui, gamePlayer);

        // 벤치 설정 (28~35칸)
        setupBenchSlots(gui, gamePlayer);

        // 레벨 정보 설정 (36칸)
        setupLevelInfo(gui, gamePlayer);

        // 필러 설정 (37~45칸)
        setupFillers(gui);

        // 버튼 설정 (46, 47, 54칸)
        setupButtons(gui, gamePlayer);

        // 상점 설정 (48~52칸)
        setupShop(gui, gamePlayer, player);

        // 빈 칸 (53칸)
        gui.setItem(EMPTY_SLOT, null);

        player.openInventory(gui);
    }

    /**
     * 배치판 슬롯 설정 (1~8, 10~17, 19~26칸)
     */
    private void setupBoardSlots(Inventory gui, GamePlayer gamePlayer) {
        Map<Position, Unit> board = gamePlayer.getBoard();

        for (int i = 0; i < BOARD_SLOTS.length; i++) {
            int slot = BOARD_SLOTS[i];

            // 슬롯을 8x3 그리드로 변환
            int row = i / 8;  // 0, 1, 2
            int col = i % 8;  // 0~7

            Position pos = new Position(col, row);
            Unit unit = board.get(pos);

            if (unit != null) {
                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                ItemMeta meta = unitItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§7위치: §e(" + col + ", " + row + ")");
                lore.add("§e클릭하여 벤치로 이동");
                meta.setLore(lore);
                unitItem.setItemMeta(meta);

                gui.setItem(slot, unitItem);
            } else {
                // 빈 배치판 칸
                ItemStack emptySlot = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = emptySlot.getItemMeta();
                meta.setDisplayName("§7빈 배치판 칸 (" + col + ", " + row + ")");
                List<String> lore = new ArrayList<>();
                lore.add("§7벤치 유닛을 좌클릭한 후");
                lore.add("§7이곳을 클릭하여 배치");
                meta.setLore(lore);
                emptySlot.setItemMeta(meta);
                gui.setItem(slot, emptySlot);
            }
        }
    }

    /**
     * 정보 패널 설정 (9, 18, 27칸)
     */
    private void setupInfoPanels(Inventory gui, GamePlayer gamePlayer) {
        // 9칸: 체력 정보
        ItemStack healthInfo = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta healthMeta = healthInfo.getItemMeta();
        healthMeta.setDisplayName("§c§l체력 정보");
        List<String> healthLore = new ArrayList<>();
        healthLore.add("§7현재 체력: §c" + gamePlayer.getHealth() + " HP");
        healthLore.add("§7승리 연속: §a" + gamePlayer.getWinStreak());
        healthLore.add("§7승리 라운드: §a" + gamePlayer.getRoundsWon());
        healthLore.add("§7패배 라운드: §c" + gamePlayer.getRoundsLost());
        healthMeta.setLore(healthLore);
        healthInfo.setItemMeta(healthMeta);
        gui.setItem(INFO_HEALTH_SLOT, healthInfo);

        // 18칸: 시너지 정보
        ItemStack synergyInfo = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta synergyMeta = synergyInfo.getItemMeta();
        synergyMeta.setDisplayName("§d§l시너지 정보");
        List<String> synergyLore = new ArrayList<>();
        synergyLore.add("§7현재 활성화된 시너지:");
        // TODO: 시너지 계산 및 표시
        synergyLore.add("§8(구현 예정)");
        synergyMeta.setLore(synergyLore);
        synergyInfo.setItemMeta(synergyMeta);
        gui.setItem(INFO_SYNERGY_SLOT, synergyInfo);

        // 27칸: 재화 정보
        ItemStack resourceInfo = new ItemStack(Material.GOLD_INGOT);
        ItemMeta resourceMeta = resourceInfo.getItemMeta();
        resourceMeta.setDisplayName("§6§l재화 정보");
        List<String> resourceLore = new ArrayList<>();
        resourceLore.add("§7보유 골드: §6" + gamePlayer.getGold() + "G");
        resourceLore.add("§7현재 레벨: §e" + gamePlayer.getLevel());
        resourceLore.add("§7현재 경험치: §a" + gamePlayer.getExperience() + " XP");
        resourceMeta.setLore(resourceLore);
        resourceInfo.setItemMeta(resourceMeta);
        gui.setItem(INFO_RESOURCES_SLOT, resourceInfo);
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
     * 벤치 슬롯 설정 (28~35칸)
     */
    private void setupBenchSlots(Inventory gui, GamePlayer gamePlayer) {
        List<Unit> bench = gamePlayer.getBench();

        for (int i = 0; i < 8; i++) {
            int slot = INV_BENCH_START + i;
            if (i < bench.size() && bench.get(i) != null) {
                Unit unit = bench.get(i);
                ItemStack unitItem = NBTUtils.createUnitItem(unit, plugin.getKey());

                ItemMeta meta = unitItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§e좌클릭: 빈 배치판 칸에 배치");
                lore.add("§eShift + 우클릭: 유닛 판매");
                meta.setLore(lore);
                unitItem.setItemMeta(meta);

                gui.setItem(slot, unitItem);
            } else {
                // 빈 벤치 칸
                ItemStack emptyBench = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = emptyBench.getItemMeta();
                meta.setDisplayName("§7빈 벤치 칸");
                List<String> lore = new ArrayList<>();
                lore.add("§7상점에서 유닛을 구매하면");
                lore.add("§7이곳에 배치됩니다");
                meta.setLore(lore);
                emptyBench.setItemMeta(meta);
                gui.setItem(slot, emptyBench);
            }
        }
    }

    /**
     * 레벨 정보 설정 (36칸)
     */
    private void setupLevelInfo(Inventory gui, GamePlayer gamePlayer) {
        ItemStack levelInfo = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = levelInfo.getItemMeta();
        meta.setDisplayName("§e§l레벨 정보");

        List<String> lore = new ArrayList<>();
        lore.add("§7현재 레벨: §e" + gamePlayer.getLevel());
        lore.add("§7현재 경험치: §a" + gamePlayer.getExperience() + " XP");

        // 레벨별 필요 경험치
        int[] xpRequired = new int[9];
        for (int i = 1; i <= 8; i++) {
            xpRequired[i] = plugin.getConfig().getInt("game.xp-required." + i, i * 2);
        }

        if (gamePlayer.getLevel() < 8) {
            int requiredXP = xpRequired[gamePlayer.getLevel()];
            lore.add("§7필요 경험치: §a" + requiredXP + " XP");
        } else {
            lore.add("§6§l최대 레벨!");
        }

        lore.add("");
        lore.add("§7최대 배치 유닛: §e" + gamePlayer.getMaxBoardUnits() + "개");

        meta.setLore(lore);
        levelInfo.setItemMeta(meta);
        gui.setItem(INFO_LEVEL_SLOT, levelInfo);
    }

    /**
     * 필러 슬롯 설정 (37~45칸)
     */
    private void setupFillers(Inventory gui) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int i = FILLER_START; i <= FILLER_END; i++) {
            gui.setItem(i, filler);
        }
    }

    /**
     * 버튼 설정 (46, 47, 54칸)
     */
    private void setupButtons(Inventory gui, GamePlayer gamePlayer) {
        // 46칸: 경험치 업
        ItemStack xpButton = createXPButton(gamePlayer);
        gui.setItem(BUTTON_XP_SLOT, xpButton);

        // 47칸: 리롤
        ItemStack rerollButton = createRerollButton(gamePlayer);
        gui.setItem(BUTTON_REROLL_SLOT, rerollButton);

        // 54칸: 관전 버튼
        ItemStack spectateButton = createSpectateButton();
        gui.setItem(BUTTON_SPECTATE_SLOT, spectateButton);
    }

    /**
     * 상점 설정 (48~52칸)
     */
    private void setupShop(Inventory gui, GamePlayer gamePlayer, Player player) {
        List<Unit> shopUnits = playerShops.computeIfAbsent(player.getUniqueId(),
            k -> shopManager.generateShop(gamePlayer.getLevel()));

        for (int i = 0; i < 5; i++) {
            int slot = SHOP_START + i;
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

                gui.setItem(slot, unitItem);
            } else {
                gui.setItem(slot, null);
            }
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

        // 스코어보드 업데이트
        var game = plugin.getGameManager().getPlayerGame(playerId);
        if (game != null) {
            game.updateAllScoreboards();
        }

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
            Player target = gp.getPlayer();
            if (target == null) continue;
            if (target.getUniqueId().equals(player.getUniqueId())) continue; // 자기 자신 제외

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
        spectatingPlayers.remove(playerId);
    }
}
