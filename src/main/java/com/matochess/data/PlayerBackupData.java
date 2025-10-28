package com.matochess.data;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어의 게임 진입 전 데이터를 저장하는 클래스
 */
public class PlayerBackupData {

    private final Location location;
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHandItem;
    private final GameMode gameMode;
    private final double health;
    private final int foodLevel;
    private final float experience;
    private final int level;

    public PlayerBackupData(Location location,
                           ItemStack[] inventoryContents,
                           ItemStack[] armorContents,
                           ItemStack offHandItem,
                           GameMode gameMode,
                           double health,
                           int foodLevel,
                           float experience,
                           int level) {
        this.location = location;
        this.inventoryContents = inventoryContents;
        this.armorContents = armorContents;
        this.offHandItem = offHandItem;
        this.gameMode = gameMode;
        this.health = health;
        this.foodLevel = foodLevel;
        this.experience = experience;
        this.level = level;
    }

    // Getters
    public Location getLocation() {
        return location;
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    public ItemStack getOffHandItem() {
        return offHandItem;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public double getHealth() {
        return health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getExperience() {
        return experience;
    }

    public int getLevel() {
        return level;
    }
}
