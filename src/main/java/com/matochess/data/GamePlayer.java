package com.matochess.data;

import com.matochess.data.Position;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents a player in an active game
 * Tracks their resources, units, level, and game state
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
    private static final int BENCH_SIZE = 9;
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
     * Get maximum units that can be placed on board based on level
     */
    public int getMaxBoardUnits() {
        return switch (level) {
            case 1 -> 2;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            case 6 -> 6;
            case 7 -> 7;
            case 8 -> 8;
            default -> 2;
        };
    }

    /**
     * Add gold to player
     */
    public void addGold(int amount) {
        this.gold += amount;
    }

    /**
     * Try to spend gold
     * @return true if successful, false if not enough gold
     */
    public boolean spendGold(int amount) {
        if (gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    /**
     * Add experience and check for level up
     * @return true if leveled up, false otherwise
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
     * Take damage
     * @return true if player is still alive, false if eliminated
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
     * Add unit to bench
     * @return true if successful, false if bench is full
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
     * Remove unit from bench
     */
    public boolean removeUnitFromBench(Unit unit) {
        return bench.remove(unit);
    }

    /**
     * Place unit on board
     * @return true if successful, false if position occupied or max units reached
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
     * Alias for placeUnitOnBoard - used by inventory GUI
     */
    public boolean placeUnit(Unit unit, Position position) {
        return placeUnitOnBoard(unit, position);
    }

    /**
     * Remove unit from board
     */
    public Unit removeUnitFromBoard(Position position) {
        return board.remove(position);
    }

    /**
     * Get unit at position
     */
    public Unit getUnitAt(Position position) {
        return board.get(position);
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
     * Check for units that can be combined and auto-upgrade (벤치 + 보드의 모든 유닛 포함)
     * 준비 단계에서만 호출되어야 함
     * @return number of units upgraded
     */
    public int autoUpgradeUnits() {
        Map<String, List<Unit>> unitsByIdAndLevel = new HashMap<>();

        // 벤치의 모든 유닛을 그룹화
        for (Unit unit : bench) {
            String key = unit.getId() + "_" + unit.getLevel().getLevel();
            unitsByIdAndLevel.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
        }

        // 보드의 모든 유닛도 그룹화 (전장에 나가있는 병력 포함)
        for (Unit unit : board.values()) {
            String key = unit.getId() + "_" + unit.getLevel().getLevel();
            unitsByIdAndLevel.computeIfAbsent(key, k -> new ArrayList<>()).add(unit);
        }

        int upgraded = 0;
        for (List<Unit> units : unitsByIdAndLevel.values()) {
            while (units.size() >= 3) {
                // Remove 3 units and create 1 upgraded unit
                Unit base = units.remove(0);
                Unit second = units.remove(0);
                Unit third = units.remove(0);

                // Transfer equipment from combined units
                List<Equipment> allEquipment = new ArrayList<>();
                allEquipment.addAll(base.removeAllEquipment());
                allEquipment.addAll(second.removeAllEquipment());
                allEquipment.addAll(third.removeAllEquipment());

                // 벤치와 보드에서 제거
                bench.remove(second);
                bench.remove(third);

                // 보드에서도 제거
                Position secondPos = findUnitPosition(second);
                Position thirdPos = findUnitPosition(third);
                if (secondPos != null) board.remove(secondPos);
                if (thirdPos != null) board.remove(thirdPos);

                // Upgrade base unit
                if (base.upgrade()) {
                    // Re-add equipment (up to max)
                    for (Equipment eq : allEquipment) {
                        if (!base.addEquipment(eq)) {
                            equipmentStorage.add(eq);
                        }
                    }
                    upgraded++;

                    // 플레이어에게 알림
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§a§l✦ 자동 합성! §r§e" + base.getName() + " §7→ §6" +
                                         base.getLevel().getDisplay() + " " + base.getName());
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    }
                }
            }
        }

        return upgraded;
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
     * Record a win
     */
    public void recordWin() {
        winStreak++;
        loseStreak = 0;
        roundsWon++;
    }

    /**
     * Record a loss
     */
    public void recordLoss() {
        loseStreak++;
        winStreak = 0;
        roundsLost++;
    }

    /**
     * Get win streak bonus gold (max 5)
     */
    public int getWinStreakBonus() {
        return Math.min(winStreak, 5);
    }

    /**
     * Get lose streak bonus gold (max 5)
     * 연패 시에도 골드를 지급하여 약한 플레이어에게 기회 제공
     */
    public int getLoseStreakBonus() {
        return Math.min(loseStreak, 5);
    }

    /**
     * Sell a unit from bench and get gold refund
     * Equipment is returned to storage
     * @return gold refunded, or 0 if unit not found
     */
    public int sellUnit(Unit unit) {
        // Check if unit is on bench
        if (!bench.contains(unit)) {
            // Check if unit is on board
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
                return 0; // Unit not found
            }
        } else {
            bench.remove(unit);
        }

        // Return equipment to storage
        List<Equipment> equipment = unit.removeAllEquipment();
        equipmentStorage.addAll(equipment);

        // Calculate sell price (full cost for tier 1, increases with level)
        int sellPrice = unit.getCost();
        if (unit.getLevel() != UnitLevel.ONE) {
            // Higher level units sell for more
            sellPrice = unit.getCost() * (unit.getLevel().getLevel());
        }

        addGold(sellPrice);
        return sellPrice;
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

    public List<Equipment> getEquipmentStorage() {
        return new ArrayList<>(equipmentStorage);
    }

    public int getRoundsWon() {
        return roundsWon;
    }

    public int getRoundsLost() {
        return roundsLost;
    }

    /**
     * Represents a position on the game board
     */
    public static class BoardPosition {
        private final int x;
        private final int y;

        public BoardPosition(int x, int y) {
            if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT) {
                throw new IllegalArgumentException("Invalid board position: " + x + ", " + y);
            }
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoardPosition that = (BoardPosition) o;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        public static boolean isValid(int x, int y) {
            return x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT;
        }
    }
}
