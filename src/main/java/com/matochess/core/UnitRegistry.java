package com.matochess.core;

import com.matochess.data.*;
import com.matochess.data.UnitSkill.SkillType;
import com.matochess.data.UnitSkill.SkillTargetType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all unit definitions
 * Manages unit templates and provides unit creation
 */
public class UnitRegistry {

    private final Map<String, UnitTemplate> templates;

    public UnitRegistry() {
        this.templates = new HashMap<>();
    }

    /**
     * Register all default units
     * 밸런싱: 체력 대폭 상향 (전투 시간 증가), 공격력 하향, 마나 시스템 추가
     */
    public void registerDefaults() {
        // === 1성 유닛 - 저비용, 초반용 (체력: 200-250) ===

        // 물리 공격 - 전사 (근접, 높은 체력)
        register("zombie_warrior", "좀비 전사", UnitTier.ONE_STAR,
                EntityType.ZOMBIE, Material.ROTTEN_FLESH,
                250, 12, 0.7, 10, 5, 1.5,
                AttackType.PHYSICAL,
                new UnitSkill("맹렬한 일격", "강력한 일격으로 단일 대상에게 높은 피해",
                    SkillType.DAMAGE, 2.5, SkillTargetType.SINGLE_ENEMY, 0, 1),
                10.0,
                UnitTrait.UNDEAD, UnitTrait.WARRIOR);

        // 물리 공격 - 원거리 (낮은 체력, 높은 사거리)
        register("skeleton_archer", "스켈레톤 궁수", UnitTier.ONE_STAR,
                EntityType.SKELETON, Material.BONE,
                180, 15, 0.9, 5, 3, 6.0,
                AttackType.PHYSICAL,
                new UnitSkill("관통 화살", "전방의 단일 적을 관통",
                    SkillType.DAMAGE, 2.0, SkillTargetType.SINGLE_ENEMY, 0, 1),
                12.0,
                UnitTrait.UNDEAD, UnitTrait.RANGER);

        // 물리 공격 - 암살자 (빠른 공속, 후방 공격 + 독 DOT)
        register("cave_spider", "동굴 거미", UnitTier.ONE_STAR,
                EntityType.CAVE_SPIDER, Material.SPIDER_EYE,
                160, 14, 1.1, 3, 3, 2.0,
                AttackType.PHYSICAL,
                new UnitSkill("맹독 습격", "후방의 적을 공격하고 맹독 부여",
                    SkillType.TELEPORT, 2.2, SkillTargetType.BACKLINE_ENEMY, 0, 1, true, false),
                15.0,
                UnitTrait.ASSASSIN);

        // === 2성 유닛 - 중간 티어 (체력: 220-280) ===

        // 물리 공격 - 암살자 (높은 기동성, 순간이동)
        register("spider_assassin", "거미 암살자", UnitTier.TWO_STAR,
                EntityType.SPIDER, Material.STRING,
                230, 20, 1.0, 8, 6, 2.0,
                AttackType.PHYSICAL,
                new UnitSkill("암살", "가장 약한 적을 순간이동하여 공격",
                    SkillType.TELEPORT, 3.0, SkillTargetType.LOWEST_HP_ENEMY, 0, 1, false, false),
                12.0,
                UnitTrait.ASSASSIN);

        // 마법 공격 - 단일 대상 (폭발 데미지)
        register("creeper_bomber", "크리퍼 폭파병", UnitTier.TWO_STAR,
                EntityType.CREEPER, Material.GUNPOWDER,
                220, 10, 0.5, 5, 5, 3.0,
                AttackType.MAGICAL,
                new UnitSkill("자폭 공격", "자신 주변의 모든 적에게 광역 마법 피해",
                    SkillType.AOE_DAMAGE, 4.0, SkillTargetType.AOE_AROUND_SELF, 4.0, 5),
                8.0,
                UnitTrait.MAGE);

        // 마법 공격 - 원거리 마법사
        register("drowned_mage", "드라운드 마법사", UnitTier.TWO_STAR,
                EntityType.DROWNED, Material.TRIDENT,
                240, 18, 0.8, 7, 8, 6.0,
                AttackType.MAGICAL,
                new UnitSkill("물의 창", "단일 적에게 강력한 마법 피해",
                    SkillType.DAMAGE, 2.8, SkillTargetType.SINGLE_ENEMY, 0, 1),
                10.0,
                UnitTrait.OCEAN, UnitTrait.MAGE);

        // === 3성 유닛 - 고급 유닛 (체력: 350-500) ===

        // 물리 공격 - 탱커 (매우 높은 방어력, 광역 스턴)
        register("iron_golem_tank", "철 골렘 탱커", UnitTier.THREE_STAR,
                EntityType.IRON_GOLEM, Material.IRON_BLOCK,
                550, 25, 0.5, 30, 15, 1.5,
                AttackType.PHYSICAL,
                new UnitSkill("대지의 충격", "주변 적들을 강타하며 기절시킴",
                    SkillType.AOE_DAMAGE, 2.0, SkillTargetType.AOE_AROUND_SELF, 3.0, 4, false, true),
                8.0,
                UnitTrait.CONSTRUCT, UnitTrait.TANK);

        // 마법 공격 - 광역 화염 마법사
        register("blaze_mage", "블레이즈 화염술사", UnitTier.THREE_STAR,
                EntityType.BLAZE, Material.BLAZE_ROD,
                320, 22, 0.9, 10, 12, 7.0,
                AttackType.MAGICAL,
                new UnitSkill("화염 폭풍", "대상 위치에 화염 폭풍으로 광역 피해",
                    SkillType.AOE_DAMAGE, 3.5, SkillTargetType.AOE_AT_TARGET, 4.0, 6),
                10.0,
                UnitTrait.NETHER, UnitTrait.MAGE);

        // 마법 공격 - 힐러/서포터
        register("witch_healer", "마녀 힐러", UnitTier.THREE_STAR,
                EntityType.WITCH, Material.GLASS_BOTTLE,
                350, 15, 0.7, 8, 20, 5.0,
                AttackType.MAGICAL,
                new UnitSkill("치유의 물약", "모든 아군을 치유",
                    SkillType.HEAL, 1.5, SkillTargetType.ALL_ALLIES, 10.0, 8),
                9.0,
                UnitTrait.SUMMONER);

        // === 4성 유닛 - 희귀 유닛 (체력: 450-650) ===

        // 물리 공격 - 강력한 전사 (높은 공격력)
        register("piglin_brute", "피글린 브루트", UnitTier.FOUR_STAR,
                EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE,
                600, 45, 0.8, 20, 10, 2.0,
                AttackType.PHYSICAL,
                new UnitSkill("광폭화", "자신의 공격력과 공격속도 증가",
                    SkillType.BUFF, 1.8, SkillTargetType.SELF, 0, 1),
                10.0,
                UnitTrait.NETHER, UnitTrait.WARRIOR);

        // 물리 공격 - 순간이동 암살자
        register("enderman_teleporter", "엔더맨 순간이동자", UnitTier.FOUR_STAR,
                EntityType.ENDERMAN, Material.ENDER_PEARL,
                420, 40, 1.2, 12, 15, 3.0,
                AttackType.PHYSICAL,
                new UnitSkill("차원 이동", "후방의 적에게 순간이동하여 높은 피해",
                    SkillType.TELEPORT, 3.5, SkillTargetType.BACKLINE_ENEMY, 0, 1, false, false),
                13.0,
                UnitTrait.END, UnitTrait.ASSASSIN);

        // 물리 공격 - 돌진 탱커
        register("ravager_charger", "파괴수 돌격병", UnitTier.FOUR_STAR,
                EntityType.RAVAGER, Material.SADDLE,
                750, 35, 0.6, 35, 8, 1.5,
                AttackType.PHYSICAL,
                new UnitSkill("파괴적 돌진", "전방의 여러 적을 밀쳐내며 피해",
                    SkillType.AOE_DAMAGE, 2.5, SkillTargetType.MULTIPLE_ENEMIES, 4.0, 4),
                9.0,
                UnitTrait.TANK);

        // === 5성 유닛 - 전설 유닛 (체력: 600-900) ===

        // 물리 공격 - 최강 전사 (위더 DOT)
        register("wither_skeleton", "위더 스켈레톤", UnitTier.FIVE_STAR,
                EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL,
                700, 55, 1.0, 25, 20, 2.5,
                AttackType.PHYSICAL,
                new UnitSkill("위더 일격", "대상에게 치명타와 함께 위더 저주 부여",
                    SkillType.DAMAGE, 4.0, SkillTargetType.HIGHEST_THREAT_ENEMY, 0, 1, true, false),
                11.0,
                UnitTrait.UNDEAD, UnitTrait.WARRIOR);

        // 마법 공격 - 소환사 (유닛 소환)
        register("evoker_summoner", "변환술사 소환사", UnitTier.FIVE_STAR,
                EntityType.EVOKER, Material.TOTEM_OF_UNDYING,
                650, 30, 0.8, 15, 30, 6.0,
                AttackType.MAGICAL,
                new UnitSkill("소환술", "여러 적을 공격하는 마법 송곳니 소환",
                    SkillType.SUMMON, 3.0, SkillTargetType.MULTIPLE_ENEMIES, 6.0, 5),
                9.0,
                UnitTrait.SUMMONER, UnitTrait.MAGE);

        // 물리/마법 하이브리드 - 탱커
        register("elder_guardian", "엘더 가디언", UnitTier.FIVE_STAR,
                EntityType.ELDER_GUARDIAN, Material.PRISMARINE_CRYSTALS,
                900, 40, 0.7, 40, 30, 4.0,
                AttackType.MAGICAL,
                new UnitSkill("상고의 저주", "모든 적의 공격력과 방어력을 대폭 감소",
                    SkillType.DEBUFF, 1.0, SkillTargetType.MULTIPLE_ENEMIES, 10.0, 8),
                8.0,
                UnitTrait.OCEAN, UnitTrait.TANK);
    }

