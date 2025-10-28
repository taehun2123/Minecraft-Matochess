package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.data.Arena;
import com.matochess.data.GamePlayer;
import com.matochess.data.Position;
import com.matochess.data.Unit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages unit placement on the battle board
 */
public class BoardManager {

    private final MatoChessPlugin plugin;

    // Track spawned entities
    private final Map<UUID, LivingEntity> spawnedEntities;

    public BoardManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.spawnedEntities = new HashMap<>();
    }

    /**
     * Spawn a single unit at a specific position
     */
    public LivingEntity spawnUnit(Unit unit, Position position, boolean isBlueTeam, Arena arena) {
        String worldName = arena.getPos1().getWorld().getName(); // 🚨 Arena에서 월드 정보 가져오기
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Combat world not found: " + worldName);
            return null;
        }

// Get spawn area: 이제 config 대신 Arena 좌표를 기준으로 상대적 위치를 계산해야 합니다.
        // pos1, pos2는 아레나의 경계 블록이므로, 그중 한쪽을 기준으로 삼아야 합니다.
        // 여기서는 Blue Team (player1)의 아레나를 사용하며,
        // Blue Team은 pos1 기준으로, Red Team(몬스터/player2)은 pos2 기준으로 배치된다고 가정합니다.

        // 아레나 경계 계산
        double minX = Math.min(arena.getPos1().getX(), arena.getPos2().getX());
        double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ());
        double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ());
        double spawnY = Math.min(arena.getPos1().getY(), arena.getPos2().getY());

        // 보드판 레이아웃 (각 칸 = 4x4 블록):
        // - X축: 8칸 × 4블록 = 32블록
        // - Z축: Blue Team 3칸 × 4블록 = 12블록, Red Team 3칸 × 4블록 = 12블록
        // Blue Team은 Z축 앞쪽 (minZ), Red Team은 Z축 뒤쪽

        // Blue Team은 앞쪽 12블록 영역 (minZ ~ minZ+12)
        // Red Team은 뒤쪽 12블록 영역 (maxZ-12 ~ maxZ)
        double zOffset = isBlueTeam ? minZ : (maxZ - 12);

        // GUI의 각 칸(1칸)을 4x4 블록으로 매핑
        // position.getX()는 GUI X좌표 (0~7) → 실제 월드 X축
        // position.getY()는 GUI Y좌표 (0~2) → 실제 월드 Z축
        // 유닛은 4x4 영역의 중앙(+2.0블록)에 스폰
        double spawnX = minX + (position.getX() * 4) + 2.0;
        double spawnZ = zOffset + (position.getY() * 4) + 2.0;

        Location spawnLoc = new Location(world, spawnX, spawnY + 1, spawnZ);

        // Spawn entity
        LivingEntity entity = (LivingEntity) world.spawnEntity(spawnLoc, unit.getEntityType());

        // Set health
        entity.setMaxHealth(unit.getHealth());
        entity.setHealth(unit.getHealth());

        // Update health bar (팀별 색상으로 구분, 발광 효과 없음)
        updateHealthBar(entity, unit, isBlueTeam);

        // AI 활성화 - 자연스러운 움직임과 공격을 위해
        entity.setAI(true);

        // Follow Range 증가 (타겟을 먼 거리에서도 따라가도록)
        if (entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
            entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(64.0); // 64블록까지 따라감
        }

        // 중력 적용 (땅에 착지)
        entity.setGravity(true);

        // 몹이 자연스럽게 소멸되지 않도록 설정
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        // Store reference
        spawnedEntities.put(unit.getInstanceId(), entity);

        return entity;
    }

    /**
     * 엔티티의 체력바 업데이트 (오버로드 - 호환성 유지)
     */
    public void updateHealthBar(LivingEntity entity, Unit unit) {
        updateHealthBar(entity, unit, false);
    }

    /**
     * 엔티티의 체력바 업데이트 (팀별 색상 구분 - 체력에 관계없이 색상 고정)
     */
    public void updateHealthBar(LivingEntity entity, Unit unit, boolean isBlueTeam) {
        double currentHealth = entity.getHealth();
        double maxHealth = entity.getMaxHealth();
        double healthPercentage = (currentHealth / maxHealth) * 100;

        // 체력바 생성 (█ 기호 사용)
        int totalBars = 10;
        int filledBars = (int) Math.ceil((healthPercentage / 100) * totalBars);

        StringBuilder healthBar = new StringBuilder();

        // 팀별로 색상 고정 (체력에 따라 변하지 않음)
        String barColor = isBlueTeam ? "§b" : "§c"; // 파란색 vs 빨간색

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                healthBar.append(barColor).append("█");
            } else {
                healthBar.append("§7█");
            }
        }

        healthBar.append("§r");

        // 유닛 이름 + 체력 표시 (팀별 색상)
        String nameColor = isBlueTeam ? "§b" : "§c";
        String displayName = nameColor + unit.getDisplayName() + " " + healthBar.toString() +
            " §7[§f" + (int)currentHealth + "§7/§f" + (int)maxHealth + "§7]";

        entity.setCustomName(displayName);
        entity.setCustomNameVisible(true);
    }

    /**
     * Spawn all units for a player on the board
     */
    public void spawnUnits(GamePlayer gamePlayer, boolean isBlueTeam) {
        String worldName = plugin.getConfig().getString("arena.combat-world", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Combat world not found: " + worldName);
            return;
        }

        // Get spawn area
        String path = isBlueTeam ? "arena.blue-team-area" : "arena.red-team-area";
        double x1 = plugin.getConfig().getDouble(path + ".pos1.x", 0);
        double y = plugin.getConfig().getDouble(path + ".pos1.y", 64);
        double z1 = plugin.getConfig().getDouble(path + ".pos1.z", 0);

        // Spawn units from board
        gamePlayer.getBoard().forEach((position, unit) -> {
            // Calculate spawn location (relative to grid)
            double spawnX = x1 + position.getX();
            double spawnZ = z1 + position.getY();

            Location spawnLoc = new Location(world, spawnX + 0.5, y, spawnZ + 0.5);

            // Spawn entity
            LivingEntity entity = (LivingEntity) world.spawnEntity(spawnLoc, unit.getEntityType());
            entity.setCustomName(unit.getDisplayName());
            entity.setCustomNameVisible(true);

            // Set health
            entity.setMaxHealth(unit.getHealth());
            entity.setHealth(unit.getHealth());

            // Store reference
            spawnedEntities.put(unit.getInstanceId(), entity);
        });
    }

    /**
     * Clear all spawned units
     */
    public void clearAllUnits() {
        spawnedEntities.values().forEach(entity -> {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        });
        spawnedEntities.clear();
    }

    /**
     * Get spawned entity for a unit
     */
    public LivingEntity getEntity(Unit unit) {
        return spawnedEntities.get(unit.getInstanceId());
    }

    /**
     * Remove entity
     */
    public void removeEntity(Unit unit) {
        LivingEntity entity = spawnedEntities.remove(unit.getInstanceId());
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }
}
