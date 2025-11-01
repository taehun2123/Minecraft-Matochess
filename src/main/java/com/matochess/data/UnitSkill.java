package com.matochess.data;

/**
 * 유닛의 스킬 정보
 * TFT 스타일의 스킬 시스템
 */
public class UnitSkill {

    private final String name;
    private final String description;
    private final SkillType type;
    private final double damageMultiplier; // 스킬 데미지 배율 (기본 공격력 대비)
    private final SkillTargetType targetType;
    private final double effectRadius; // 광역 스킬의 경우 반경
    private final int maxTargets; // 최대 타겟 수 (멀티타겟 스킬용)
    private final boolean appliesDot; // DOT 효과 적용 여부
    private final boolean appliesStun; // 스턴 효과 적용 여부

    public UnitSkill(String name, String description, SkillType type,
                     double damageMultiplier, SkillTargetType targetType,
                     double effectRadius, int maxTargets) {
        this(name, description, type, damageMultiplier, targetType, effectRadius, maxTargets, false, false);
    }

    public UnitSkill(String name, String description, SkillType type,
                     double damageMultiplier, SkillTargetType targetType,
                     double effectRadius, int maxTargets, boolean appliesDot, boolean appliesStun) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.damageMultiplier = damageMultiplier;
        this.targetType = targetType;
        this.effectRadius = effectRadius;
        this.maxTargets = maxTargets;
        this.appliesDot = appliesDot;
        this.appliesStun = appliesStun;
    }

    /**
     * 스킬 타입
     */
    public enum SkillType {
        DAMAGE("데미지", "적에게 피해를 줌"),
        AOE_DAMAGE("광역 데미지", "여러 적에게 피해를 줌"),
        BUFF("강화", "아군을 강화함"),
        DEBUFF("약화", "적을 약화시킴"),
        HEAL("힐", "아군을 치유함"),
        SUMMON("소환", "유닛을 소환함"),
        TELEPORT("순간이동", "위치를 이동함"),
        BACKSTAB("후방 공격", "적의 후방으로 이동하여 공격"),
        BEAM("광선 공격", "직선상의 적에게 강력한 광선 피해"),
        CHARGE("돌진", "전방으로 돌진하며 광역 피해"),
        DRAGON_BREATH("용의 숨결", "넓은 범위에 용의 숨결을 분출");

        private final String displayName;
        private final String description;

        SkillType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 스킬 타겟 타입
     */
    public enum SkillTargetType {
        SINGLE_ENEMY("단일 적"),
        MULTIPLE_ENEMIES("다수 적"),
        AOE_AROUND_SELF("자신 주변"),
        AOE_AT_TARGET("대상 위치"),
        LOWEST_HP_ENEMY("최저 체력 적"),
        HIGHEST_THREAT_ENEMY("최고 위협 적"),
        ALL_ALLIES("모든 아군"),
        SELF("자신"),
        BACKLINE_ENEMY("후방 적");

        private final String displayName;

        SkillTargetType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillType getType() {
        return type;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public SkillTargetType getTargetType() {
        return targetType;
    }

    public double getEffectRadius() {
        return effectRadius;
    }

    public int getMaxTargets() {
        return maxTargets;
    }

    public boolean appliesDot() {
        return appliesDot;
    }

    public boolean appliesStun() {
        return appliesStun;
    }
}