    /**
     * Register a unit template with attack type and skill
     */
    private void register(String id, String name, UnitTier tier,
                         EntityType entityType, Material iconMaterial,
                         double hp, double atk, double atkSpd,
                         double armor, double mr, double range,
                         AttackType attackType, UnitSkill skill, double manaPerAttack,
                         UnitTrait... traits) {
        UnitTemplate template = new UnitTemplate(id, name, tier,
                entityType, iconMaterial,
                hp, atk, atkSpd, armor, mr, range,
                attackType, skill, manaPerAttack, traits);
        templates.put(id, template);
    }

    /**
     * Create a unit from template
     */
    public Unit createUnit(String id) {
        UnitTemplate template = templates.get(id);
        if (template == null) {
            return null;
        }

        Unit unit = new Unit(template.id, template.name, template.tier,
                           template.entityType, template.iconMaterial);
        unit.setBaseStats(template.hp, template.atk, template.atkSpd,
                         template.armor, template.mr, template.range);

        // Set attack type and skill
        unit.setAttackType(template.attackType);
        unit.setSkill(template.skill);
        unit.setManaPerAttack(template.manaPerAttack);

        // Add traits
        for (UnitTrait trait : template.traits) {
            unit.addTrait(trait);
        }

        return unit;
    }

    /**
     * Get all unit IDs of a specific tier
     */
    public List<String> getUnitIdsByTier(UnitTier tier) {
        return templates.values().stream()
                .filter(t -> t.tier == tier)
                .map(t -> t.id)
                .collect(Collectors.toList());
    }

