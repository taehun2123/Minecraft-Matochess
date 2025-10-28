package com.matochess.data;

/**
 * Represents the enhancement level of a unit
 * Units can be upgraded up to 3 stars by combining 3 identical units
 */
public enum UnitLevel {
    LEVEL_1(1, 1.0, "★"),
    LEVEL_2(2, 2.2, "★★"),
    LEVEL_3(3, 4.4, "★★★");

    // 별칭 (Aliases for backward compatibility)
    public static final UnitLevel ONE = LEVEL_1;
    public static final UnitLevel TWO = LEVEL_2;
    public static final UnitLevel THREE = LEVEL_3;

    private final int level;
    private final double statMultiplier;
    private final String display;

    UnitLevel(int level, double statMultiplier, String display) {
        this.level = level;
        this.statMultiplier = statMultiplier;
        this.display = display;
    }

    public int getLevel() {
        return level;
    }

    public double getStatMultiplier() {
        return statMultiplier;
    }

    public String getDisplay() {
        return display;
    }

    /**
     * Get the next level, or null if already max level
     */
    public UnitLevel next() {
        if (this == LEVEL_3) return null;
        return fromLevel(level + 1);
    }

    public static UnitLevel fromLevel(int level) {
        for (UnitLevel unitLevel : values()) {
            if (unitLevel.level == level) {
                return unitLevel;
            }
        }
        return LEVEL_1;
    }
}
