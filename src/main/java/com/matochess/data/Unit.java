package com.matochess.data;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a game unit (기물)
 * Units can be purchased, upgraded, and equipped with items
 */
public class Unit {

    private final String id;
    private final String name;
    private final UnitTier tier;
    private UnitLevel level;

    // Base stats (before multipliers)
    private double baseHealth;
    private double baseAttackDamage;
    private double baseAttackSpeed;
    private double baseArmor;
    private double baseMagicResist;
    private double baseRange;

    // Attack and skill system
    private AttackType attackType; // 물리/마법 공격 타입
    private UnitSkill skill; // 유닛의 스킬
    private double mana = 0.0; // 현재 마나
    private double manaPerAttack = 10.0; // 공격당 마나 획득량
    private static final double MAX_MANA = 100.0; // 최대 마나 (스킬 발동)

    // Additional combat stats (not multiplied by level)
    private double criticalChance = 0.0;  // Percentage (0-100)
    private double criticalDamage = 50.0; // Percentage multiplier (default 50% extra)
    private double lifeSteal = 0.0;       // Percentage (0-100)

    // Equipment
    private final List<Equipment> equipment;
    private static final int MAX_EQUIPMENT = 3;

    // Traits (특성)
    private final List<UnitTrait> traits;

    // Entity representation
    private final EntityType entityType;
    private final Material iconMaterial;

    // Unique instance ID for tracking
    private final UUID instanceId;

    public Unit(String id, String name, UnitTier tier, EntityType entityType, Material iconMaterial) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.level = UnitLevel.LEVEL_1;
        this.equipment = new ArrayList<>();
        this.traits = new ArrayList<>();
        this.entityType = entityType;
        this.iconMaterial = iconMaterial;
        this.instanceId = UUID.randomUUID();

        // Default stats will be set by unit definitions
        this.baseHealth = 100.0;
        this.baseAttackDamage = 10.0;
        this.baseAttackSpeed = 1.0;
        this.baseArmor = 5.0;
        this.baseMagicResist = 5.0;
        this.baseRange = 2.0;