    /**
     * Get random unit of a specific tier
     */
    public Unit getRandomUnitOfTier(UnitTier tier) {
        List<String> unitIds = getUnitIdsByTier(tier);
        if (unitIds.isEmpty()) {
            return null;
        }

        String randomId = unitIds.get(new Random().nextInt(unitIds.size()));
        return createUnit(randomId);
    }

    /**
     * Check if unit exists
     */
    public boolean hasUnit(String id) {
        return templates.containsKey(id);
    }

    /**
     * Get all registered unit IDs
     */
    public Set<String> getAllUnitIds() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Unit template class (updated with attack type and skill)
     */
    private static class UnitTemplate {
        final String id;
        final String name;
        final UnitTier tier;
        final EntityType entityType;
        final Material iconMaterial;
        final double hp, atk, atkSpd, armor, mr, range;
        final AttackType attackType;
        final UnitSkill skill;
        final double manaPerAttack;
        final UnitTrait[] traits;

        UnitTemplate(String id, String name, UnitTier tier,
                    EntityType entityType, Material iconMaterial,
                    double hp, double atk, double atkSpd,
                    double armor, double mr, double range,
                    AttackType attackType, UnitSkill skill, double manaPerAttack,
                    UnitTrait... traits) {
            this.id = id;
            this.name = name;
            this.tier = tier;
            this.entityType = entityType;
            this.iconMaterial = iconMaterial;
            this.hp = hp;
            this.atk = atk;
            this.atkSpd = atkSpd;
            this.armor = armor;
            this.mr = mr;
            this.range = range;
            this.attackType = attackType;
            this.skill = skill;
            this.manaPerAttack = manaPerAttack;
            this.traits = traits;
        }
    }
}
