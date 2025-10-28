package com.matochess.data;

import org.bukkit.ChatColor;

/**
 * Represents the tier/rarity of a unit
 * From 1-star (common) to 5-star (legendary)
 */
public enum UnitTier {
    ONE_STAR(1, ChatColor.GRAY, "1", 1),
    TWO_STAR(2, ChatColor.GREEN, "2", 2),
    THREE_STAR(3, ChatColor.BLUE, "3", 3),
    FOUR_STAR(4, ChatColor.DARK_PURPLE, "4", 4),
    FIVE_STAR(5, ChatColor.GOLD, "5", 5);

    private final int tier;
    private final ChatColor color;
    private final String display;
    private final int cost;

    UnitTier(int tier, ChatColor color, String display, int cost) {
        this.tier = tier;
        this.color = color;
        this.display = display;
        this.cost = cost;
    }

    public int getTier() {
        return tier;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDisplay() {
        return display;
    }

    public int getCost() {
        return cost;
    }

    public static UnitTier fromTier(int tier) {
        for (UnitTier unitTier : values()) {
            if (unitTier.tier == tier) {
                return unitTier;
            }
        }
        return ONE_STAR;
    }
}
