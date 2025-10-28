package com.matochess.data;

import org.bukkit.ChatColor;

/**
 * Represents player ranking tiers
 * From COPPER (lowest) to ENDER (highest)
 */
public enum Tier {
    COPPER(1, ChatColor.RED, "구리"),
    SILVER(2, ChatColor.WHITE, "은"),
    GOLD(3, ChatColor.GOLD, "금"),
    EMERALD(4, ChatColor.GREEN, "에메랄드"),
    DIAMOND(5, ChatColor.AQUA, "다이아"),
    NETHERITE(6, ChatColor.DARK_RED, "네더"),
    ENDER(7, ChatColor.DARK_PURPLE, "엔더");

    private final int rank;
    private final ChatColor color;
    private final String displayName;

    Tier(int rank, ChatColor color, String displayName) {
        this.rank = rank;
        this.color = color;
        this.displayName = displayName;
    }

    public int getRank() {
        return rank;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get formatted tier name with color
     */
    public String getFormattedName() {
        return color + displayName;
    }

    /**
     * Get next tier, or null if already max
     */
    public Tier next() {
        if (this == ENDER) return null;
        return fromRank(rank + 1);
    }

    /**
     * Get previous tier, or null if already min
     */
    public Tier previous() {
        if (this == COPPER) return null;
        return fromRank(rank - 1);
    }

    public static Tier fromRank(int rank) {
        for (Tier tier : values()) {
            if (tier.rank == rank) {
                return tier;
            }
        }
        return COPPER;
    }
}
