package com.matochess.core;

import com.matochess.data.Unit;
import com.matochess.data.UnitTier;
import com.matochess.data.UnitTrait;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * Registry for PVE monsters
 * Special monsters that don't appear in the shop
 * Each round has unique monster waves
 */
public class MonsterRegistry {

    private final Map<Integer, List<MonsterWave>> waves;

    public MonsterRegistry() {
        this.waves = new HashMap<>();
        registerDefaultWaves();
    }

    /**
     * Register default monster waves for PVE rounds
     */
    private void registerDefaultWaves() {
        // Round 4 - Undead Horde (언데드 무리)
        waves.put(4, Arrays.asList(
            createMonsterWave(
                Arrays.asList(
                    createMonster("pve_zombie_horde", "§c좀비 무리", EntityType.ZOMBIE,
                        150, 25, 0.7, 15, 5, UnitTier.ONE_STAR, UnitTrait.UNDEAD, UnitTrait.WARRIOR),
                    createMonster("pve_skeleton_squad", "§7스켈레톤 소대", EntityType.SKELETON,
                        120, 30, 1.0, 10, 5, UnitTier.ONE_STAR, UnitTrait.UNDEAD, UnitTrait.RANGER),
                    createMonster("pve_zombie_horde", "§c좀비 무리", EntityType.ZOMBIE,
                        150, 25, 0.7, 15, 5, UnitTier.ONE_STAR, UnitTrait.UNDEAD, UnitTrait.WARRIOR),
                    createMonster("pve_drowned_warrior", "§b익사한 전사", EntityType.DROWNED,
                        180, 28, 0.8, 20, 10, UnitTier.ONE_STAR, UnitTrait.UNDEAD, UnitTrait.OCEAN)
                )
            )
        ));

        // Round 7 - Nether Invasion (네더 침공)
        waves.put(7, Arrays.asList(
            createMonsterWave(
                Arrays.asList(
                    createMonster("pve_piglin_brute", "§6피글린 브루트", EntityType.PIGLIN_BRUTE,
                        350, 50, 0.6, 35, 20, UnitTier.TWO_STAR, UnitTrait.NETHER, UnitTrait.WARRIOR),
                    createMonster("pve_blaze_lord", "§e블레이즈 로드", EntityType.BLAZE,
                        250, 60, 1.2, 15, 10, UnitTier.TWO_STAR, UnitTrait.NETHER, UnitTrait.MAGE),
                    createMonster("pve_wither_skeleton", "§8위더 스켈레톤", EntityType.WITHER_SKELETON,
                        320, 55, 0.8, 25, 15, UnitTier.TWO_STAR, UnitTrait.NETHER, UnitTrait.WARRIOR),
                    createMonster("pve_hoglin_rider", "§c호글린 기수", EntityType.HOGLIN,
                        400, 45, 0.7, 30, 20, UnitTier.TWO_STAR, UnitTrait.NETHER, UnitTrait.TANK)
                )
            )
        ));

        // Round 11 - Illager Raid (일리저 습격)
        waves.put(11, Arrays.asList(
            createMonsterWave(
                Arrays.asList(
                    createMonster("pve_ravager_beast", "§4파괴수", EntityType.RAVAGER,
                        600, 60, 0.5, 60, 40, UnitTier.THREE_STAR, UnitTrait.CONSTRUCT, UnitTrait.TANK),
                    createMonster("pve_evoker_master", "§5소환사 마스터", EntityType.EVOKER,
                        450, 80, 1.0, 25, 30, UnitTier.THREE_STAR, UnitTrait.CONSTRUCT, UnitTrait.SUMMONER),
                    createMonster("pve_vindicator_captain", "§c변명자 대장", EntityType.VINDICATOR,
                        500, 70, 0.8, 40, 35, UnitTier.THREE_STAR, UnitTrait.CONSTRUCT, UnitTrait.WARRIOR),
                    createMonster("pve_pillager_elite", "§9약탈자 정예", EntityType.PILLAGER,
                        420, 65, 1.1, 30, 25, UnitTier.THREE_STAR, UnitTrait.CONSTRUCT, UnitTrait.RANGER),
                    createMonster("pve_vex_swarm", "§7벡스 무리", EntityType.VEX,
                        300, 50, 1.5, 15, 20, UnitTier.THREE_STAR, UnitTrait.CONSTRUCT, UnitTrait.ASSASSIN)
                )
            )
        ));

        // Round 15 - Ancient Guardians (고대 수호자)
        waves.put(15, Arrays.asList(
            createMonsterWave(
                Arrays.asList(
                    createMonster("pve_elder_guardian", "§b엘더 가디언", EntityType.ELDER_GUARDIAN,
                        900, 90, 0.6, 70, 60, UnitTier.FOUR_STAR, UnitTrait.OCEAN, UnitTrait.TANK),
                    createMonster("pve_guardian_elite", "§3가디언 정예", EntityType.GUARDIAN,
                        600, 70, 1.0, 50, 45, UnitTier.THREE_STAR, UnitTrait.OCEAN, UnitTrait.MAGE),
                    createMonster("pve_warden", "§0워든", EntityType.WARDEN,
                        1200, 120, 0.5, 90, 70, UnitTier.FIVE_STAR, UnitTrait.CONSTRUCT, UnitTrait.TANK),
                    createMonster("pve_elder_guardian", "§b엘더 가디언", EntityType.ELDER_GUARDIAN,
                        900, 90, 0.6, 70, 60, UnitTier.FOUR_STAR, UnitTrait.OCEAN, UnitTrait.TANK)
                )
            )
        ));

        // Round 20+ - Ender Apocalypse (엔더 종말)
        waves.put(20, Arrays.asList(
            createMonsterWave(
                Arrays.asList(
                    createMonster("pve_enderman_legion", "§5엔더맨 군단", EntityType.ENDERMAN,
                        800, 100, 1.0, 50, 80, UnitTier.FOUR_STAR, UnitTrait.END, UnitTrait.ASSASSIN),
                    createMonster("pve_ender_dragon", "§d엔더 드래곤", EntityType.ENDER_DRAGON,
                        2000, 150, 0.7, 100, 90, UnitTier.FIVE_STAR, UnitTrait.END, UnitTrait.MAGE),
                    createMonster("pve_enderman_legion", "§5엔더맨 군단", EntityType.ENDERMAN,
                        800, 100, 1.0, 50, 80, UnitTier.FOUR_STAR, UnitTrait.END, UnitTrait.ASSASSIN),
                    createMonster("pve_shulker_guard", "§e셜커 수호자", EntityType.SHULKER,
                        700, 80, 0.8, 80, 70, UnitTier.FOUR_STAR, UnitTrait.END, UnitTrait.TANK),
                    createMonster("pve_enderman_legion", "§5엔더맨 군단", EntityType.ENDERMAN,
                        800, 100, 1.0, 50, 80, UnitTier.FOUR_STAR, UnitTrait.END, UnitTrait.ASSASSIN)
                )
            )
        ));
    }

