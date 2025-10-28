package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.data.Arena;
import com.matochess.data.GamePlayer;
import com.matochess.data.Position;
import com.matochess.data.Unit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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

        double xBase = isBlueTeam ? arena.getPos1().getX() : arena.getPos2().getX(); // 🚨 Arena pos 사용
        double yBase = arena.getPos1().getY(); // Y 좌표는 동일하다고 가정
        double zBase = isBlueTeam ? arena.getPos1().getZ() : arena.getPos2().getZ(); // 🚨 Arena pos 사용

// Calculate spawn location (relative to grid)
        // Blue Team은 (xBase, zBase)를 시작점으로 사용하고, Red Team은 반대편을 시작점으로 사용해야 합니다.
        // 현재는 간단하게 아레나의 XZ 경계 중 하나를 사용합니다. (좌표 시스템에 따라 상세 조정 필요)

        // 🚨 P1(Blue)은 pos1을, P2(Red/Monster)는 pos2를 기준으로 배치된다고 가정합니다.

        // 유닛 배치판은 8x8 (혹은 8x3) 등 정해진 크기가 있으므로, 아레나 pos1을 기준으로 삼아 계산합니다.

        // Red 팀은 Blue 팀과 반대 방향(예: Z 축)에 배치되어야 합니다.
        // 아레나 pos1, pos2의 X, Z 좌표를 사용하여 유닛이 소환될 영역을 계산해야 합니다.

        // **간단화된 로직 (수평 Z축이 전투 방향이라고 가정):**
        // Blue Team은 Z1 기준으로, Red Team은 Z2 기준으로 소환 (Z2가 더 큰 Z 좌표라고 가정)

        double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ());
        double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ());
        double spawnY = arena.getPos1().getY(); // Y는 고정

        // Blue Team (Team 1)은 낮은 Z축에서 소환 (예: Z = Z1)
        double zOffset1 = minZ;

        // Red Team (Team 2)은 높은 Z축에서 소환 (예: Z = Z2 - 3)
        // 8x3 보드판이라고 가정하고, 몬스터가 가장자리에서 3칸 떨어진 위치에 소환되도록 합니다.
        double zOffset2 = maxZ;

        double spawnX = xBase + position.getX();
        double spawnZ = (isBlueTeam ? zOffset1 : zOffset2) + position.getY(); // position.getY()는 Z축 offset

        Location spawnLoc = new Location(world, spawnX + 0.5, spawnY, spawnZ + 0.5);

        // Spawn entity
        LivingEntity entity = (LivingEntity) world.spawnEntity(spawnLoc, unit.getEntityType());

        // Set health
        entity.setMaxHealth(unit.getHealth());
        entity.setHealth(unit.getHealth());

        // 내 유닛(Blue Team)에게 발광 효과 적용
        if (isBlueTeam) {
            entity.setGlowing(true);
            // 발광 색상은 팀(스코어보드 팀)으로 설정할 수 있음
            // Note: 발광 색상 변경은 스코어보드 팀이 필요함 (나중에 추가 가능)
        }

        // Update health bar (Blue Team은 다른 색상)
        updateHealthBar(entity, unit, isBlueTeam);

        // Prevent AI (we control them)
        entity.setAI(false);

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
     * 엔티티의 체력바 업데이트 (팀별 색상 구분)
     */
    public void updateHealthBar(LivingEntity entity, Unit unit, boolean isBlueTeam) {
        double currentHealth = entity.getHealth();
        double maxHealth = entity.getMaxHealth();
        double healthPercentage = (currentHealth / maxHealth) * 100;

        // 체력바 생성 (█ 기호 사용)
        int totalBars = 10;
        int filledBars = (int) Math.ceil((healthPercentage / 100) * totalBars);

        StringBuilder healthBar = new StringBuilder();

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                // Blue Team (내 유닛)은 파란색 계열 체력바
                if (isBlueTeam) {
                    if (healthPercentage > 50) {
                        healthBar.append("§b"); // Aqua (밝은 파란색)
                    } else if (healthPercentage > 25) {
                        healthBar.append("§9"); // Blue (파란색)
                    } else {
                        healthBar.append("§1"); // Dark Blue (어두운 파란색)
                    }
                }
                // Red Team (적 유닛)은 빨간색/노란색 계열 체력바
                else {
                    if (healthPercentage > 50) {
                        healthBar.append("§a"); // Green
                    } else if (healthPercentage > 25) {
                        healthBar.append("§e"); // Yellow
                    } else {
                        healthBar.append("§c"); // Red
                    }
                }
                healthBar.append("█");
            } else {
                healthBar.append("§7█");
            }
        }

        // 유닛 이름 + 체력 표시 (Blue Team은 이름도 파란색)
        String nameColor = isBlueTeam ? "§b" : "§f";
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
