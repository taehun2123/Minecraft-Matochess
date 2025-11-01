package com.matochess.utils;

import com.matochess.data.Equipment;
import com.matochess.data.Unit;
import com.matochess.data.UnitLevel;
import com.matochess.data.UnitTier;
import com.matochess.data.UnitTrait;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for working with NBT data on ItemStacks
 * Used to store Unit and Equipment data on items
 */
public class NBTUtils {

    // NBT Keys for Units
    private static final String KEY_UNIT_ID = "matochess_unit_id";
    private static final String KEY_UNIT_NAME = "matochess_unit_name";
    private static final String KEY_UNIT_TIER = "matochess_unit_tier";
    private static final String KEY_UNIT_LEVEL = "matochess_unit_level";
    private static final String KEY_UNIT_INSTANCE = "matochess_unit_instance";
    private static final String KEY_UNIT_EQUIPMENT = "matochess_unit_equipment";

    // NBT Keys for Equipment
    private static final String KEY_EQUIPMENT_ID = "matochess_equipment_id";
    private static final String KEY_EQUIPMENT_NAME = "matochess_equipment_name";
    private static final String KEY_EQUIPMENT_INSTANCE = "matochess_equipment_instance";

    // Type marker
    private static final String KEY_TYPE = "matochess_type";
    private static final String TYPE_UNIT = "unit";
    private static final String TYPE_EQUIPMENT = "equipment";

    /**
     * Create an ItemStack representing a Unit (기본 값)
     */
    public static ItemStack createUnitItem(Unit unit, NamespacedKey key) {
        return createUnitItem(unit, key, null);
    }