        // Default attack type (will be set by unit definitions)
        this.attackType = AttackType.PHYSICAL;
        this.skill = null;
    }

    /**
     * Create a copy of this unit (for purchasing from shop)
     */
    public Unit copy() {
        Unit copy = new Unit(id, name, tier, entityType, iconMaterial);
        copy.setBaseStats(baseHealth, baseAttackDamage, baseAttackSpeed,
                         baseArmor, baseMagicResist, baseRange);
        return copy;
    }

    /**
     * Set all base stats at once
     */
    public void setBaseStats(double health, double attackDamage, double attackSpeed,
                            double armor, double magicResist, double range) {
        this.baseHealth = health;
        this.baseAttackDamage = attackDamage;
        this.baseAttackSpeed = attackSpeed;
        this.baseArmor = armor;
        this.baseMagicResist = magicResist;
        this.baseRange = range;
    }

    // Individual stat setters (used for synergy bonuses and combat modifications)
    public void setAttackDamage(double attackDamage) {
        this.baseAttackDamage = attackDamage / level.getStatMultiplier();
    }

    public void setArmor(double armor) {
        this.baseArmor = armor / level.getStatMultiplier();
    }

    public void setMagicResist(double magicResist) {
        this.baseMagicResist = magicResist / level.getStatMultiplier();
    }

    public void setAttackSpeed(double attackSpeed) {
        this.baseAttackSpeed = attackSpeed / level.getStatMultiplier();
    }

    public void setCriticalChance(double criticalChance) {
        // Critical stats are stored directly, not multiplied by level
        this.criticalChance = criticalChance;
    }

    public void setCriticalDamage(double criticalDamage) {
        this.criticalDamage = criticalDamage;
    }

    public void setLifeSteal(double lifeSteal) {
        this.lifeSteal = lifeSteal;
    }

    /**
     * Get actual health after level multiplier and equipment bonuses
     */
    public double getHealth() {
        double total = baseHealth * level.getStatMultiplier();
        return total + getEquipmentBonus(Equipment.StatType.HEALTH);
    }

    /**
     * Get actual attack damage after level multiplier and equipment bonuses
     */
    public double getAttackDamage() {
        double total = baseAttackDamage * level.getStatMultiplier();
        return total + getEquipmentBonus(Equipment.StatType.ATTACK_DAMAGE);
    }

    /**
     * Get actual attack speed after level multiplier and equipment bonuses
     */
    public double getAttackSpeed() {
        double total = baseAttackSpeed * level.getStatMultiplier();
        return total + getEquipmentBonus(Equipment.StatType.ATTACK_SPEED);
    }

    /**
     * Get actual armor after level multiplier and equipment bonuses
     */
    public double getArmor() {
        double total = baseArmor * level.getStatMultiplier();
        return total + getEquipmentBonus(Equipment.StatType.ARMOR);
    }

    /**
     * Get actual magic resist after level multiplier and equipment bonuses
     */
    public double getMagicResist() {
        double total = baseMagicResist * level.getStatMultiplier();
        return total + getEquipmentBonus(Equipment.StatType.MAGIC_RESIST);
    }

    /**
     * Get critical chance percentage with equipment bonuses
     */
    public double getCriticalChance() {
        return criticalChance + getEquipmentBonus(Equipment.StatType.CRITICAL_CHANCE);
    }

    /**
     * Get critical damage multiplier percentage with equipment bonuses
     */
    public double getCriticalDamage() {
        return criticalDamage + getEquipmentBonus(Equipment.StatType.CRITICAL_DAMAGE);
    }

    /**
     * Get life steal percentage with equipment bonuses
     */
    public double getLifeSteal() {
        return lifeSteal + getEquipmentBonus(Equipment.StatType.LIFE_STEAL);
    }

    /**
     * Calculate total bonus from all equipped items for a specific stat type
     */
    private double getEquipmentBonus(Equipment.StatType statType) {
        double bonus = 0.0;
        for (Equipment eq : equipment) {
            bonus += eq.getStatBonus(statType);
        }
        return bonus;
    }

    /**
     * Add equipment to this unit
     * @return true if successfully added, false if equipment slots are full
     */
    public boolean addEquipment(Equipment eq) {
        if (equipment.size() >= MAX_EQUIPMENT) {
            return false;
        }
        equipment.add(eq);
        return true;
    }

    /**
     * Remove equipment from this unit
     */
    public boolean removeEquipment(Equipment eq) {
        return equipment.remove(eq);
    }

    /**
     * Remove all equipment and return them
     */
    public List<Equipment> removeAllEquipment() {
        List<Equipment> removed = new ArrayList<>(equipment);
        equipment.clear();
        return removed;
    }

    /**
     * Upgrade this unit to the next level
     * @return true if upgraded, false if already max level
     */
    public boolean upgrade() {
        UnitLevel nextLevel = level.next();
        if (nextLevel == null) {
            return false;
        }
        this.level = nextLevel;
        return true;
    }

    /**
     * Check if this unit can be combined with another
     * (same id and same level)
     */
    public boolean canCombineWith(Unit other) {
        return this.id.equals(other.id) && this.level == other.level;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UnitTier getTier() {
        return tier;
    }

    public UnitLevel getLevel() {
        return level;
    }

    public List<Equipment> getEquipment() {
        return new ArrayList<>(equipment);
    }

    public List<UnitTrait> getTraits() {
        return new ArrayList<>(traits);
    }

    /**
     * Add a trait to this unit
     */
    public void addTrait(UnitTrait trait) {
        if (!traits.contains(trait)) {
            traits.add(trait);
        }
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseAttackDamage() {
        return baseAttackDamage;
    }

    public double getBaseAttackSpeed() {
        return baseAttackSpeed;
    }

    public double getBaseArmor() {
        return baseArmor;
    }

    public double getBaseMagicResist() {
        return baseMagicResist;
    }

    public double getBaseRange() {
        return baseRange;
    }

    /**
     * Get the gold cost of this unit based on tier
     */
    public int getCost() {
        return tier.getCost();
    }

    /**
     * Get display name with tier and level
     */
    public String getDisplayName() {
        return tier.getColor() + name + " " + level.getDisplay() + " " + tier.getDisplay();
    }

    /**
     * 마나 시스템 메소드
     */

    // 공격 시 마나 증가
    public void addMana(double amount) {
        this.mana = Math.min(this.mana + amount, MAX_MANA);
    }

    // 마나 초기화 (스킬 사용 후)
    public void resetMana() {
        this.mana = 0.0;
    }

    // 스킬 사용 가능 여부 확인
    public boolean canCastSkill() {
        return this.mana >= MAX_MANA && this.skill != null;
    }

    // 마나 획득량 설정
    public void setManaPerAttack(double manaPerAttack) {
        this.manaPerAttack = manaPerAttack;
    }

    // 공격 타입 설정
    public void setAttackType(AttackType attackType) {
        this.attackType = attackType;
    }

    // 스킬 설정
    public void setSkill(UnitSkill skill) {
        this.skill = skill;
    }

    // Getters for new fields
    public AttackType getAttackType() {
        return attackType;
    }

    public UnitSkill getSkill() {
        return skill;
    }

    public double getMana() {
        return mana;
    }

    public double getManaPerAttack() {
        return manaPerAttack;
    }

    public static double getMaxMana() {
        return MAX_MANA;
    }

    /**
     * Create a deep copy of this unit
     * Used for PVE monsters and shop generation
     */
    public Unit clone() {
        Unit copy = new Unit(
            this.id,
            this.name,
            this.tier,
            this.entityType,
            this.iconMaterial
        );

        // Copy base stats
        copy.setBaseStats(
            this.baseHealth,
            this.baseAttackDamage,
            this.baseAttackSpeed,
            this.baseArmor,
            this.baseMagicResist,
            this.baseRange
        );

        // Copy level
        copy.level = this.level;

        // Copy combat stats
        copy.criticalChance = this.criticalChance;
        copy.criticalDamage = this.criticalDamage;
        copy.lifeSteal = this.lifeSteal;

        // Copy attack type and skill system
        copy.attackType = this.attackType;
        copy.skill = this.skill;
        copy.manaPerAttack = this.manaPerAttack;
        copy.mana = 0.0; // Reset mana for new copy

        // Copy traits
        for (UnitTrait trait : this.traits) {
            copy.addTrait(trait);
        }

        // Copy equipment (use Equipment.copy() method for proper duplication)
        for (Equipment eq : this.equipment) {
            copy.equipment.add(eq.copy());
        }

        return copy;
    }
}
