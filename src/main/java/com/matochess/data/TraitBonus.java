package com.matochess.data;

import lombok.Getter;

/**
 * Represents a trait synergy bonus
 * Applied when certain number of units with same trait are on board
 */
@Getter
public class TraitBonus {

    private final UnitTrait trait;
    private final int requiredCount;
    private final BonusType bonusType;
    private final double bonusValue;
    private final String description;

    public TraitBonus(UnitTrait trait, int requiredCount, BonusType bonusType,
                     double bonusValue, String description) {
        this.trait = trait;
        this.requiredCount = requiredCount;
        this.bonusType = bonusType;
        this.bonusValue = bonusValue;
        this.description = description;
    }

    /**
     * Types of bonuses that can be applied
     */
    public enum BonusType {
        // 스탯 증가
        HEALTH_FLAT("체력 증가"),
        HEALTH_PERCENT("체력 % 증가"),
        ATTACK_DAMAGE_FLAT("공격력 증가"),
        ATTACK_DAMAGE_PERCENT("공격력 % 증가"),
        ATTACK_SPEED_FLAT("공격 속도 증가"),
        ATTACK_SPEED_PERCENT("공격 속도 % 증가"),
        ARMOR_FLAT("방어력 증가"),
        ARMOR_PERCENT("방어력 % 증가"),
        MAGIC_RESIST_FLAT("마법 저항력 증가"),
        MAGIC_RESIST_PERCENT("마법 저항력 % 증가"),

        // 특수 효과
        LIFESTEAL("생명력 흡수"),
        DAMAGE_REDUCTION("피해 감소"),
        CRITICAL_CHANCE("치명타 확률"),
        CRITICAL_DAMAGE("치명타 피해"),
        DODGE_CHANCE("회피 확률"),
        HEAL_PER_SECOND("초당 체력 회복"),
        MANA_REGEN("마나 재생"),

        // 전투 효과
        ARMOR_PENETRATION("방어구 관통"),
        MAGIC_PENETRATION("마법 관통"),
        TRUE_DAMAGE("고정 피해"),
        AOE_DAMAGE("광역 피해");

        private final String displayName;

        BonusType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
