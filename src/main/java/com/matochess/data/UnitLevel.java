package com.matochess.data;

/**
 * 유닛의 강화단계 (1~3성)
 * 3개의 같은 강화단계의 동일한 유닛을 조합하여 다음 강화단계로 승급을 할 수 있습니다.
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
     * 다음 레벨을 얻습니다. null 시 최대 레벨임
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
