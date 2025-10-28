package com.matochess.data;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents equipment (장비) that can be equipped on units
 * Equipment provides stat bonuses to units
 */
public class Equipment {

    private final String id;
    private final String name;
    private final Material material;
    private final Map<StatType, Double> statBonuses;
    private final UUID instanceId;

    public Equipment(String id, String name, Material material) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.statBonuses = new HashMap<>();
        this.instanceId = UUID.randomUUID();
    }

    /**
     * Add a stat bonus to this equipment
     */
    public void addStatBonus(StatType statType, double value) {
        statBonuses.put(statType, statBonuses.getOrDefault(statType, 0.0) + value);
    }

    /**
     * Get the bonus for a specific stat type
     */
    public double getStatBonus(StatType statType) {
        return statBonuses.getOrDefault(statType, 0.0);
    }

    /**
     * Create a copy of this equipment
     */
    public Equipment copy() {
        Equipment copy = new Equipment(id, name, material);
        copy.statBonuses.putAll(this.statBonuses);
        return copy;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public Map<StatType, Double> getStatBonuses() {
        return new HashMap<>(statBonuses);
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * Enum for different stat types that equipment can modify
     */
    public enum StatType {
        HEALTH("체력"),
        ATTACK_DAMAGE("공격력"),
        ATTACK_SPEED("공격 속도"),
        ARMOR("방어력"),
        MAGIC_RESIST("마법 저항력"),
        CRITICAL_CHANCE("치명타 확률"),
        CRITICAL_DAMAGE("치명타 피해"),
        LIFE_STEAL("생명력 흡수"),
        MOVEMENT_SPEED("이동 속도");

        private final String displayName;

        StatType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
