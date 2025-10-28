package com.matochess.core;

import com.matochess.data.Equipment;
import com.matochess.data.Equipment.StatType;
import org.bukkit.Material;

import java.util.*;

/**
 * Registry for all equipment definitions
 */
public class EquipmentRegistry {

    private final Map<String, EquipmentTemplate> templates;

    public EquipmentRegistry() {
        this.templates = new HashMap<>();
    }

    /**
     * Register all default equipment
     */
    public void registerDefaults() {
        // 기본 무기
        register("iron_sword", "철검", Material.IRON_SWORD)
            .addStat(StatType.ATTACK_DAMAGE, 15.0);

        register("diamond_sword", "다이아 검", Material.DIAMOND_SWORD)
            .addStat(StatType.ATTACK_DAMAGE, 30.0);

        register("bow", "활", Material.BOW)
            .addStat(StatType.ATTACK_DAMAGE, 20.0)
            .addStat(StatType.ATTACK_SPEED, 0.3);

        // 방어구
        register("iron_chestplate", "철 갑옷", Material.IRON_CHESTPLATE)
            .addStat(StatType.ARMOR, 30.0);

        register("diamond_chestplate", "다이아 갑옷", Material.DIAMOND_CHESTPLATE)
            .addStat(StatType.ARMOR, 50.0);

        register("shield", "방패", Material.SHIELD)
            .addStat(StatType.ARMOR, 20.0)
            .addStat(StatType.HEALTH, 50.0);

        // 특수 아이템
        register("golden_apple", "황금 사과", Material.GOLDEN_APPLE)
            .addStat(StatType.HEALTH, 100.0)
            .addStat(StatType.LIFE_STEAL, 10.0);

        register("enchanted_book", "마법책", Material.ENCHANTED_BOOK)
            .addStat(StatType.ATTACK_DAMAGE, 25.0)
            .addStat(StatType.MAGIC_RESIST, 30.0);

        register("netherite_ingot", "네더라이트 주괴", Material.NETHERITE_INGOT)
            .addStat(StatType.ARMOR, 40.0)
            .addStat(StatType.MAGIC_RESIST, 40.0);

        register("blaze_rod", "블레이즈 막대", Material.BLAZE_ROD)
            .addStat(StatType.ATTACK_DAMAGE, 35.0)
            .addStat(StatType.CRITICAL_CHANCE, 15.0);

        register("totem_of_undying", "불사의 토템", Material.TOTEM_OF_UNDYING)
            .addStat(StatType.HEALTH, 150.0)
            .addStat(StatType.ARMOR, 25.0)
            .addStat(StatType.MAGIC_RESIST, 25.0);

        register("ender_pearl", "엔더 진주", Material.ENDER_PEARL)
            .addStat(StatType.ATTACK_SPEED, 0.5)
            .addStat(StatType.CRITICAL_CHANCE, 20.0);

        register("dragon_egg", "드래곤 알", Material.DRAGON_EGG)
            .addStat(StatType.HEALTH, 200.0)
            .addStat(StatType.ATTACK_DAMAGE, 50.0)
            .addStat(StatType.ARMOR, 30.0);
    }

    /**
     * Register equipment template builder
     */
    private EquipmentBuilder register(String id, String name, Material material) {
        EquipmentTemplate template = new EquipmentTemplate(id, name, material);
        templates.put(id, template);
        return new EquipmentBuilder(template);
    }

    /**
     * Create equipment from template
     */
    public Equipment createEquipment(String id) {
        EquipmentTemplate template = templates.get(id);
        if (template == null) {
            return null;
        }

        Equipment equipment = new Equipment(template.id, template.name, template.material);
        template.stats.forEach(equipment::addStatBonus);
        return equipment;
    }

    /**
     * Get random equipment
     */
    public Equipment getRandomEquipment() {
        if (templates.isEmpty()) {
            return null;
        }

        List<String> ids = new ArrayList<>(templates.keySet());
        String randomId = ids.get(new Random().nextInt(ids.size()));
        return createEquipment(randomId);
    }

    /**
     * Get all equipment IDs
     */
    public Set<String> getAllEquipmentIds() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Equipment template
     */
    private static class EquipmentTemplate {
        final String id;
        final String name;
        final Material material;
        final Map<StatType, Double> stats;

        EquipmentTemplate(String id, String name, Material material) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.stats = new HashMap<>();
        }
    }

    /**
     * Builder for equipment templates
     */
    private static class EquipmentBuilder {
        private final EquipmentTemplate template;

        EquipmentBuilder(EquipmentTemplate template) {
            this.template = template;
        }

        EquipmentBuilder addStat(StatType stat, double value) {
            template.stats.put(stat, value);
            return this;
        }
    }
}
