package com.matochess.game;

import com.matochess.MatoChessPlugin;
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
    public LivingEntity spawnUnit(Unit unit, Position position, boolean isBlueTeam) {
        String worldName = plugin.getConfig().getString("arena.combat-world", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Combat world not found: " + worldName);
            return null;
        }

        // Get spawn area
        String path = isBlueTeam ? "arena.blue-team-area" : "arena.red-team-area";
        double x1 = plugin.getConfig().getDouble(path + ".pos1.x", 0);
        double y = plugin.getConfig().getDouble(path + ".pos1.y", 64);
        double z1 = plugin.getConfig().getDouble(path + ".pos1.z", 0);

        // Calculate spawn location (relative to grid)
        double spawnX = x1 + position.getX();
        double spawnZ = z1 + position.getY();

        Location spawnLoc = new Location(world, spawnX + 0.5, y, spawnZ + 0.5);

        // Spawn entity
        LivingEntity entity = (LivingEntity) world.spawnEntity(spawnLoc, unit.getEntityType());

        // Set health
        entity.setMaxHealth(unit.getHealth());
        entity.setHealth(unit.getHealth());

        // Update health bar
        updateHealthBar(entity, unit);

        // Prevent AI (we control them)
        entity.setAI(false);

        // Store reference
        spawnedEntities.put(unit.getInstanceId(), entity);

        return entity;
    }

    /**
     * 엔티티의 체력바 업데이트
     */
    public void updateHealthBar(LivingEntity entity, Unit unit) {
        double currentHealth = entity.getHealth();
        double maxHealth = entity.getMaxHealth();
        double healthPercentage = (currentHealth / maxHealth) * 100;

        // 체력바 생성 (█ 기호 사용)
        int totalBars = 10;
        int filledBars = (int) Math.ceil((healthPercentage / 100) * totalBars);

        StringBuilder healthBar = new StringBuilder();

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                if (healthPercentage > 50) {
                    healthBar.append("§a");
                } else if (healthPercentage > 25) {
                    healthBar.append("§e");
                } else {
                    healthBar.append("§c");
                }
                healthBar.append("█");
            } else {
                healthBar.append("§7█");
            }
        }

        // 유닛 이름 + 체력 표시
        String displayName = unit.getDisplayName() + " " + healthBar.toString() +
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
