package com.matochess.core;

import com.matochess.data.*;
import com.matochess.data.TraitBonus.BonusType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages trait synergies and bonuses
 * TFT-style trait system with tiered bonuses
 * Each synergy has clear strengths and weaknesses
 */
public class SynergyManager {

    private final Map<UnitTrait, List<TraitBonus>> traitBonuses;

    public SynergyManager() {
        this.traitBonuses = new HashMap<>();
        initializeTraitBonuses();
    }

    /**
     * Initialize all trait bonuses
     * Each synergy designed with specific strength/weakness
     */
    private void initializeTraitBonuses() {
        // UNDEAD (언데드) - 지속력 특화, 낮은 순간 화력
        // 장점: 체력 회복으로 오래 버팀
        // 단점: 공격력이 낮아 킬 속도 느림
        addTraitBonus(UnitTrait.UNDEAD, 2, BonusType.HEAL_PER_SECOND, 5.0,
                     "언데드 유닛 초당 5 체력 회복");
        addTraitBonus(UnitTrait.UNDEAD, 4, BonusType.HEAL_PER_SECOND, 15.0,
                     "언데드 유닛 초당 15 체력 회복");
        addTraitBonus(UnitTrait.UNDEAD, 6, BonusType.LIFESTEAL, 30.0,
                     "언데드 유닛 30% 생명력 흡수");

        // NETHER (네더) - 극딜 특화, 낮은 방어력
        // 장점: 높은 공격력과 고정 피해
        // 단점: 방어 능력 없어 쉽게 죽음
        addTraitBonus(UnitTrait.NETHER, 2, BonusType.ATTACK_DAMAGE_PERCENT, 25.0,
                     "네더 유닛 공격력 +25%");
        addTraitBonus(UnitTrait.NETHER, 4, BonusType.TRUE_DAMAGE, 40.0,
                     "네더 유닛 공격의 40%를 고정 피해로 전환");

        // OCEAN (바다) - 마법 저항 특화, 물리 방어 약함
        // 장점: 마법 피해에 강함, 마나 회복 빠름
        // 단점: 물리 공격에 취약
        addTraitBonus(UnitTrait.OCEAN, 2, BonusType.MAGIC_RESIST_FLAT, 25.0,
                     "바다 유닛 마법 저항력 +25");
        addTraitBonus(UnitTrait.OCEAN, 4, BonusType.MANA_REGEN, 60.0,
                     "바다 유닛 마나 재생 +60%");
        addTraitBonus(UnitTrait.OCEAN, 6, BonusType.MAGIC_RESIST_PERCENT, 50.0,
                     "바다 유닛 마법 저항력 +50%");

        // CONSTRUCT (구조물) - 물리 방어 특화, 느린 공속
        // 장점: 매우 높은 방어력
        // 단점: 공격 속도 느림
        addTraitBonus(UnitTrait.CONSTRUCT, 2, BonusType.ARMOR_FLAT, 40.0,
                     "구조물 유닛 방어력 +40");
        addTraitBonus(UnitTrait.CONSTRUCT, 4, BonusType.DAMAGE_REDUCTION, 35.0,
                     "구조물 유닛 받는 피해 35% 감소");

        // END (엔드) - 회피/치명타 특화, 낮은 체력
        // 장점: 높은 기동성과 폭발력
        // 단점: 맞으면 쉽게 죽음
        addTraitBonus(UnitTrait.END, 2, BonusType.DODGE_CHANCE, 25.0,
                     "엔드 유닛 25% 회피율");
        addTraitBonus(UnitTrait.END, 4, BonusType.CRITICAL_CHANCE, 50.0,
                     "엔드 유닛 50% 치명타 확률");

        // WARRIOR (전사) - 균형잡힌 탱커, 낮은 딜
        // 장점: 방어력과 체력이 높음
        // 단점: 공격력 낮음
        addTraitBonus(UnitTrait.WARRIOR, 2, BonusType.ARMOR_FLAT, 30.0,
                     "전사 유닛 방어력 +30");
        addTraitBonus(UnitTrait.WARRIOR, 4, BonusType.HEALTH_PERCENT, 40.0,
                     "전사 유닛 체력 +40%");
        addTraitBonus(UnitTrait.WARRIOR, 6, BonusType.DAMAGE_REDUCTION, 25.0,
                     "전사 유닛 받는 피해 25% 감소");

        // MAGE (마법사) - 마법 피해 특화, 물리 방어 약함
        // 장점: 높은 마법 피해와 관통력
        // 단점: 낮은 물리 방어력
        addTraitBonus(UnitTrait.MAGE, 3, BonusType.MAGIC_PENETRATION, 50.0,
                     "마법사 유닛 마법 관통 50%");
        addTraitBonus(UnitTrait.MAGE, 6, BonusType.ATTACK_DAMAGE_PERCENT, 100.0,
                     "마법사 유닛 공격력 +100%");

        // ASSASSIN (암살자) - 치명타 특화, 매우 약한 방어
        // 장점: 폭발적인 순간 화력
        // 단점: 매우 낮은 생존력
        addTraitBonus(UnitTrait.ASSASSIN, 3, BonusType.CRITICAL_CHANCE, 30.0,
                     "암살자 유닛 치명타 확률 +30%");
        addTraitBonus(UnitTrait.ASSASSIN, 6, BonusType.CRITICAL_DAMAGE, 120.0,
                     "암살자 유닛 치명타 피해 +120%");

        // TANK (탱커) - 최고의 생존력, 최악의 화력
        // 장점: 엄청난 체력과 방어력
        // 단점: 공격력 거의 없음
        addTraitBonus(UnitTrait.TANK, 2, BonusType.HEALTH_FLAT, 200.0,
                     "탱커 유닛 체력 +200");
        addTraitBonus(UnitTrait.TANK, 4, BonusType.ARMOR_PERCENT, 60.0,
                     "탱커 유닛 방어력 +60%");

        // RANGER (사수) - 공격 속도 특화, 낮은 방어
        // 장점: 매우 빠른 공격 속도
        // 단점: 낮은 방어력
        addTraitBonus(UnitTrait.RANGER, 2, BonusType.ATTACK_SPEED_PERCENT, 30.0,
                     "사수 유닛 공격 속도 +30%");
        addTraitBonus(UnitTrait.RANGER, 4, BonusType.ATTACK_SPEED_PERCENT, 70.0,
                     "사수 유닛 공격 속도 +70%");
        addTraitBonus(UnitTrait.RANGER, 6, BonusType.ATTACK_DAMAGE_PERCENT, 50.0,
                     "사수 유닛 공격력 +50%");

        // SUMMONER (소환사) - 유틸리티 특화, 약한 개체 능력
        // 장점: 팀 전체 버프
        // 단점: 개별 유닛은 약함
        addTraitBonus(UnitTrait.SUMMONER, 2, BonusType.HEALTH_PERCENT, 30.0,
                     "소환사 유닛 체력 +30%");
        addTraitBonus(UnitTrait.SUMMONER, 4, BonusType.MANA_REGEN, 80.0,
                     "소환사 유닛 마나 재생 +80%");
    }

