package com.matochess.data;

import com.matochess.data.Position;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 게임에 참여 중인 단일 플레이어의 상태를 관리하는 데이터 클래스입니다.
 * 자원, 벤치/배치 유닛, 장비, 라운드 성적 등의 정보를 모두 보관합니다.
 */
@Getter @Setter
public class GamePlayer {

    // Getters and setters
    private final UUID playerId;

    private final Player player;
    // 🚨 새 Getter
    @Getter
    private final UUID gameId; // 🚨 새 필드: 소속된 게임 ID

    // Resources
    private int gold;
    private int experience;
    private int level;
    private int health;

    // Game stats
    private int winStreak;
    private int loseStreak;
    private int roundsWon;
    private int roundsLost;
    private boolean isAlive;
    private int placement; // Final placement when eliminated

    // Units
    private final List<Unit> bench; // 대기판 (waiting units)
    private final Map<Position, Unit> board; // 배치된 유닛
    private final List<Equipment> equipmentStorage; // 장비 보관함

    // Board configuration
    private static final int BENCH_SIZE = 8;
    private static final int BOARD_WIDTH = 8;
    private static final int BOARD_HEIGHT = 3; // 플레이어는 8*3 영역에만 배치 가능

    public GamePlayer(Player player, UUID gameId, int startingGold, int startingLevel, int startingHealth) {
        this.playerId = player.getUniqueId();
        this.player = player;
        this.gameId = gameId; // 🚨 gameId 저장
        this.gold = startingGold;
        this.experience = 0;
        this.level = startingLevel;
        this.health = startingHealth;
        this.winStreak = 0;
        this.loseStreak = 0;
        this.roundsWon = 0;
        this.roundsLost = 0;
        this.isAlive = true;
        this.placement = 0;
        this.bench = new ArrayList<>();
        this.board = new HashMap<>();
        this.equipmentStorage = new ArrayList<>();
    }

    /**
     * 레벨에 따른 최대 유닛 배치 수
     */
    public int getMaxBoardUnits() {
        int maxSlots = BOARD_WIDTH * BOARD_HEIGHT;
        int allowed = Math.max(1, level);
        return Math.min(maxSlots, allowed);
    }

    /**
     * 유저에게 골드 추가
     */
    public void addGold(int amount) {
        this.gold += amount;
    }