    /**
     * Create a special PVE monster
     */
    private Unit createMonster(String id, String displayName, EntityType entityType,
                              double health, double attackDamage, double attackSpeed,
                              double armor, double magicResist, UnitTier tier,
                              UnitTrait... traits) {
        Unit monster = new Unit(
            id,
            displayName,
            tier,
            entityType,
            Material.BARRIER // Monsters don't have shop items
        );

        // Set stats
        monster.setBaseStats(health, attackDamage, attackSpeed, armor, magicResist, 1.0);

        // Add traits
        for (UnitTrait trait : traits) {
            monster.addTrait(trait);
        }

        return monster;
    }

    /**
     * Create a monster wave
     */
    private MonsterWave createMonsterWave(List<Unit> monsters) {
        return new MonsterWave(monsters);
    }

    /**
     * Get monster wave for a specific round
     */
    public MonsterWave getWaveForRound(int round) {
        // Round 20+: Ender Apocalypse
        if (round >= 20) {
            return getRandomWave(20);
        }

        // Round 15-19: Ancient Guardians
        if (round >= 15) {
            return getRandomWave(15);
        }

        // Round 11-14: Illager Raid
        if (round >= 11) {
            return getRandomWave(11);
        }

        // Round 7-10: Nether Invasion
        if (round >= 7) {
            return getRandomWave(7);
        }

        // Round 4-6: Undead Horde
        return getRandomWave(4);
    }

    /**
     * Get random wave for a round
     */
    private MonsterWave getRandomWave(int round) {
        List<MonsterWave> roundWaves = waves.get(round);
        if (roundWaves == null || roundWaves.isEmpty()) {
            return null;
        }
        return roundWaves.get(new Random().nextInt(roundWaves.size()));
    }

    /**
     * Monster wave data structure
     */
    public static class MonsterWave {
        private final List<Unit> monsters;

        public MonsterWave(List<Unit> monsters) {
            this.monsters = new ArrayList<>(monsters);
        }

        public List<Unit> getMonsters() {
            // Return copies to prevent modification
            List<Unit> copies = new ArrayList<>();
            for (Unit monster : monsters) {
                copies.add(monster.clone());
            }
            return copies;
        }

        public int getMonsterCount() {
            return monsters.size();
        }
    }
}