    /**
     * Add a trait bonus
     */
    private void addTraitBonus(UnitTrait trait, int required, BonusType type,
                              double value, String description) {
        TraitBonus bonus = new TraitBonus(trait, required, type, value, description);
        traitBonuses.computeIfAbsent(trait, k -> new ArrayList<>()).add(bonus);
    }

    /**
     * Calculate active synergies for a list of units
     */
    public Map<UnitTrait, ActiveSynergy> calculateSynergies(List<Unit> units) {
        // Count units per trait
        Map<UnitTrait, Integer> traitCounts = new HashMap<>();

        for (Unit unit : units) {
            for (UnitTrait trait : unit.getTraits()) {
                traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
            }
        }

        // Find active bonuses
        Map<UnitTrait, ActiveSynergy> activeSynergies = new HashMap<>();

        for (Map.Entry<UnitTrait, Integer> entry : traitCounts.entrySet()) {
            UnitTrait trait = entry.getKey();
            int count = entry.getValue();

            List<TraitBonus> bonuses = traitBonuses.get(trait);
            if (bonuses == null) continue;

            // Find all active bonuses for this trait
            List<TraitBonus> activeBonuses = bonuses.stream()
                    .filter(b -> count >= b.getRequiredCount())
                    .collect(Collectors.toList());

            if (!activeBonuses.isEmpty()) {
                // Get the highest tier bonus
                TraitBonus highestBonus = activeBonuses.stream()
                        .max(Comparator.comparingInt(TraitBonus::getRequiredCount))
                        .orElse(null);

                if (highestBonus != null) {
                    activeSynergies.put(trait, new ActiveSynergy(trait, count, highestBonus));
                }
            }
        }

        return activeSynergies;
    }

    /**
     * Get all possible bonuses for a trait
     */
    public List<TraitBonus> getTraitBonuses(UnitTrait trait) {
        return traitBonuses.getOrDefault(trait, new ArrayList<>());
    }

    /**
     * Represents an active synergy
     */
    public static class ActiveSynergy {
        private final UnitTrait trait;
        private final int unitCount;
        private final TraitBonus activeBonus;

        public ActiveSynergy(UnitTrait trait, int unitCount, TraitBonus activeBonus) {
            this.trait = trait;
            this.unitCount = unitCount;
            this.activeBonus = activeBonus;
        }

        public UnitTrait getTrait() {
            return trait;
        }

        public int getUnitCount() {
            return unitCount;
        }

        public TraitBonus getActiveBonus() {
            return activeBonus;
        }

        /**
         * Get formatted display string
         */
        public String getDisplayString() {
            return trait.getFormattedName() + " (" + unitCount + "/" +
                   activeBonus.getRequiredCount() + ") - " +
                   activeBonus.getDescription();
        }
    }
}