    /**
     * 골드 사용
     * @return true 성공적으로 수행됨, false 면 충분하지 않은 골드
     */
    public boolean spendGold(int amount) {
        if (gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    /**
     * 경험치 추가 및 레벨업 체크 로직
     * @return true 면 레벨업, false 면 그렇지 않음
     */
    public boolean addExperience(int amount, int[] xpRequiredPerLevel) {
        experience += amount;

        if (level >= 8) {
            return false; // Max level
        }

        int requiredXp = xpRequiredPerLevel[level];
        if (experience >= requiredXp) {
            experience -= requiredXp;
            level++;
            return true;
        }
        return false;
    }

    /**
     * 데미지 부여
     * @return true 면 플레이어는 여전히 살아있음, false 면 제거됨.
     */
    public boolean takeDamage(int damage) {
        health -= damage;
        if (health <= 0) {
            health = 0;
            isAlive = false;
        }
        return isAlive;
    }

    /**
     * 유닛을 벤치로 보내기
     * @return true 면 성공적 수행, false 면 벤치가 가득 참
     */
    public boolean addUnitToBench(Unit unit) {
        if (bench.size() >= BENCH_SIZE) {
            return false;
        }
        bench.add(unit);

        // 자동 합성은 준비 단계에서만 수동으로 호출됨
        // autoUpgradeUnits();

        return true;
    }

    /**
     * 벤치에서부터 유닛 제거
     */
    public boolean removeUnitFromBench(Unit unit) {
        return bench.remove(unit);
    }

    /**
     * 보드에 유닛 놓기
     * @return true 면 성공적, false 면 배치 인원이 가득 찼거나 최대 유닛에 도달함
     */
    public boolean placeUnitOnBoard(Unit unit, Position position) {
        if (board.size() >= getMaxBoardUnits()) {
            return false;
        }
        if (board.containsKey(position)) {
            return false;
        }
        board.put(position, unit);
        return true;
    }

    /**
     * Alias for placeUnitOnBoard - used by inventory GUI ( 인벤토리 GUI 배치판 )
     */
    public boolean placeUnit(Unit unit, Position position) {
        return placeUnitOnBoard(unit, position);
    }

    /**
     * 현재 배치판에서 해당 좌표의 유닛을 직접 참조로 반환
     */
    public Unit getBoardUnit(Position position) {
        return board.get(position);
    }

    /**
     * 배치판에서부터 유닛 제거
     */
    public Unit removeUnitFromBoard(Position position) {
        return board.remove(position);
    }

    /**
     * Add equipment to storage
     */
    public void addEquipment(Equipment equipment) {
        equipmentStorage.add(equipment);
    }

    /**
     * Remove equipment from storage
     */
    public boolean removeEquipment(Equipment equipment) {
        return equipmentStorage.remove(equipment);
    }

    /**
     * 벤치 + (옵션에 따라) 보드 유닛의 합성 조건을 검사하고 자동으로 업그레이드합니다.
     * 준비 단계에서는 includeBoardUnits=true 로 호출되어 보드 우선 합성을 수행하고,
     * 전투 중에는 벤치 유닛만 합성하도록 false 로 호출합니다.
     * @return 합성이 일어난 횟수
     */
    public int autoUpgradeUnits() {
        return autoUpgradeUnits(true);
    }

    /**
     * 내부 합성 루틴. includeBoardUnits 가 false 면 벤치끼리만 합성됩니다.
     * 연속적인 합성을 보장하기 위해 do-while 루프를 사용합니다.
     */
    public int autoUpgradeUnits(boolean includeBoardUnits) {
        int totalUpgraded = 0;
        boolean madeUpgrade;

        do {
            madeUpgrade = false; // 이번 루프에서 합성이 일어났는지 추적

            // 1. 매번 반복 시마다 벤치와 보드의 현재 유닛 목록으로 그룹을 재구성
            Map<String, List<Unit>> unitsByIdAndLevel = new HashMap<>();

            // 벤치의 모든 유닛을 그룹화
            for (Unit unit : bench) {
                String key = unit.getId() + "_" + unit.getLevel().getLevel();
                unitsByIdAndLevel.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
            }

            // 보드의 모든 유닛도 그룹화
            if (includeBoardUnits) {
                for (Unit unit : board.values()) {
                    String key = unit.getId() + "_" + unit.getLevel().getLevel();
                    unitsByIdAndLevel.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
                }
            }

            // ConcurrentModificationException을 방지하기 위해 values()의 사본을 만듭니다.
            // 이 사본을 반복하는 동안 원본 맵(unitsByIdAndLevel)은 변경되지 않지만,
            // 유닛 리스트(units)는 while 루프 내에서 변경됩니다.
            Collection<List<Unit>> currentGroups = new ArrayList<>(unitsByIdAndLevel.values());

            // 2. 각 그룹을 반복하여 합성 시도
            for (List<Unit> units : currentGroups) {
                // 이 그룹에서 3개 이상 유닛이 될 때까지 계속 합성 시도
                while (units.size() >= 3) {
                    // 우선순위: 보드에 있는 유닛을 base로 선택 (업그레이드 후 보드에 유지하기 위해)
                    Unit base = null;
                    Position basePos = null;

                    if (includeBoardUnits) {
                        // 보드에 있는 유닛을 먼저 찾아서 base로 설정
                        for (Unit unit : units) {
                            Position pos = findUnitPosition(unit);
                            if (pos != null) {
                                base = unit;
                                basePos = pos;
                                break;
                            }
                        }
                    }

                    // 보드에 유닛이 없으면 첫 번째 유닛(벤치)을 base로
                    if (base == null) {
                        base = units.get(0);
                    }

                    // base를 제외한 나머지 2개 선택
                    units.remove(base);
                    Unit second = units.remove(0);
                    Unit third = units.remove(0);

                    // Transfer equipment from combined units
                    List<Equipment> allEquipment = new ArrayList<>();
                    allEquipment.addAll(base.removeAllEquipment());
                    allEquipment.addAll(second.removeAllEquipment());
                    allEquipment.addAll(third.removeAllEquipment());

                    // second와 third를 벤치와 보드에서 제거
                    bench.remove(second);
                    bench.remove(third);

                    Position secondPos = findUnitPosition(second);
                    Position thirdPos = findUnitPosition(third);
                    if (secondPos != null) board.remove(secondPos);
                    if (thirdPos != null) board.remove(thirdPos);

                    // base가 벤치에 있으면 제거 (보드에 있으면 그대로 유지)
                    if (basePos == null) {
                        bench.remove(base);
                    }

                    // Upgrade base unit
                    if (base.upgrade()) {
                        base.setCurrentHealth(base.getHealth());

                        // 업그레이드 후 유닛을 제자리에 다시 배치하거나 벤치에 추가
                        if (basePos == null) {
                            bench.add(base);
                        } else {
                            board.put(basePos, base);
                        }

                        // Re-add equipment (up to max)
                        for (Equipment eq : allEquipment) {
                            if (!base.addEquipment(eq)) {
                                equipmentStorage.add(eq);
                            }
                        }

                        madeUpgrade = true; // 🚨 합성이 일어났음을 표시
                        totalUpgraded++;

                        // 🚨 연속 합성을 위해: 업그레이드된 유닛을 현재 리스트(units)에 다시 추가하여
                        // while 루프 조건을 다시 검사하게 합니다.
                        // (이 유닛은 다음 do-while 루프에서 새로운 레벨 그룹으로 올바르게 재분류됩니다.)
                        units.add(base);

                        // 플레이어에게 알림
                        if (player != null && player.isOnline()) {
                            String locationMsg = basePos != null ? " §7(보드 유지)" : " §7(벤치)";
                            player.sendMessage("§a§l✦ 자동 합성! §r§e" + base.getName() + " §7→ §6" +
                                    base.getLevel().getDisplay() + " " + base.getName() + locationMsg);
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        }
                    }
                }
            }

            // 3. 합성이 발생했다면, 전체 유닛 목록을 다시 그룹화하여 재검사
        } while (madeUpgrade);

        return totalUpgraded;
    }

    /**
     * 유닛의 보드상 위치 찾기
     */
    private Position findUnitPosition(Unit unit) {
        for (Map.Entry<Position, Unit> entry : board.entrySet()) {
            if (entry.getValue().equals(unit)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 승리 기록
     */
    public void recordWin() {
        winStreak++;
        loseStreak = 0;
        roundsWon++;
    }

    /**
     * 패배 기록
     */
    public void recordLoss() {
        loseStreak++;
        winStreak = 0;
        roundsLost++;
    }

    /**
     * 연승보너스 골드 (max 5)
     */
    public int getWinStreakBonus() {
        return Math.min(winStreak, 5);
    }

    /**
     * 연패 보너스 골드 (max 5)
     * 연패 시에도 골드를 지급하여 약한 플레이어에게 기회 제공
     */
    public int getLoseStreakBonus() {
        return Math.min(loseStreak, 5);
    }

    /**
     * 벤치에 있는 유닛을 판매하여 골드를 얻습니다.
     * 장비는 회수되어 장비 보관함에 저장되며, 반환 정보가 함께 제공됩니다.
     * @return 판매 결과 객체, 유닛이 존재하지 않는다면 null
     */
    public SellResult sellUnit(Unit unit) {
        // 유닛이 벤치에 있는지 확인합니다.
        if (!bench.contains(unit)) {
            // 유닛이 보드에 있는지 확인합니다.
            Position positionToRemove = null;
            for (Map.Entry<Position, Unit> entry : board.entrySet()) {
                if (entry.getValue().equals(unit)) {
                    positionToRemove = entry.getKey();
                    break;
                }
            }

            if (positionToRemove != null) {
                board.remove(positionToRemove);
            } else {
                // 유닛을 찾을 수 없을 때
                return null;
            }
        } else {
            bench.remove(unit);
        }

        // 장비를 장비 보관함(인벤토리)에 보관합니디.(회수)
        List<Equipment> equipment = unit.removeAllEquipment();
        equipmentStorage.addAll(equipment);

        // 유닛 코스트를 계산하여 골드 지급하는 로직
        // 1성 = cost * 1 (유닛 1개)
        // 2성 = cost * 3 (유닛 3개 합성)
        // 3성 = cost * 9 (2성 3개 합성 = 유닛 9개)
        // 공식: cost * (3^(level-1))
        int level = unit.getLevel().getLevel();
        int unitsUsed = (int) Math.pow(3, level - 1);
        int sellPrice = unit.getCost() * unitsUsed;

        addGold(sellPrice);
        return new SellResult(sellPrice, new ArrayList<>(equipment));
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getGameId() {
        return gameId;
    }

    public Player getPlayer() {
        return player;
    }

    public int getGold() {
        return gold;
    }

    public int getExperience() {
        return experience;
    }

    public int getLevel() {
        return level;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.max(0, health);
    }

    public int getWinStreak() {
        return winStreak;
    }

    public int getLoseStreak() {
        return loseStreak;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    public List<Unit> getBench() {
        return new ArrayList<>(bench);
    }

    public Map<Position, Unit> getBoard() {
        return new HashMap<>(board);
    }

    public void clearAllUnits() {
        board.clear();
        bench.clear();
        equipmentStorage.clear();
    }

    public List<Equipment> getEquipmentStorage() {
        return new ArrayList<>(equipmentStorage);
    }

    /**
     * 유닛 판매 결과 데이터
     */
    public static class SellResult {
        private final int goldGained;
        private final List<Equipment> reclaimedEquipment;

        public SellResult(int goldGained, List<Equipment> reclaimedEquipment) {
            this.goldGained = goldGained;
            this.reclaimedEquipment = reclaimedEquipment;
        }

        public int getGoldGained() {
            return goldGained;
        }

        public List<Equipment> getReclaimedEquipment() {
            return reclaimedEquipment;
        }
    }

    /**
     * 저장된 장비 중 Instance ID로 조회
     */
    public Equipment getEquipmentByInstanceId(UUID instanceId) {
        for (Equipment equipment : equipmentStorage) {
            if (equipment.getInstanceId().equals(instanceId)) {
                return equipment;
            }
        }
        return null;
    }

    /**
     * Instance ID로 장비를 제거하면서 반환
     */
    public Equipment removeEquipmentByInstanceId(UUID instanceId) {
        Iterator<Equipment> iterator = equipmentStorage.iterator();
        while (iterator.hasNext()) {
            Equipment equipment = iterator.next();
            if (equipment.getInstanceId().equals(instanceId)) {
                iterator.remove();
                return equipment;
            }
        }
        return null;
    }

    /**
     * 벤치에서 Instance ID로 유닛 검색
     */
    public Unit getBenchUnit(UUID unitInstanceId) {
        for (Unit unit : bench) {
            if (unit.getInstanceId().equals(unitInstanceId)) {
                return unit;
            }
        }
        return null;
    }

    public int getRoundsWon() {
        return roundsWon;
    }

    public int getRoundsLost() {
        return roundsLost;
    }
}
