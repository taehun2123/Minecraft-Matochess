package com.matochess.core;

import com.matochess.data.Unit;
import com.matochess.data.UnitTier;
import com.matochess.data.UnitTrait;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all unit definitions
 * Manages unit templates and provides unit creation
 */
public class UnitRegistry {

    private final Map<String, UnitTemplate> templates;

    public UnitRegistry() {
        this.templates = new HashMap<>();
    }

    /**
     * Register all default units
     */
    public void registerDefaults() {
        // 1성 유닛 - 저비용, 초반용
        register("zombie_warrior", "좀비 전사", UnitTier.ONE_STAR,
                EntityType.ZOMBIE, Material.ROTTEN_FLESH,
                100, 15, 0.8, 10, 5, 1.5,
                UnitTrait.UNDEAD, UnitTrait.WARRIOR);

        register("skeleton_archer", "스켈레톤 궁수", UnitTier.ONE_STAR,
                EntityType.SKELETON, Material.BONE,
                80, 20, 1.0, 5, 3, 5.0,
                UnitTrait.UNDEAD, UnitTrait.RANGER);

        register("cave_spider", "동굴 거미", UnitTier.ONE_STAR,
                EntityType.CAVE_SPIDER, Material.SPIDER_EYE,
                70, 18, 1.2, 3, 3, 2.0,
                UnitTrait.ASSASSIN);

        // 2성 유닛 - 중간 티어
        register("spider_assassin", "거미 암살자", UnitTier.TWO_STAR,
                EntityType.SPIDER, Material.STRING,
                120, 25, 1.2, 8, 6, 2.0,
                UnitTrait.ASSASSIN);

        register("creeper_bomber", "크리퍼 폭파병", UnitTier.TWO_STAR,
                EntityType.CREEPER, Material.GUNPOWDER,
                100, 40, 0.5, 5, 5, 3.0,
                UnitTrait.MAGE);

        register("drowned_mage", "드라운드 마법사", UnitTier.TWO_STAR,
                EntityType.DROWNED, Material.TRIDENT,
                110, 30, 0.9, 7, 8, 6.0,
                UnitTrait.OCEAN, UnitTrait.MAGE);

        // 3성 유닛 - 고급 유닛
        register("iron_golem_tank", "철 골렘 탱커", UnitTier.THREE_STAR,
                EntityType.IRON_GOLEM, Material.IRON_BLOCK,
                250, 35, 0.6, 25, 15, 1.5,
                UnitTrait.CONSTRUCT, UnitTrait.TANK);

        register("blaze_mage", "블레이즈 화염술사", UnitTier.THREE_STAR,
                EntityType.BLAZE, Material.BLAZE_ROD,
                150, 50, 1.0, 10, 12, 7.0,
                UnitTrait.NETHER, UnitTrait.MAGE);

        register("witch_healer", "마녀 힐러", UnitTier.THREE_STAR,
                EntityType.WITCH, Material.GLASS_BOTTLE,
                140, 25, 0.8, 8, 20, 5.0,
                UnitTrait.SUMMONER);

        // 4성 유닛 - 희귀 유닛
        register("piglin_brute", "피글린 브루트", UnitTier.FOUR_STAR,
                EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE,
                300, 60, 0.9, 20, 10, 2.0,
                UnitTrait.NETHER, UnitTrait.WARRIOR);

        register("enderman_teleporter", "엔더맨 순간이동자", UnitTier.FOUR_STAR,
                EntityType.ENDERMAN, Material.ENDER_PEARL,
                180, 55, 1.3, 12, 15, 3.0,
                UnitTrait.END, UnitTrait.ASSASSIN);

        register("ravager_charger", "파괴수 돌격병", UnitTier.FOUR_STAR,
                EntityType.RAVAGER, Material.SADDLE,
                400, 45, 0.7, 30, 8, 1.5,
                UnitTrait.TANK);

        // 5성 유닛 - 전설 유닛
        register("wither_skeleton", "위더 스켈레톤", UnitTier.FIVE_STAR,
                EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL,
                350, 80, 1.1, 25, 20, 2.5,
                UnitTrait.UNDEAD, UnitTrait.WARRIOR);

        register("evoker_summoner", "변환술사 소환사", UnitTier.FIVE_STAR,
                EntityType.EVOKER, Material.TOTEM_OF_UNDYING,
                280, 70, 0.9, 15, 30, 6.0,
                UnitTrait.SUMMONER, UnitTrait.MAGE);

        register("elder_guardian", "엘더 가디언", UnitTier.FIVE_STAR,
                EntityType.ELDER_GUARDIAN, Material.PRISMARINE_CRYSTALS,
                500, 60, 0.8, 35, 25, 4.0,
                UnitTrait.OCEAN, UnitTrait.TANK);
    }

    /**
     * Register a unit template
     */
    private void register(String id, String name, UnitTier tier,
                         EntityType entityType, Material iconMaterial,
                         double hp, double atk, double atkSpd,
                         double armor, double mr, double range,
                         UnitTrait... traits) {
        UnitTemplate template = new UnitTemplate(id, name, tier,
                entityType, iconMaterial,
                hp, atk, atkSpd, armor, mr, range, traits);
        templates.put(id, template);
    }

    /**
     * Create a unit from template
     */
    public Unit createUnit(String id) {
        UnitTemplate template = templates.get(id);
        if (template == null) {
            return null;
        }

        Unit unit = new Unit(template.id, template.name, template.tier,
                           template.entityType, template.iconMaterial);
        unit.setBaseStats(template.hp, template.atk, template.atkSpd,
                         template.armor, template.mr, template.range);

        // Add traits
        for (UnitTrait trait : template.traits) {
            unit.addTrait(trait);
        }

        return unit;
    }

    /**
     * Get all unit IDs of a specific tier
     */
    public List<String> getUnitIdsByTier(UnitTier tier) {
        return templates.values().stream()
                .filter(t -> t.tier == tier)
                .map(t -> t.id)
                .collect(Collectors.toList());
    }

    /**
     * Get random unit of a specific tier
     */
    public Unit getRandomUnitOfTier(UnitTier tier) {
        List<String> unitIds = getUnitIdsByTier(tier);
        if (unitIds.isEmpty()) {
            return null;
        }

        String randomId = unitIds.get(new Random().nextInt(unitIds.size()));
        return createUnit(randomId);
    }

    /**
     * Check if unit exists
     */
    public boolean hasUnit(String id) {
        return templates.containsKey(id);
    }

    /**
     * Get all registered unit IDs
     */
    public Set<String> getAllUnitIds() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Unit template class
     */
    private static class UnitTemplate {
        final String id;
        final String name;
        final UnitTier tier;
        final EntityType entityType;
        final Material iconMaterial;
        final double hp, atk, atkSpd, armor, mr, range;
        final UnitTrait[] traits;

        UnitTemplate(String id, String name, UnitTier tier,
                    EntityType entityType, Material iconMaterial,
                    double hp, double atk, double atkSpd,
                    double armor, double mr, double range,
                    UnitTrait... traits) {
            this.id = id;
            this.name = name;
            this.tier = tier;
            this.entityType = entityType;
            this.iconMaterial = iconMaterial;
            this.hp = hp;
            this.atk = atk;
            this.atkSpd = atkSpd;
            this.armor = armor;
            this.mr = mr;
            this.range = range;
            this.traits = traits;
        }
    }
}
