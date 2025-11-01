package com.matochess.data;

import org.bukkit.ChatColor;

/**
 * 플레이어의 랭킹 티어
 * 구리 티어 ~ 엔더 티어까지 존재
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
     * 포멧된 티어 이름과 색상을 얻습니다.
     */
    public String getFormattedName() {
        return color + displayName;
    }

    /**
     * null은 최고 티어임을 의미, 다음 티어를 얻습니다.
     */
    public Tier next() {
        if (this == ENDER) return null;
        return fromRank(rank + 1);
    }

    /**
     * null은 최소 티어임을 의미, 이전 티어를 얻습니다.
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