    /**
     * Create an ItemStack representing a Unit with 사전 계산된 표시 스텟
     */
    public static ItemStack createUnitItem(Unit unit, NamespacedKey key, UnitDisplayStats stats) {
        ItemStack item = new ItemStack(unit.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Set type marker
        container.set(new NamespacedKey(key.getNamespace(), KEY_TYPE),
                     PersistentDataType.STRING, TYPE_UNIT);

        // Set unit data
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_ID),
                     PersistentDataType.STRING, unit.getId());
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_NAME),
                     PersistentDataType.STRING, unit.getName());
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_TIER),
                     PersistentDataType.INTEGER, unit.getTier().getTier());
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_LEVEL),
                     PersistentDataType.INTEGER, unit.getLevel().getLevel());
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_INSTANCE),
                     PersistentDataType.STRING, unit.getInstanceId().toString());

        // Set equipment IDs (comma-separated)
        List<String> equipmentIds = new ArrayList<>();
        for (Equipment eq : unit.getEquipment()) {
            equipmentIds.add(eq.getInstanceId().toString());
        }
        container.set(new NamespacedKey(key.getNamespace(), KEY_UNIT_EQUIPMENT),
                     PersistentDataType.STRING, String.join(",", equipmentIds));

        // Set display name and lore
        meta.setDisplayName(unit.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "티어: " + unit.getTier().getDisplay());
        lore.add(ChatColor.GRAY + "레벨: " + unit.getLevel().getDisplay());
        lore.add(ChatColor.GRAY + "비용: " + ChatColor.GOLD + unit.getCost() + "G");
        lore.add("");

        // 시너지 정보 추가
        if (!unit.getTraits().isEmpty()) {
            lore.add(ChatColor.LIGHT_PURPLE + "시너지:");
            for (UnitTrait trait : unit.getTraits()) {
                lore.add(ChatColor.GRAY + "  • " + ChatColor.GOLD + trait.getDisplayName());
            }
            lore.add("");
        }

        double displayHealth = stats != null ? stats.getHealth() : unit.getHealth();
        double displayAttackDamage = stats != null ? stats.getAttackDamage() : unit.getAttackDamage();
        double displayAttackSpeed = stats != null ? stats.getAttackSpeed() : unit.getAttackSpeed();
        double displayArmor = stats != null ? stats.getArmor() : unit.getArmor();
        double displayMagicResist = stats != null ? stats.getMagicResist() : unit.getMagicResist();
        double displayCritChance = stats != null ? stats.getCriticalChance() : unit.getCriticalChance();
        double displayCritDamage = stats != null ? stats.getCriticalDamage() : unit.getCriticalDamage();
        double displayLifeSteal = stats != null ? stats.getLifeSteal() : unit.getLifeSteal();

        lore.add(ChatColor.GREEN + "체력: " + String.format("%.1f", displayHealth));
        lore.add(ChatColor.RED + "공격력: " + String.format("%.1f", displayAttackDamage));
        lore.add(ChatColor.YELLOW + "공격 속도: " + String.format("%.2f", displayAttackSpeed));
        lore.add(ChatColor.AQUA + "방어력: " + String.format("%.1f", displayArmor));
        lore.add(ChatColor.BLUE + "마법 저항력: " + String.format("%.1f", displayMagicResist));
        lore.add(ChatColor.GOLD + "치명타 확률: " + String.format("%.1f", displayCritChance) + "%");
        lore.add(ChatColor.GOLD + "치명타 피해: " + String.format("%.1f", displayCritDamage) + "%");
        lore.add(ChatColor.DARK_RED + "생명력 흡수: " + String.format("%.1f", displayLifeSteal) + "%");

        if (stats != null && !stats.getAdditionalLore().isEmpty()) {
            lore.add("");
            lore.addAll(stats.getAdditionalLore());
        }

        if (!unit.getEquipment().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "장착된 장비:");
            for (Equipment eq : unit.getEquipment()) {
                lore.add(ChatColor.GRAY + "[장비] - " + ChatColor.LIGHT_PURPLE + eq.getName());
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 유닛 표시용 스텟 묶음
     */
    public static class UnitDisplayStats {
        private final double health;
        private final double attackDamage;
        private final double attackSpeed;
        private final double armor;
        private final double magicResist;
        private final double criticalChance;
        private final double criticalDamage;
        private final double lifeSteal;
        private final List<String> additionalLore;

        public UnitDisplayStats(double health, double attackDamage, double attackSpeed,
                                double armor, double magicResist, double criticalChance,
                                double criticalDamage, double lifeSteal, List<String> additionalLore) {
            this.health = health;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
            this.armor = armor;
            this.magicResist = magicResist;
            this.criticalChance = criticalChance;
            this.criticalDamage = criticalDamage;
            this.lifeSteal = lifeSteal;
            this.additionalLore = additionalLore != null ? additionalLore : new ArrayList<>();
        }

        public double getHealth() {
            return health;
        }

        public double getAttackDamage() {
            return attackDamage;
        }

        public double getAttackSpeed() {
            return attackSpeed;
        }

        public double getArmor() {
            return armor;
        }

        public double getMagicResist() {
            return magicResist;
        }

        public double getCriticalChance() {
            return criticalChance;
        }

        public double getCriticalDamage() {
            return criticalDamage;
        }

        public double getLifeSteal() {
            return lifeSteal;
        }

        public List<String> getAdditionalLore() {
            return additionalLore;
        }
    }

    /**
     * Create an ItemStack representing Equipment
     */
    public static ItemStack createEquipmentItem(Equipment equipment, NamespacedKey key) {
        ItemStack item = new ItemStack(equipment.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Set type marker
        container.set(new NamespacedKey(key.getNamespace(), KEY_TYPE),
                     PersistentDataType.STRING, TYPE_EQUIPMENT);

        // Set equipment data
        container.set(new NamespacedKey(key.getNamespace(), KEY_EQUIPMENT_ID),
                     PersistentDataType.STRING, equipment.getId());
        container.set(new NamespacedKey(key.getNamespace(), KEY_EQUIPMENT_NAME),
                     PersistentDataType.STRING, equipment.getName());
        container.set(new NamespacedKey(key.getNamespace(), KEY_EQUIPMENT_INSTANCE),
                     PersistentDataType.STRING, equipment.getInstanceId().toString());

        // Set display name and lore
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + equipment.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "장비");
        lore.add("");
        lore.add(ChatColor.WHITE + "§o원하는 유닛에 드래그 앤 드롭하여 강화하세요!");
        lore.add("");

        equipment.getStatBonuses().forEach((statType, value) -> {
            String sign = value > 0 ? "+" : "";
            lore.add(ChatColor.GREEN + sign + String.format("%.1f", value) + " " + statType.getDisplayName());
        });

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Check if an ItemStack is a Unit
     */
    public static boolean isUnitItem(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String type = container.get(new NamespacedKey(key.getNamespace(), KEY_TYPE),
                                   PersistentDataType.STRING);
        return TYPE_UNIT.equals(type);
    }

    /**
     * Check if an ItemStack is Equipment
     */
    public static boolean isEquipmentItem(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String type = container.get(new NamespacedKey(key.getNamespace(), KEY_TYPE),
                                   PersistentDataType.STRING);
        return TYPE_EQUIPMENT.equals(type);
    }

    /**
     * Get Unit ID from ItemStack
     */
    public static String getUnitId(ItemStack item, NamespacedKey key) {
        if (!isUnitItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(new NamespacedKey(key.getNamespace(), KEY_UNIT_ID),
                           PersistentDataType.STRING);
    }

    /**
     * Get Unit tier from ItemStack
     */
    public static UnitTier getUnitTier(ItemStack item, NamespacedKey key) {
        if (!isUnitItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        Integer tier = container.get(new NamespacedKey(key.getNamespace(), KEY_UNIT_TIER),
                                    PersistentDataType.INTEGER);
        return tier != null ? UnitTier.fromTier(tier) : null;
    }

    /**
     * Get Unit level from ItemStack
     */
    public static UnitLevel getUnitLevel(ItemStack item, NamespacedKey key) {
        if (!isUnitItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        Integer level = container.get(new NamespacedKey(key.getNamespace(), KEY_UNIT_LEVEL),
                                     PersistentDataType.INTEGER);
        return level != null ? UnitLevel.fromLevel(level) : null;
    }

    /**
     * Get Unit instance ID from ItemStack
     */
    public static UUID getUnitInstanceId(ItemStack item, NamespacedKey key) {
        if (!isUnitItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String instanceId = container.get(new NamespacedKey(key.getNamespace(), KEY_UNIT_INSTANCE),
                                         PersistentDataType.STRING);
        return instanceId != null ? UUID.fromString(instanceId) : null;
    }

    /**
     * Get Equipment ID from ItemStack
     */
    public static String getEquipmentId(ItemStack item, NamespacedKey key) {
        if (!isEquipmentItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(new NamespacedKey(key.getNamespace(), KEY_EQUIPMENT_ID),
                           PersistentDataType.STRING);
    }

    /**
     * Get Equipment instance ID from ItemStack
     */
    public static UUID getEquipmentInstanceId(ItemStack item, NamespacedKey key) {
        if (!isEquipmentItem(item, key)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String instanceId = container.get(new NamespacedKey(key.getNamespace(), KEY_EQUIPMENT_INSTANCE),
                                         PersistentDataType.STRING);
        return instanceId != null ? UUID.fromString(instanceId) : null;
    }
}
