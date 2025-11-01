package com.matochess.combat;

import com.matochess.MatoChessPlugin;
import com.matochess.board.BoardInstance;
import com.matochess.core.EquipmentRegistry;
import com.matochess.core.MonsterRegistry;
import com.matochess.core.SynergyManager;
import com.matochess.data.AttackType;
import com.matochess.data.Equipment;
import com.matochess.data.GamePlayer;
import com.matochess.data.Position;
import com.matochess.data.TraitBonus;
import com.matochess.data.Unit;
import com.matochess.data.UnitSkill;
import com.matochess.data.UnitTier;
import com.matochess.data.UnitTrait;
import com.matochess.data.UnitSkill.SkillTargetType;
import com.matochess.data.UnitSkill.SkillType;
import com.matochess.game.BoardManager;
import com.matochess.game.GameInstance;
import com.matochess.utils.NBTUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

// 한 번의 전투를 담당하며 PVP/PVE 라운드별 유닛 소환, 스킬 처리, 보상 분배까지 전담한다.
public class CombatInstance {
    // ===== 전투 컨텍스트 =====
    private final MatoChessPlugin plugin;                 // 메인 플러그인 인스턴스
    private final UUID combatId;                          // 전투를 식별하는 고유 ID
    private final GamePlayer player1;                     // 파란 팀(혹은 유일한 플레이어)
    private final GamePlayer player2;                     // 빨간 팀, PVE라면 null
    private final boolean isPVE;                          // PVE 라운드 여부
    private final int pveRound;                           // PVE 라운드 인덱스
    private final CombatManager combatManager;            // 전투 수치 계산과 유틸을 제공
    private final Runnable completionCallback;            // 전투 종료 시 호출되는 후처리 콜백

    // ===== 전장 및 엔티티 상태 =====
    private final BoardManager boardManager;              // 체스 보드(전장) 내 유닛 스폰 및 UI 관리
    private final BoardInstance combatBoard;              // 전투가 진행되는 실제 보드 인스턴스
    private final Map<LivingEntity, Unit> entityToUnit;   // 소환된 생명체 -> 게임 유닛 데이터 매핑
    private final Map<LivingEntity, Position> entityBoardPositions; // 유닛의 보드 좌표 기억
    private final Map<UUID, LivingEntity> team1Entities;  // 파란 팀 UUID -> 실 엔티티
    private final Map<UUID, LivingEntity> team2Entities;  // 빨간 팀 UUID -> 실 엔티티

    // ===== 전투 중 상태 효과 =====
    private final Map<UUID, Long> lastAttackTick;         // 유닛별 마지막 공격 틱 저장 (공격 속도 관리)
    private final Map<LivingEntity, DotEffect> dotEffects;// 지속 피해 상태 추적
    private final Map<LivingEntity, Integer> stunnedEntities; // 기절 남은 틱 추적
    private final Map<LivingEntity, Integer> summonedEntities; // 소환수 생존 틱 추적

    // ===== 루프 상태 =====
    private BukkitTask combatTask;                        // 반복 틱 작업
    private boolean isFinished;                           // 전투 종료 여부
    private int tickCount;                                // 현재까지 진행된 틱 수

    // ===== 상수 =====
    private static final int SUMMONED_ENTITY_LIFETIME_TICKS = 40;
    private static final double HEALTH_CAP = 1024.0;
    private static final double GOLD_DROP_RATIO = 0.6;

    public CombatInstance(MatoChessPlugin plugin, UUID combatId, GamePlayer player1, GamePlayer player2, BoardInstance combatBoard, Runnable completionCallback) {
        this.plugin = plugin;
        this.combatId = combatId;
        this.player1 = player1;
        this.player2 = player2;
        this.combatBoard = combatBoard;
        this.combatManager = plugin.getCombatManager();
        this.completionCallback = completionCallback;
        this.isPVE = false;
        this.pveRound = 0;
        this.isFinished = false;
        this.tickCount = 0;
        this.boardManager = new BoardManager(plugin);
        this.entityToUnit = new HashMap<>();
        this.entityBoardPositions = new HashMap<>();
        this.team1Entities = new HashMap<>();
        this.team2Entities = new HashMap<>();
        this.dotEffects = new HashMap<>();
        this.stunnedEntities = new HashMap<>();
        this.lastAttackTick = new HashMap<>();
        this.summonedEntities = new ConcurrentHashMap<>();
    }

    public CombatInstance(MatoChessPlugin plugin, UUID combatId, GamePlayer player, int round, BoardInstance combatBoard, Runnable completionCallback) {
        this.plugin = plugin;
        this.combatId = combatId;
        this.player1 = player;
        this.combatBoard = combatBoard;
        this.combatManager = plugin.getCombatManager();
        this.player2 = null;
        this.isPVE = true;
        this.pveRound = round;
        this.completionCallback = completionCallback;
        this.isFinished = false;
        this.tickCount = 0;
        this.boardManager = new BoardManager(plugin);
        this.entityToUnit = new HashMap<>();
        this.entityBoardPositions = new HashMap<>();
        this.team1Entities = new HashMap<>();
        this.team2Entities = new HashMap<>();
        this.dotEffects = new HashMap<>();
        this.stunnedEntities = new HashMap<>();
        this.lastAttackTick = new HashMap<>();
        this.summonedEntities = new HashMap<>();
    }

    // 전투 루프를 시작하고 플레이어와 유닛을 전장으로 이동시킨다.
    public void start() {
        this.teleportPlayersToCombatArena();
        this.player1.getPlayer().sendMessage("§c§l전투 시작!");
        this.player1.getPlayer().playSound(this.player1.getPlayer().getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
        if (!this.isPVE && this.player2 != null) {
            this.player2.getPlayer().sendMessage("§c§l전투 시작!");
            this.player2.getPlayer().playSound(this.player2.getPlayer().getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
        }

        this.spawnUnits();
        this.applySynergyBonuses();
        this.plugin.getLogger().info("After spawn - Team 1 entities: " + this.team1Entities.size() + ", Team 2 entities: " + this.team2Entities.size());
        Bukkit.getScheduler().runTaskLater(this.plugin, this::spawnCombatStartEffects, 10L);
        this.combatTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.isFinished) {
                this.endCombat(true);
            }

        }, (long)this.plugin.getConfig().getInt("game.combat-time", 60) * 20L);
    }

    private void teleportPlayersToCombatArena() {
        this.teleportSinglePlayerToCombatArena(this.player1);
        if (!this.isPVE && this.player2 != null) {
            this.teleportSinglePlayerToCombatArena(this.player2);
        }

    }

    private void teleportSinglePlayerToCombatArena(GamePlayer gp) {
        Player player = gp.getPlayer();
        if (player != null && player.isOnline() && this.combatBoard != null) {
            Location spawnPos = this.combatBoard.getPlayerSpawnLocation();
            player.setGameMode(GameMode.ADVENTURE);
            player.setWalkSpeed(0.2F);
            player.setFlySpeed(0.1F);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.teleport(spawnPos);
            Location spawn = this.combatBoard.getPlayerSpawnLocation();
            this.plugin.getLogger().info("Player " + player.getName() + " teleported to combat arena at " + String.format("%.1f, %.1f, %.1f", spawn.getX(), spawn.getY(), spawn.getZ()));
        }
    }

    // 전투 개시 파티클과 연출을 양 팀에 적용한다.
    private void spawnCombatStartEffects() {
        for (LivingEntity teamEntity : this.team1Entities.values()) {
            if (teamEntity != null && !teamEntity.isDead()) {
                teamEntity.getWorld().spawnParticle(Particle.FLAME, teamEntity.getLocation().add(0.0, 0.5, 0.0), 10, 0.3, 0.5, 0.3, 0.02);
                teamEntity.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, teamEntity.getLocation().add(0.0, 1.0, 0.0), 15, 0.5, 0.5, 0.5, 0.5);
            }
        }

        for (LivingEntity enemyEntity : this.team2Entities.values()) {
            if (enemyEntity != null && !enemyEntity.isDead()) {
                enemyEntity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, enemyEntity.getLocation().add(0.0, 0.5, 0.0), 10, 0.3, 0.5, 0.3, 0.02);
                enemyEntity.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, enemyEntity.getLocation().add(0.0, 1.0, 0.0), 15, 0.5, 0.5, 0.5, 0.5);
            }
        }
    }

    // 보드상의 배치를 기반으로 실체 유닛을 소환한다.
    private void spawnUnits() {
        plugin.getLogger().info("Spawning units for combat - Team 1: " + player1.getBoard().size() + " units, Team 2: " + (player2 != null ? player2.getBoard().size() : "PVE") + " units");
        player1.getBoard().forEach((position, originalUnit) -> {
            Unit combatUnit = originalUnit.clone();
            LivingEntity entity = boardManager.spawnUnit(combatUnit, position, true, combatBoard);
            if (entity != null && !entity.isDead()) {
                team1Entities.put(combatUnit.getInstanceId(), entity);
                entityToUnit.put(entity, combatUnit);
                entityBoardPositions.put(entity, new Position(position.getX(), position.getY()));
                plugin.getLogger().info("Team 1 unit spawned: " + combatUnit.getId() + " at " + position);
            } else {
                plugin.getLogger().warning("Failed to spawn Team 1 unit: " + originalUnit.getId());
            }
        });
        if (!isPVE && player2 != null) {
            player2.getBoard().forEach((position, originalUnit) -> {
                Unit combatUnit = originalUnit.clone();
                LivingEntity entity = boardManager.spawnUnit(combatUnit, position, false, combatBoard);
                if (entity != null && !entity.isDead()) {
                    team2Entities.put(combatUnit.getInstanceId(), entity);
                    entityToUnit.put(entity, combatUnit);
                    entityBoardPositions.put(entity, new Position(position.getX(), position.getY()));
                    plugin.getLogger().info("Team 2 unit spawned: " + combatUnit.getId() + " at " + position);
                } else {
                    plugin.getLogger().warning("Failed to spawn Team 2 unit: " + originalUnit.getId());
                }

            });
        } else {
            spawnPVEMonsters();
        }

    }

    private void applySynergyBonuses() {
        SynergyManager synergyManager = new SynergyManager();
        List<Unit> team1Units = new ArrayList<>();
        for (LivingEntity teamEntity : team1Entities.values()) {
            Unit unit = entityToUnit.get(teamEntity);
            if (unit != null) {
                team1Units.add(unit);
            }
        }

        if (!team1Units.isEmpty()) {
            Map<UnitTrait, SynergyManager.ActiveSynergy> team1Synergies = synergyManager.calculateSynergies(team1Units);
            for (LivingEntity teamEntity : team1Entities.values()) {
                Unit unit = entityToUnit.get(teamEntity);
                if (unit != null) {
                    applyBonusesToEntity(teamEntity, unit, team1Synergies);
                }
            }

            if (!team1Synergies.isEmpty() && player1 != null) {
                player1.getPlayer().sendMessage("§a§l활성 시너지:");
                for (SynergyManager.ActiveSynergy synergy : team1Synergies.values()) {
                    player1.getPlayer().sendMessage("§e" + synergy.getDisplayString());
                }

                for (LivingEntity teamEntity : team1Entities.values()) {
                    if (teamEntity != null && !teamEntity.isDead()) {
                        teamEntity.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, teamEntity.getLocation().add(0.0, 2.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0);
                        teamEntity.getWorld().spawnParticle(Particle.END_ROD, teamEntity.getLocation().add(0.0, 1.0, 0.0), 3, 0.2, 0.5, 0.2, 0.05);
                    }
                }
            }
        }

        if (!isPVE && player2 != null) {
            List<Unit> team2Units = new ArrayList<>();
            for (LivingEntity enemyEntity : team2Entities.values()) {
                Unit unit = entityToUnit.get(enemyEntity);
                if (unit != null) {
                    team2Units.add(unit);
                }
            }

            if (!team2Units.isEmpty()) {
                Map<UnitTrait, SynergyManager.ActiveSynergy> team2Synergies = synergyManager.calculateSynergies(team2Units);
                for (LivingEntity enemyEntity : team2Entities.values()) {
                    Unit unit = entityToUnit.get(enemyEntity);
                    if (unit != null) {
                        applyBonusesToEntity(enemyEntity, unit, team2Synergies);
                    }
                }

                if (!team2Synergies.isEmpty()) {
                    player2.getPlayer().sendMessage("§a§l활성 시너지:");
                    for (SynergyManager.ActiveSynergy synergy : team2Synergies.values()) {
                        player2.getPlayer().sendMessage("§e" + synergy.getDisplayString());
                    }

                    for (LivingEntity enemyEntity : team2Entities.values()) {
                        if (enemyEntity != null && !enemyEntity.isDead()) {
                            enemyEntity.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, enemyEntity.getLocation().add(0.0, 2.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0);
                            enemyEntity.getWorld().spawnParticle(Particle.END_ROD, enemyEntity.getLocation().add(0.0, 1.0, 0.0), 3, 0.2, 0.5, 0.2, 0.05);
                        }
                    }
                }
            }
        }
    }

    private void applyBonusesToEntity(LivingEntity entity, Unit unit, Map<UnitTrait, SynergyManager.ActiveSynergy> synergies) {
        for (UnitTrait trait : unit.getTraits()) {
            SynergyManager.ActiveSynergy synergy = synergies.get(trait);
            if (synergy != null) {
                TraitBonus bonus = synergy.getActiveBonus();
                applyBonus(entity, unit, bonus);
            }
        }
    }

    private void applyBonus(LivingEntity entity, Unit unit, TraitBonus bonus) {
        double value = bonus.getBonusValue();
        switch (bonus.getBonusType()) {
            case HEALTH_FLAT:
                increaseUnitMaxHealth(unit, value);
                syncEntityHealthAndBar(entity, unit);
                break;
            case HEALTH_PERCENT:
                double currentMax = unit.getHealth();
                double flatIncrease = currentMax * (value / 100.0);
                increaseUnitMaxHealth(unit, flatIncrease);
                syncEntityHealthAndBar(entity, unit);
                break;
            case ATTACK_DAMAGE_FLAT:
                unit.setAttackDamage(unit.getAttackDamage() + value);
                updateEntityAttackDamage(entity, unit);
                break;
            case ATTACK_DAMAGE_PERCENT:
                unit.setAttackDamage(unit.getAttackDamage() * (1.0 + value / 100.0));
                updateEntityAttackDamage(entity, unit);
                break;
            case ARMOR_FLAT:
                unit.setArmor(unit.getArmor() + value);
                break;
            case ARMOR_PERCENT:
                unit.setArmor(unit.getArmor() * (1.0 + value / 100.0));
                break;
            case MAGIC_RESIST_FLAT:
                unit.setMagicResist(unit.getMagicResist() + value);
                break;
            case MAGIC_RESIST_PERCENT:
                unit.setMagicResist(unit.getMagicResist() * (1.0 + value / 100.0));
                break;
            case ATTACK_SPEED_FLAT:
                unit.setAttackSpeed(unit.getAttackSpeed() + value);
                break;
            case ATTACK_SPEED_PERCENT:
                unit.setAttackSpeed(unit.getAttackSpeed() * (1.0 + value / 100.0));
                break;
            case CRITICAL_CHANCE:
                unit.setCriticalChance(unit.getCriticalChance() + value);
                break;
            case CRITICAL_DAMAGE:
                unit.setCriticalDamage(unit.getCriticalDamage() + value);
                break;
            case LIFESTEAL:
                unit.setLifeSteal(unit.getLifeSteal() + value);
        }

    }

    private void increaseUnitMaxHealth(Unit unit, double flatIncrease) {
        if (!(flatIncrease <= 0.0)) {
            double currentHealth = unit.getHealth();
            double targetHealth = Math.min(1024.0, currentHealth + flatIncrease);
            double effectiveIncrease = targetHealth - currentHealth;
            if (!(effectiveIncrease <= 0.0)) {
                double multiplier = unit.getLevel().getStatMultiplier();
                double newBaseHealth = unit.getBaseHealth() + effectiveIncrease / multiplier;
                unit.setBaseStats(newBaseHealth, unit.getBaseAttackDamage(), unit.getBaseAttackSpeed(), unit.getBaseArmor(), unit.getBaseMagicResist(), unit.getBaseRange());
                unit.setCurrentHealth(unit.getHealth());
            }
        }
    }

    private void syncEntityHealthAndBar(LivingEntity entity, Unit unit) {
        syncEntityHealthFromUnit(entity, unit);
        boolean isBlueTeam = team1Entities.containsValue(entity);
        boardManager.updateHealthBar(entity, unit, isBlueTeam);
    }

    private void updateEntityAttackDamage(LivingEntity entity, Unit unit) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr != null) {
            attr.setBaseValue(unit.getAttackDamage());
        }

    }

    private void spawnPVEMonsters() {
        MonsterRegistry monsterRegistry = plugin.getMonsterRegistry();
        MonsterRegistry.MonsterWave wave = monsterRegistry.getWaveForRound(pveRound);
        if (wave == null) {
            plugin.getLogger().warning("No monster wave found for round " + pveRound);
        } else {
            List<Unit> monsters = wave.getMonsters();
            plugin.getLogger().info("Spawning " + monsters.size() + " monsters for round " + pveRound);
            int monstersPerRow = Math.min(4, monsters.size());

            for(int i = 0; i < monsters.size(); ++i) {
                Unit monster = monsters.get(i);
                int row = i / monstersPerRow;
                int col = i % monstersPerRow * 2 + 1;
                Position position = new Position(col, row);
                LivingEntity entity = boardManager.spawnUnit(monster, position, false, combatBoard);
                if (entity != null) {
                    team2Entities.put(monster.getInstanceId(), entity);
                    entityToUnit.put(entity, monster);
                }
            }

            player1.getPlayer().sendMessage("§c" + wave.getMonsterCount() + "마리의 몬스터가 나타났습니다!");
        }
    }

    // 전투 진행 틱을 호출하여 AI, 상태이상, 종료 판정을 수행한다.
    private void tick() {
        if (!isFinished) {
            ++tickCount;
            if (tickCount == 1) {
                plugin.getLogger().info("First tick - Team 1: " + team1Entities.size() + " entities, Team 2: " + team2Entities.size() + " entities");
                assignInitialTargets(team1Entities, team2Entities);
                assignInitialTargets(team2Entities, team1Entities);
                teleportAssassinsToBackline(true);
                if (!isPVE) {
                    teleportAssassinsToBackline(false);
                }
            }

            if (tickCount >= 4) {
                processDotEffects();
                processCCEffects();
                removeDeadEntities();
                updateSummonedEntities();
            }

            if (!team1Entities.isEmpty() && !team2Entities.isEmpty()) {
                performCombatAI();
                enforceVexBounds();
                enforceGroundUnitBounds();
            } else {
                plugin.getLogger().info("Combat ending - Team 1: " + team1Entities.size() + " entities, Team 2: " + team2Entities.size() + " entities (tick: " + tickCount + ")");
                endCombat(false);
            }
        }
    }

    private void assignInitialTargets(Map<UUID, LivingEntity> sourceTeam, Map<UUID, LivingEntity> targetTeam) {
        if (!targetTeam.isEmpty()) {
            List<LivingEntity> targets = new ArrayList<>(targetTeam.values());
            for (LivingEntity sourceEntity : sourceTeam.values()) {
                if (sourceEntity instanceof Mob sourceMob) {
                    LivingEntity closestTarget = findClosestEntity(sourceEntity, targets);
                    if (closestTarget != null) {
                        sourceMob.setTarget(closestTarget);
                    }
                }
            }
        }
    }

    private LivingEntity findClosestEntity(LivingEntity source, Collection<LivingEntity> targets) {
        LivingEntity closest = null;
        double minDistanceSquared = Double.MAX_VALUE;
        Location sourceLoc = source.getLocation();

        for (LivingEntity target : targets) {
            if (target != null && target.isValid() && !target.isDead() && !target.equals(source)) {
                try {
                    double distanceSquared = sourceLoc.distanceSquared(target.getLocation());
                    if (distanceSquared < minDistanceSquared) {
                        minDistanceSquared = distanceSquared;
                        closest = target;
                    }
                } catch (IllegalStateException ex) {
                    plugin.getLogger().warning("Error calculating distance for entity: " + target.getUniqueId() + " - " + ex.getMessage());
                }
            }
        }

        return closest;
    }

    // 비행 소환수(Vex)가 보드 바깥으로 이탈하지 않도록 위치를 보정한다.
    private void enforceVexBounds() {
        if (combatBoard != null && !entityToUnit.isEmpty()) {
            Location corner1 = combatBoard.getCorner1();
            Location corner2 = combatBoard.getCorner2();
            double minX = Math.min(corner1.getX(), corner2.getX()) + 0.5;
            double maxX = Math.max(corner1.getX(), corner2.getX()) - 0.5;
            double boardFloorY = Math.min(corner1.getY(), corner2.getY());
            double floorLimit = boardFloorY + 0.5;
            double maxBoardY = Math.max(corner1.getY(), corner2.getY()) - 0.5;
            double maxAllowedY = boardFloorY + 3.5;
            double maxY = Math.min(Math.min(maxBoardY, boardFloorY + 8.5), maxAllowedY);
            if (maxY <= floorLimit) {
                maxY = floorLimit + 0.25;
            }

            double minZ = Math.min(corner1.getZ(), corner2.getZ()) + 0.5;
            double maxZ = Math.max(corner1.getZ(), corner2.getZ()) - 0.5;
            List<LivingEntity> trackedEntities = new ArrayList<>(entityToUnit.keySet());
            for (LivingEntity entity : trackedEntities) {
                if (!(entity instanceof Vex) || entity.isDead()) {
                    continue;
                }

                Location current = entity.getLocation();
                if (current.getWorld() == null) {
                    continue;
                }

                double clampedX = Math.max(minX, Math.min(maxX, current.getX()));
                double clampedY = Math.max(floorLimit, Math.min(maxY, current.getY()));
                double clampedZ = Math.max(minZ, Math.min(maxZ, current.getZ()));
                boolean adjusted = Math.abs(clampedX - current.getX()) > 0.05
                    || Math.abs(clampedY - current.getY()) > 0.05
                    || Math.abs(clampedZ - current.getZ()) > 0.05;
                if (!adjusted) {
                    continue;
                }

                Location safeLocation = new Location(current.getWorld(), clampedX, clampedY, clampedZ, current.getYaw(), current.getPitch());
                entity.teleport(safeLocation);
                Vector velocity = entity.getVelocity();
                Vector redirected = velocity.clone();
                if (clampedX <= minX + 0.01 || clampedX >= maxX - 0.01) {
                    redirected.setX(-redirected.getX() * 0.4);
                }

                if (clampedZ <= minZ + 0.01 || clampedZ >= maxZ - 0.01) {
                    redirected.setZ(-redirected.getZ() * 0.4);
                }

                if (clampedY <= floorLimit + 0.01) {
                    redirected.setY(Math.abs(redirected.getY()) + 0.12);
                } else if (clampedY >= maxY - 0.01) {
                    redirected.setY(-Math.abs(redirected.getY()) * 0.3);
                }

                entity.setVelocity(redirected);
            }
        }
    }

    // 지속 피해(DOT) 효과를 처리하고 만료된 엔트리를 제거한다.
    private void processDotEffects() {
        List<LivingEntity> toRemove = new ArrayList<>();
        Map<LivingEntity, DotEffect> snapshot = new HashMap<>(dotEffects);

        for (Map.Entry<LivingEntity, DotEffect> entry : snapshot.entrySet()) {
            LivingEntity entity = entry.getKey();
            DotEffect dot = entry.getValue();
            if (entity != null && !entity.isDead()) {
                Unit unit = entityToUnit.get(entity);
                if (unit == null) {
                    toRemove.add(entity);
                    continue;
                }

                double remaining = applyDamageVirtual(entity, unit, dot.damagePerTick);
                boolean killed = remaining <= 0.0 || entity.isDead();
                if (killed) {
                    entity.getWorld().spawnParticle(Particle.SMOKE_LARGE, entity.getLocation().add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.05);
                    checkUnitDeath(unit, entity);
                    toRemove.add(entity);
                    continue;
                }

                Particle particle = Particle.VILLAGER_ANGRY;
                if (dot.effectName.contains("독") || dot.effectName.contains("poison")) {
                    particle = Particle.SLIME;
                } else if (dot.effectName.contains("화염") || dot.effectName.contains("fire")) {
                    particle = Particle.FLAME;
                } else if (dot.effectName.contains("위더") || dot.effectName.contains("wither")) {
                    particle = Particle.SMOKE_LARGE;
                }

                entity.getWorld().spawnParticle(particle, entity.getLocation().add(0.0, 1.0, 0.0), 3, 0.2, 0.3, 0.2, 0.0);
                boolean isBlueTeam = team1Entities.containsValue(entity);
                boardManager.updateHealthBar(entity, unit, isBlueTeam);

                int newTicks = dot.remainingTicks - 1;
                if (newTicks <= 0) {
                    toRemove.add(entity);
                } else {
                    dotEffects.put(entity, new DotEffect(dot.damagePerTick, newTicks, dot.effectName));
                }
            } else {
                toRemove.add(entity);
            }
        }

        for (LivingEntity entity : toRemove) {
            dotEffects.remove(entity);
        }
    }

    // 기절 등 군중제어(콜드다운) 효과를 틱 단위로 관리한다.
    private void processCCEffects() {
        List<LivingEntity> toRemove = new ArrayList<>();
        Map<LivingEntity, Integer> snapshot = new HashMap<>(stunnedEntities);

        for (Map.Entry<LivingEntity, Integer> entry : snapshot.entrySet()) {
            LivingEntity entity = entry.getKey();
            int remainingTicks = entry.getValue();
            if (entity != null && !entity.isDead()) {
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0.0, 2.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0);
                remainingTicks -= 1;
                if (remainingTicks <= 0) {
                    toRemove.add(entity);
                    entity.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, entity.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, 0.05);
                } else {
                    stunnedEntities.put(entity, remainingTicks);
                }
            } else {
                toRemove.add(entity);
            }
        }

        for (LivingEntity entity : toRemove) {
            stunnedEntities.remove(entity);
        }
    }

    // 소환된 유닛의 생존 시간을 감소시키고 만료 시 제거한다.
    private void updateSummonedEntities() {
        if (!summonedEntities.isEmpty()) {
            List<Map.Entry<LivingEntity, Integer>> snapshot = new ArrayList<>(summonedEntities.entrySet());
            for (Map.Entry<LivingEntity, Integer> entry : snapshot) {
                LivingEntity entity = entry.getKey();
                int timeLeft = entry.getValue() - 1;
                if (entity != null && !entity.isDead() && entity.isValid() && timeLeft > 0) {
                    entry.setValue(timeLeft);
                    summonedEntities.put(entity, timeLeft);
                } else {
                    expireSummonedEntity(entity);
                    summonedEntities.remove(entity);
                }
            }
        }
    }

    private void expireSummonedEntity(LivingEntity entity) {
        if (entity != null) {
            entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0.0, 1.0, 0.0), 10, 0.4, 0.4, 0.4, 0.01);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6F, 1.6F);
            removeEntityFromTeams(entity);
            entityToUnit.remove(entity);
            if (entity.isValid()) {
                entity.remove();
            }

        }
    }

    private void removeEntityFromTeams(LivingEntity entity) {
        if (entity != null) {
            team1Entities.entrySet().removeIf((entry) -> entry.getValue().equals(entity));
            team2Entities.entrySet().removeIf((entry) -> entry.getValue().equals(entity));
            summonedEntities.remove(entity);
            entityBoardPositions.remove(entity);
        }
    }

    private void registerSummonedEntity(LivingEntity entity, Unit unit, boolean isTeam1) {
        if (entity != null && unit != null) {
            configureSummonedEntity(entity, unit, isTeam1);
            if (isTeam1) {
                team1Entities.put(unit.getInstanceId(), entity);
            } else {
                team2Entities.put(unit.getInstanceId(), entity);
            }

            entityToUnit.put(entity, unit);
            summonedEntities.put(entity, 40);
            boardManager.updateHealthBar(entity, unit, isTeam1);
            Map<UUID, LivingEntity> enemyTeam = isTeam1 ? team2Entities : team1Entities;
            LivingEntity focusTarget = findClosestEnemy(entity, enemyTeam);
            if (focusTarget != null && entity instanceof Mob mob) {
                mob.setTarget(focusTarget);
            }

        }
    }

    private void configureSummonedEntity(LivingEntity entity, Unit unit, boolean isTeam1) {
        double targetHealth = unit.getHealth();
        double appliedHealth = Math.min(targetHealth, 1024.0);
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(appliedHealth);
        }

        entity.setHealth(appliedHealth);
        unit.setCurrentHealth(targetHealth);
        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            Objects.requireNonNull(entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(unit.getAttackDamage());
        }

        if (entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            double speed = Math.max(0.25, Math.min(0.45, 0.3 + unit.getAttackSpeed() * 0.02));
            Objects.requireNonNull(entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(speed);
        }

        if (entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
            Objects.requireNonNull(entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE)).setBaseValue(64.0);
        }

        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(false);
        entity.setAI(true);
        String namePrefix = isTeam1 ? "§b" : "§c";
        entity.setCustomName(namePrefix + unit.getName());
        entity.setCustomNameVisible(true);
        entity.addScoreboardTag("matochess_summon");
        if (entity instanceof Mob mob) {
            mob.setAware(true);
            mob.setCollidable(true);
        }

    }

    private Unit createSummonedUnit(String id, String name, UnitTier tier, EntityType entityType, double hp, double attack, double attackSpeed, double armor, double magicResist, AttackType attackType) {
        Unit summon = new Unit(id, name, tier, entityType, Material.AIR);
        summon.setBaseStats(hp, attack, attackSpeed, armor, magicResist, 2.0);
        summon.setAttackType(attackType);
        summon.setManaPerAttack(25.0);
        return summon;
    }

    private void spawnEvokerFangBurst(Location center) {
        if (center != null) {
            for(int i = 0; i < 8; ++i) {
                double angle = 0.7853981633974483 * (double)i;
                Location fangLoc = center.clone().add(Math.cos(angle) * 1.5, 0.0, Math.sin(angle) * 1.5);
                Objects.requireNonNull(fangLoc.getWorld()).spawnEntity(fangLoc, EntityType.EVOKER_FANGS);
            }

            Objects.requireNonNull(center.getWorld()).playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2F, 0.8F);
            center.getWorld().spawnParticle(Particle.CRIT_MAGIC, center.clone().add(0.0, 0.4, 0.0), 25, 0.6, 0.4, 0.6, 0.1);
        }
    }

    private void removeDeadEntities() {
        int team1Before = team1Entities.size();
        int team2Before = team2Entities.size();
        team1Entities.values().removeIf((entity) -> {
            boolean remove = entity == null || entity.isDead() || !entity.isValid();
            if (remove && entity != null) {
                plugin.getLogger().info("Removing Team 1 entity - Dead: " + entity.isDead() + ", Valid: " + entity.isValid() + ", Health: " + entity.getHealth());
                if (entity.isValid()) {
                    entity.remove();
                }

                summonedEntities.remove(entity);
                entityBoardPositions.remove(entity);
            } else if (remove) {
                plugin.getLogger().info("Removing Team 1 entity - Entity is null");
            }

            return remove;
        });
        team2Entities.values().removeIf((entity) -> {
            boolean remove = entity == null || entity.isDead() || !entity.isValid();
            if (remove && entity != null) {
                plugin.getLogger().info("Removing Team 2 entity - Dead: " + entity.isDead() + ", Valid: " + entity.isValid() + ", Health: " + entity.getHealth());
                if (entity.isValid()) {
                    entity.remove();
                }

                summonedEntities.remove(entity);
                entityBoardPositions.remove(entity);
            } else if (remove) {
                plugin.getLogger().info("Removing Team 2 entity - Entity is null");
            }

            return remove;
        });
        entityToUnit.keySet().removeIf((entity) -> entity == null || entity.isDead());
        if (team1Before != team1Entities.size() || team2Before != team2Entities.size()) {
            plugin.getLogger().info("Entities removed - Team 1: " + team1Before + " -> " + team1Entities.size() + ", Team 2: " + team2Before + " -> " + team2Entities.size());
        }

    }

    // 두 팀의 유닛을 순회하며 개별 행동 로직을 실행한다.
    private void performCombatAI() {
        List<LivingEntity> team1Snapshot = new ArrayList<>(team1Entities.values());
        for (LivingEntity attacker : team1Snapshot) {
            if (attacker != null && !attacker.isDead()) {
                performEntityAction(attacker, team2Entities);
            }
        }

        List<LivingEntity> team2Snapshot = new ArrayList<>(team2Entities.values());
        for (LivingEntity attacker : team2Snapshot) {
            if (attacker != null && !attacker.isDead()) {
                performEntityAction(attacker, team1Entities);
            }
        }
    }

    // 개별 유닛의 목표 설정, 이동, 공격/스킬 발동을 처리한다.
    private void performEntityAction(LivingEntity attacker, Map<UUID, LivingEntity> enemies) {
        Unit attackerUnit = entityToUnit.get(attacker);
        if (attackerUnit != null) {
            if (!stunnedEntities.containsKey(attacker)) {
                LivingEntity target = findClosestEnemy(attacker, enemies);
                if (target != null) {
                    Unit targetUnit = entityToUnit.get(target);
                    if (targetUnit != null) {
                        boolean isRangedUnit = attacker instanceof Skeleton || attacker instanceof Stray || attacker instanceof Drowned || attacker instanceof Witch || attacker instanceof Pillager || attacker instanceof Blaze || attacker instanceof Creeper || attacker instanceof Evoker || attacker instanceof ElderGuardian || attacker instanceof Guardian || attacker instanceof Shulker;
                        double baseDamage;
                        if (attacker instanceof Mob mob) {
                            LivingEntity currentTarget = mob.getTarget();
                            boolean needsNewTarget = false;
                            if (currentTarget != null) {
                                if (currentTarget.isDead()) {
                                    needsNewTarget = true;
                                } else {
                                    boolean attackerIsTeam1 = team1Entities.containsValue(attacker);
                                    boolean targetIsTeam1 = team1Entities.containsValue(currentTarget);
                                    if (attackerIsTeam1 == targetIsTeam1) {
                                        mob.setTarget(null);
                                        needsNewTarget = true;
                                    }
                                }
                            } else {
                                needsNewTarget = true;
                            }

                            if (needsNewTarget || currentTarget != target) {
                                mob.setTarget(target);
                                if (mob.getTarget() == null || mob.getTarget().isDead()) {
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        if (!mob.isDead() && !target.isDead()) {
                                            mob.setTarget(target);
                                        }

                                    }, 1L);
                                }
                            }

                            if (attacker.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
                                Objects.requireNonNull(attacker.getAttribute(Attribute.GENERIC_FOLLOW_RANGE)).setBaseValue(128.0);
                            }

                            double speedMultiplier = Math.max(1.0, Math.min(1.6, 0.85 + attackerUnit.getAttackSpeed() * 0.55));
                            AttributeInstance moveAttr = attacker.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                            if (moveAttr != null) {
                                baseDamage = isRangedUnit ? 0.24 : 0.32;
                                moveAttr.setBaseValue(baseDamage * speedMultiplier);
                            }
                        }

                        if (attacker.getType() == EntityType.ENDER_DRAGON) {
                            UnitSkill dragonSkill = attackerUnit.getSkill();
                            double cooldownTimeSeconds = 4.0;
                            if (dragonSkill != null) {
                                cooldownTimeSeconds = Math.max(2.5, 6.0 - attackerUnit.getAttackSpeed());
                            }

                            long requiredTicks = Math.max(4L, (long)Math.ceil(cooldownTimeSeconds / 0.5));
                            long lastAttack = lastAttackTick.getOrDefault(attackerUnit.getInstanceId(), (long)tickCount - requiredTicks);
                            if ((long)tickCount - lastAttack >= requiredTicks) {
                                lastAttackTick.put(attackerUnit.getInstanceId(), (long)tickCount);
                                UnitSkill effectiveSkill = dragonSkill != null ? dragonSkill : new UnitSkill("용의 포효", "용의 숨결을 분출하여 광역 피해", SkillType.DRAGON_BREATH, 2.8, SkillTargetType.MULTIPLE_ENEMIES, 7.5, 6);
                                handleDragonBreathSkill(attacker, attackerUnit, enemies, effectiveSkill);
                                return;
                            }
                        }

                        double distance = attacker.getLocation().distance(target.getLocation());
                        double unitRange = Math.max(2.5, attackerUnit.getBaseRange());
                        double attackRange = isRangedUnit ? Math.max(6.0, unitRange) : Math.max(3.0, Math.min(unitRange, 4.0));
                        if (attacker instanceof Creeper) {
                            attackRange = Math.max(attackRange, 8.0);
                        }

                        if (isRangedUnit && distance > attackRange) {
                            Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
                            push.setY(0);
                            if (push.lengthSquared() > 0.01) {
                                push = push.normalize().multiply(0.25);
                                attacker.setVelocity(push);
                            }

                        } else {
                            if (distance <= attackRange) {
                                if (attackerUnit.canCastSkill()) {
                                    castSkill(attacker, attackerUnit, target, targetUnit, enemies);
                                    return;
                                }

                                if (handleCustomBasicAttack(attacker, attackerUnit, target, targetUnit, enemies)) {
                                    return;
                                }

                                double damage;
                                if (isRangedUnit) {
                                    baseDamage = Math.max(0.1, attackerUnit.getAttackSpeed());
                                    damage = 1.0 / baseDamage;
                                    long requiredTicks = Math.max(1L, Math.round(damage / 0.5));
                                    long lastAttack = lastAttackTick.getOrDefault(attackerUnit.getInstanceId(), (long)tickCount - requiredTicks);
                                    if ((long)tickCount - lastAttack < requiredTicks) {
                                        return;
                                    }

                                    lastAttackTick.put(attackerUnit.getInstanceId(), (long)tickCount);
                                    handleRangedAttack(attacker, attackerUnit, target, targetUnit);
                                    return;
                                }

                                baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit);
                                damage = applyCriticalStrike(attackerUnit, target, baseDamage);
                                applySkillDamage(target, targetUnit, damage, true);
                                attackerUnit.addMana(attackerUnit.getManaPerAttack());
                                boolean attackerIsBlue = team1Entities.containsValue(attacker);
                                boardManager.updateHealthBar(attacker, attackerUnit, attackerIsBlue);
                                attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0.0, 1.0, 0.0), 1);
                                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.0F);
                                if (attackerUnit.getLifeSteal() > 0.0) {
                                    double heal = damage * (attackerUnit.getLifeSteal() / 100.0);
                                    applyHealingVirtual(attacker, attackerUnit, heal);
                                    boolean isBlueTeam = team1Entities.containsValue(attacker);
                                    boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
                                    if (heal > 0.0) {
                                        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0.0, 2.0, 0.0), 2, 0.3, 0.3, 0.3, 0.0);
                                        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3F, 2.0F);
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private LivingEntity findClosestEnemy(LivingEntity attacker, Map<UUID, LivingEntity> enemies) {
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead()) {
                double distance = attacker.getLocation().distance(enemy.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = enemy;
                }
            }
        }

        return closest;
    }

    private boolean canExecuteCustomAttack(Unit attackerUnit, double attacksPerSecond) {
        double cooldownSeconds = 1.0 / Math.max(0.1, attacksPerSecond);
        long requiredTicks = Math.max(1L, Math.round(cooldownSeconds / 0.5));
        long last = lastAttackTick.getOrDefault(attackerUnit.getInstanceId(), (long)tickCount - requiredTicks);
        if ((long)tickCount - last < requiredTicks) {
            return true;
        } else {
            lastAttackTick.put(attackerUnit.getInstanceId(), (long)tickCount);
            return false;
        }
    }

    private double getActualMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double base = attr != null ? attr.getBaseValue() : entity.getMaxHealth();
        if (base <= 0.0) {
            base = entity.getMaxHealth();
        }

        return Math.max(1.0, base);
    }

    private void syncEntityHealthFromUnit(LivingEntity entity, Unit unit) {
        double virtualMax = Math.max(1.0, unit.getHealth());
        double actualMax = getActualMaxHealth(entity);
        double virtualCurrent = Math.max(0.0, unit.getCurrentHealth());
        double actualCurrent = Math.min(actualMax, virtualCurrent * actualMax / virtualMax);
        entity.setHealth(actualCurrent);
    }

    private double applyDamageVirtual(LivingEntity entity, Unit unit, double damage) {
        double virtualCurrent = Math.max(0.0, unit.getCurrentHealth() - damage);
        unit.setCurrentHealth(virtualCurrent);
        syncEntityHealthFromUnit(entity, unit);
        return virtualCurrent;
    }

    private void applyHealingVirtual(LivingEntity entity, Unit unit, double heal) {
        double virtualMax = Math.max(1.0, unit.getHealth());
        double virtualCurrent = Math.min(virtualMax, unit.getCurrentHealth() + heal);
        unit.setCurrentHealth(virtualCurrent);
        syncEntityHealthFromUnit(entity, unit);
    }

    private boolean handleCustomBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit, Map<UUID, LivingEntity> enemies) {
        if (target != null && targetUnit != null) {
            EntityType type = attacker.getType();
            return switch (type) {
                case EVOKER -> handleEvokerBasicAttack(attacker, attackerUnit, target, targetUnit);
                case ELDER_GUARDIAN -> handleGuardianBeamAttack(attacker, attackerUnit, enemies, true);
                case GUARDIAN -> handleGuardianBeamAttack(attacker, attackerUnit, enemies, false);
                case SHULKER -> handleShulkerBasicAttack(attacker, attackerUnit, target, targetUnit);
                case PILLAGER -> handlePillagerBasicAttack(attacker, attackerUnit, target, targetUnit);
                case PIGLIN_BRUTE -> handlePiglinBruteBasicAttack(attacker, attackerUnit, target, targetUnit);
                case HOGLIN -> handleHoglinBasicAttack(attacker, attackerUnit, target, targetUnit);
                case STRAY -> handleStrayBasicAttack(attacker, attackerUnit, target, targetUnit);
                default -> false;
            };
        } else {
            return false;
        }
    }

    private boolean handleEvokerBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.35, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Location start = attacker.getLocation().add(0.0, 0.6, 0.0);
            Location end = target.getLocation().add(0.0, 0.2, 0.0);
            Vector direction = end.toVector().subtract(start.toVector());
            int segments = Math.max(6, (int)(direction.length() * 6.0));
            Vector step = direction.clone().multiply(1.0 / (double)segments);

            for(int i = 0; i <= segments; ++i) {
                Location point = start.clone().add(step.clone().multiply(i));
                attacker.getWorld().spawnParticle(Particle.SPELL_WITCH, point, 3, 0.15, 0.15, 0.15, 0.0);
            }

            attacker.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, end, 15, 0.4, 0.4, 0.4, 0.05);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.9F, 1.6F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.15;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private boolean handleStrayBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.45, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Location start = attacker.getEyeLocation();
            Location end = target.getEyeLocation();
            Vector direction = end.toVector().subtract(start.toVector());
            double distance = start.distance(end);
            int segments = Math.max(6, (int)Math.round(distance * 5.0));
            Vector step = direction.clone().multiply(1.0 / (double) segments);

            for(int i = 0; i <= segments; ++i) {
                Location point = start.clone().add(step.clone().multiply(i));
                attacker.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 2, 0.05, 0.05, 0.05, 0.0);
                if (i % 2 == 0) {
                    attacker.getWorld().spawnParticle(Particle.CLOUD, point, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_STRAY_AMBIENT, 0.7F, 0.5F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.12;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 50, 1, true, true, true));
            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private boolean handleGuardianBeamAttack(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, boolean elder) {
        LivingEntity primary = findClosestEnemy(attacker, enemies);
        if (primary == null) {
            return true;
        } else {
            Unit primaryUnit = entityToUnit.get(primary);
            if (primaryUnit == null) {
                return true;
            } else {
                double attacksPerSecond = Math.max(elder ? 0.25 : 0.35, attackerUnit.getAttackSpeed());
                if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
                    return true;
                } else {
                    Location start = attacker.getEyeLocation();
                    Location end = primary.getEyeLocation();
                    Vector direction = end.toVector().subtract(start.toVector());
                    int segments = Math.max(8, (int)(direction.length() * 8.0));
                    Vector step = direction.clone().multiply(1.0 / (double)segments);

                    for(int i = 0; i <= segments; ++i) {
                        Location point = start.clone().add(step.clone().multiply(i));
                        attacker.getWorld().spawnParticle(elder ? Particle.GLOW : Particle.WATER_BUBBLE, point, elder ? 3 : 2, 0.1, 0.1, 0.1, 0.0);
                    }

                    attacker.getWorld().playSound(attacker.getLocation(), elder ? Sound.ENTITY_ELDER_GUARDIAN_CURSE : Sound.ENTITY_GUARDIAN_ATTACK, 1.2F, elder ? 0.6F : 1.0F);
                    double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, primaryUnit);
                    double damage = baseDamage * (elder ? 1.25 : 1.05);
                    damage = applyCriticalStrike(attackerUnit, primary, damage);
                    applySkillDamage(primary, primaryUnit, damage, true);
                    if (elder) {
                        for (LivingEntity enemy : enemies.values()) {
                            if (enemy != null && !enemy.equals(primary) && !enemy.isDead() && enemy.getLocation().distanceSquared(primary.getLocation()) <= 9.0) {
                                Unit splashUnit = entityToUnit.get(enemy);
                                if (splashUnit != null) {
                                    double splashDamage = baseDamage * 0.35;
                                    applySkillDamage(enemy, splashUnit, splashDamage, false);
                                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, true, true, true));
                                }
                            }
                        }
                    }

                    attackerUnit.addMana(attackerUnit.getManaPerAttack());
                    boolean isBlueTeam = team1Entities.containsValue(attacker);
                    boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
                    return true;
                }
            }
        }
    }

    private boolean handleShulkerBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.3, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Location start = attacker.getEyeLocation();
            Location end = target.getEyeLocation();
            Vector direction = end.toVector().subtract(start.toVector());
            int segments = Math.max(6, (int)(direction.length() * 6.0));
            Vector step = direction.clone().multiply(1.0 / (double)segments);

            for(int i = 0; i <= segments; ++i) {
                Location point = start.clone().add(step.clone().multiply(i));
                attacker.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.05, 0.05, 0.05, 0.0);
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 0.9F, 1.0F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.05;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, true, true, true));
            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private boolean handlePillagerBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.4, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Location start = attacker.getEyeLocation();
            Location end = target.getEyeLocation();
            Vector direction = end.toVector().subtract(start.toVector());
            int segments = Math.max(6, (int)(direction.length() * 6.0));
            Vector step = direction.clone().multiply(1.0 / (double)segments);

            for(int i = 0; i <= segments; ++i) {
                Location point = start.clone().add(step.clone().multiply(i));
                attacker.getWorld().spawnParticle(Particle.CRIT, point, 1, 0.05, 0.05, 0.05, 0.0);
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 1.2F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.1;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private boolean handlePiglinBruteBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.45, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Vector dash = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            dash.setY(0);
            if (dash.lengthSquared() > 0.01) {
                dash.normalize().multiply(0.6);
                attacker.setVelocity(dash.setY(0.2));
            }

            attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0.0, 1.0, 0.0), 6, 0.4, 0.4, 0.4, 0.02);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PIGLIN_JEALOUS, 1.2F, 0.9F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.25;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            Vector knockback = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            knockback.setY(0);
            if (knockback.lengthSquared() > 0.01) {
                knockback.normalize().multiply(0.5).setY(0.2);
                target.setVelocity(knockback);
            }

            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private boolean handleHoglinBasicAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double attacksPerSecond = Math.max(0.35, attackerUnit.getAttackSpeed());
        if (canExecuteCustomAttack(attackerUnit, attacksPerSecond)) {
            return true;
        } else {
            Vector dash = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            dash.setY(0);
            if (dash.lengthSquared() > 0.01) {
                dash.normalize().multiply(0.75);
                attacker.setVelocity(dash.setY(0.25));
            }

            attacker.getWorld().spawnParticle(Particle.CLOUD, attacker.getLocation().add(0.0, 0.6, 0.0), 10, 0.4, 0.3, 0.4, 0.02);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_HOGLIN_ATTACK, 1.3F, 0.8F);
            double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit) * 1.2;
            double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
            applySkillDamage(target, targetUnit, damage, true);
            Vector knockback = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            knockback.setY(0);
            if (knockback.lengthSquared() > 0.01) {
                knockback.normalize().multiply(0.7).setY(0.3);
                target.setVelocity(knockback);
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 0, true, true, true));
            attackerUnit.addMana(attackerUnit.getManaPerAttack());
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            return true;
        }
    }

    private void checkUnitDeath(Unit unit, LivingEntity entity) {
        if (isPVE && unit != null && team2Entities.containsKey(unit.getInstanceId())) {
            handlePVEMonsterReward();
        }

        entity.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, entity.getLocation().add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.05);
        entity.getWorld().spawnParticle(Particle.SMOKE_LARGE, entity.getLocation().add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, 0.05);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_DEATH, 0.8F, 0.9F);
        entity.remove();
        if (team1Entities.containsKey(Objects.requireNonNull(unit).getInstanceId())) {
            team1Entities.remove(unit.getInstanceId());
        } else team2Entities.remove(unit.getInstanceId());

        entityToUnit.remove(entity);
    }

    private void handlePVEMonsterReward() {
        if (isPVE && player1 != null) {
            Player player = player1.getPlayer();
            if (player != null) {
                GameInstance game = plugin.getGameManager().getGame(player1.getGameId());
                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll < 0.6) {
                    int goldReward = ThreadLocalRandom.current().nextInt(1, 6);
                    player1.addGold(goldReward);
                    player.sendMessage("§6[PVE 보상] §f골드 §e+" + goldReward + "G");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.1F);
                    if (game != null) {
                        game.updateGameDisplays();
                    }

                    plugin.getInventoryGUIManager().refreshGameInventory(player, player1);
                } else {
                    EquipmentRegistry equipmentRegistry = plugin.getEquipmentRegistry();
                    if (equipmentRegistry == null) {
                        int fallbackGold = ThreadLocalRandom.current().nextInt(1, 6);
                        player1.addGold(fallbackGold);
                        player.sendMessage("§6[PVE 보상] §f골드 §e+" + fallbackGold + "G");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.1F);
                        if (game != null) {
                            game.updateGameDisplays();
                        }

                        plugin.getInventoryGUIManager().refreshGameInventory(player, player1);
                    } else {
                        Equipment equipment = equipmentRegistry.getRandomEquipment();
                        if (equipment == null) {
                            int fallbackGold = ThreadLocalRandom.current().nextInt(1, 6);
                            player1.addGold(fallbackGold);
                            player.sendMessage("§6[PVE 보상] §f골드 §e+" + fallbackGold + "G");
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.1F);
                            if (game != null) {
                                game.updateGameDisplays();
                            }

                            plugin.getInventoryGUIManager().refreshGameInventory(player, player1);
                        } else {
                            player1.addEquipment(equipment);
                            ItemStack equipmentItem = NBTUtils.createEquipmentItem(equipment, plugin.getKey());
                            boolean stored = plugin.getInventoryGUIManager().tryAddEquipmentItemToInventory(player, equipmentItem);
                            if (stored) {
                                player.sendMessage("§d[PVE 보상] §f장비 획득: §d" + equipment.getName());
                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
                            } else {
                                Item dropped = player.getWorld().dropItemNaturally(player.getLocation().add(0.0, 0.2, 0.0), equipmentItem);
                                dropped.setOwner(player.getUniqueId());
                                dropped.setPickupDelay(10);
                                player.sendMessage("§d[PVE 보상] §f장비가 바닥에 떨어졌습니다! §7(준비 단계 종료 전 회수)");
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
                                if (game != null) {
                                    game.registerPendingGroundDrop(player1.getPlayerId(), dropped, equipment.getInstanceId());
                                }
                            }

                            if (game != null) {
                                game.updateGameDisplays();
                            }

                            plugin.getInventoryGUIManager().refreshGameInventory(player, player1);
                        }
                    }
                }
            }
        }
    }

    private void castSkill(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit, Map<UUID, LivingEntity> enemies) {
        UnitSkill skill = attackerUnit.getSkill();
        if (skill != null) {
            attackerUnit.resetMana();
            boolean isBlueTeam = team1Entities.containsValue(attacker);
            boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
            attacker.getWorld().spawnParticle(Particle.SPELL_WITCH, attacker.getLocation().add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, 0.1);
            attacker.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, attacker.getLocation().add(0.0, 2.0, 0.0), 20, 0.5, 1.0, 0.5, 0.5);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 1.2F);
            if (attacker instanceof Creeper) {
                handleCreeperBombSkill(attacker, attackerUnit, target, enemies, skill);
            } else {
                switch (skill.getType()) {
                    case DAMAGE:
                    case BACKSTAB:
                        handleSingleTargetSkill(attackerUnit, target, targetUnit, skill);
                        break;
                    case AOE_DAMAGE:
                        handleAOESkill(attacker, attackerUnit, enemies, skill);
                        break;
                    case BUFF:
                        handleBuffSkill(attacker, attackerUnit, skill);
                        break;
                    case DEBUFF:
                        handleDebuffSkill(attacker, enemies, skill);
                        break;
                    case HEAL:
                        handleHealSkill(attacker, attackerUnit, skill);
                        break;
                    case SUMMON:
                        handleSummonSkill(attacker, attackerUnit, skill);
                        break;
                    case TELEPORT:
                        handleTeleportSkill(attacker, attackerUnit, enemies, skill);
                        break;
                    case BEAM:
                        handleBeamSkill(attacker, attackerUnit, enemies, skill);
                        break;
                    case CHARGE:
                        handleChargeSkill(attacker, attackerUnit, target, targetUnit, enemies, skill);
                        break;
                    case DRAGON_BREATH:
                        handleDragonBreathSkill(attacker, attackerUnit, enemies, skill);
                }

            }
        }
    }

    private void handleSummonSkill(LivingEntity attacker, Unit attackerUnit, UnitSkill skill) {
        attacker.getWorld().spawnParticle(Particle.PORTAL, attacker.getLocation().add(0.0, 1.0, 0.0), 60, 0.6, 1.2, 0.6, 0.4);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.4F, 1.0F);
        boolean isTeam1 = team1Entities.containsValue(attacker);
        Map<UUID, LivingEntity> enemies = isTeam1 ? team2Entities : team1Entities;
        if (!"evoker_summoner".equals(attackerUnit.getId()) && !"pve_evoker_master".equals(attackerUnit.getId())) {
            for(int i = 0; i < Math.max(2, skill.getMaxTargets()); ++i) {
                double angle = (double)i * (360.0 / (double)Math.max(2, skill.getMaxTargets())) * Math.PI / 180.0;
                Location spawnLoc = attacker.getLocation().clone().add(Math.cos(angle) * 1.8, 0.0, Math.sin(angle) * 1.8);
                Wolf wolf = (Wolf)attacker.getWorld().spawnEntity(spawnLoc, EntityType.WOLF);
                wolf.setAngry(true);
                Unit summonedWolf = createSummonedUnit("summoned_wolf", "소환 늑대", UnitTier.THREE_STAR, EntityType.WOLF, 220.0, 35.0, 1.3, 18.0, 10.0, AttackType.PHYSICAL);
                registerSummonedEntity(wolf, summonedWolf, isTeam1);
                LivingEntity focusTarget = findClosestEnemy(wolf, enemies);
                if (focusTarget != null) {
                    wolf.setTarget(focusTarget);
                }

                wolf.getWorld().spawnParticle(Particle.SWEEP_ATTACK, wolf.getLocation().add(0.0, 0.8, 0.0), 8, 0.2, 0.2, 0.2, 0.01);
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.8F, 0.6F);
        } else {
            LivingEntity focusTarget = findClosestEnemy(attacker, enemies);
            if (focusTarget != null) {
                spawnEvokerFangBurst(focusTarget.getLocation());
            }

            for(int i = 0; i < 2; ++i) {
                double angle = ((double)i * 180.0 + 45.0) * Math.PI / 180.0;
                Location spawnLoc = attacker.getLocation().clone().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0);
                Vex vex = (Vex)attacker.getWorld().spawnEntity(spawnLoc, EntityType.VEX);
                Unit summonedSpirit = createSummonedUnit("summoned_arcane_vex", "정령 칼날", UnitTier.FOUR_STAR, EntityType.VEX, 180.0, 40.0, 1.6, 20.0, 20.0, AttackType.MAGICAL);
                summonedSpirit.setManaPerAttack(40.0);
                registerSummonedEntity(vex, summonedSpirit, isTeam1);
                if (focusTarget != null) {
                    vex.setTarget(focusTarget);
                }

                vex.getWorld().spawnParticle(Particle.END_ROD, vex.getLocation().add(0.0, 0.8, 0.0), 12, 0.2, 0.4, 0.2, 0.01);
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.3F, 1.6F);
        }
    }

    private void handleCreeperBombSkill(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        LivingEntity primaryTarget = target;
        if (target == null || target.isDead()) {
            primaryTarget = findClosestEnemy(attacker, enemies);
        }

        if (primaryTarget != null) {
            Location start = attacker.getEyeLocation();
            Location impact = primaryTarget.getLocation().clone();
            double radius = Math.max(3.0, skill.getEffectRadius());
            double travelTimeTicks = Math.min(12, Math.max(6, (int)Math.round(start.distance(impact) * 1.5)));
            Vector direction = impact.clone().subtract(start).toVector();
            int segments = Math.max(8, (int)(direction.length() * 6.0));

            for(int i = 0; i <= segments; ++i) {
                double t = (double)i / (double)segments;
                Location point = start.clone().add(direction.clone().multiply(t));
                Objects.requireNonNull(point.getWorld()).spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.6F, 1.2F);
            Location lockedImpact = impact.clone();
            LivingEntity capturedTarget = primaryTarget;
            Map<UUID, LivingEntity> enemySnapshot = new HashMap<>(enemies);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location explosionLoc = !capturedTarget.isDead() && capturedTarget.isValid() ? capturedTarget.getLocation().clone() : lockedImpact.clone();
                Objects.requireNonNull(explosionLoc.getWorld()).spawnParticle(Particle.EXPLOSION_LARGE, explosionLoc.clone().add(0.0, 1.0, 0.0), 1, 0.2, 0.2, 0.2, 0.0);
                explosionLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, explosionLoc.clone().add(0.0, 1.0, 0.0), 25, radius / 2.0, 0.5, radius / 2.0, 0.05);
                explosionLoc.getWorld().spawnParticle(Particle.FLAME, explosionLoc.clone().add(0.0, 0.6, 0.0), 20, radius / 3.0, 0.4, radius / 3.0, 0.04);
                explosionLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);
                Collection<LivingEntity> targetsSnapshot = new ArrayList<>(enemySnapshot.values());
                for (LivingEntity enemy : targetsSnapshot) {
                    if (enemy != null && !enemy.isDead()) {
                        double distance = enemy.getLocation().distance(explosionLoc);
                        if (!(distance > radius)) {
                            Unit enemyUnit = (Unit)entityToUnit.get(enemy);
                            if (enemyUnit != null) {
                                double falloff = 1.0 - distance / radius * 0.4;
                                falloff = Math.max(0.35, Math.min(1.0, falloff));
                                double baseDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, enemyUnit, skill.getDamageMultiplier());
                                double scaledDamage = baseDamage * falloff;
                                applySkillDamage(enemy, enemyUnit, scaledDamage, true);
                                Vector knockback = enemy.getLocation().toVector().subtract(explosionLoc.toVector());
                                if (knockback.lengthSquared() > 0.01) {
                                    knockback.normalize().multiply(0.4).setY(0.35);
                                    enemy.setVelocity(knockback);
                                }
                            }
                        }
                    }
                }

            }, (long)travelTimeTicks);
        }
    }

    private void handleBeamSkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        if (!enemies.isEmpty()) {
            Location origin = attacker.getLocation().add(0.0, 1.2, 0.0);
            Vector direction = attacker.getLocation().getDirection().normalize();
            double range = Math.max(6.0, skill.getEffectRadius());
            double maxAngle = Math.toRadians(35.0);
            int maxTargets = Math.max(2, skill.getMaxTargets());

            for(double step = 0.0; step <= range; step += 0.4) {
                Location particleLoc = origin.clone().add(direction.clone().multiply(step));
                Objects.requireNonNull(origin.getWorld()).spawnParticle(Particle.GLOW, particleLoc, 4, 0.1, 0.1, 0.1, 0.01);
                origin.getWorld().spawnParticle(Particle.WATER_SPLASH, particleLoc, 3, 0.0, 0.0, 0.0, 0.0);
            }

            List<LivingEntity> victims = new ArrayList<>();
            for (LivingEntity potentialTarget : enemies.values()) {
                if (potentialTarget != null && !potentialTarget.isDead()) {
                    Vector toEnemy = potentialTarget.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(origin.toVector());
                    double distance = toEnemy.length();
                    if (distance <= range && distance > 0.1) {
                        double angle = direction.angle(toEnemy.clone().normalize());
                        if (angle <= maxAngle) {
                            victims.add(potentialTarget);
                        }
                    }
                }
            }

            victims.sort(Comparator.comparingDouble((e) -> e.getLocation().distanceSquared(origin)));
            if (victims.size() > maxTargets) {
                victims = victims.subList(0, maxTargets);
            }

            for (LivingEntity victim : victims) {
                Unit victimUnit = entityToUnit.get(victim);
                if (victimUnit != null) {
                    double damage = combatManager.calculateSkillDamage(attackerUnit, victimUnit, skill.getDamageMultiplier());
                    applySkillDamage(victim, victimUnit, damage, true);
                    victimUnit.setAttackDamage(Math.max(10.0, victimUnit.getAttackDamage() * 0.9));
                    victimUnit.setArmor(Math.max(5.0, victimUnit.getArmor() * 0.85));
                    victim.getWorld().spawnParticle(Particle.GLOW, victim.getLocation().add(0.0, 1.0, 0.0), 15, 0.2, 0.4, 0.2, 0.04);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.8F, 0.5F);
                }
            }

            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2F, 0.7F);
        }
    }

    private void handleChargeSkill(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        if (target != null && targetUnit != null) {
            Vector dash = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            dash.setY(0);
            if (dash.lengthSquared() > 0.01) {
                dash.normalize().multiply(1.2);
                attacker.setVelocity(dash.setY(0.2));
            }

            attacker.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, attacker.getLocation().add(0.0, 0.6, 0.0), 12, 0.3, 0.3, 0.3, 0.015);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1.4F, 0.6F);
            double radius = Math.max(2.8, skill.getEffectRadius());
            int maxTargets = Math.max(1, skill.getMaxTargets());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!attacker.isDead()) {
                    Location impact = attacker.getLocation();
                    List<LivingEntity> victims = new ArrayList<>();

                    for (LivingEntity potentialTarget : enemies.values()) {
                        if (potentialTarget != null && !potentialTarget.isDead() && potentialTarget.getLocation().distanceSquared(impact) <= radius * radius) {
                            victims.add(potentialTarget);
                        }
                    }

                    victims.sort(Comparator.comparingDouble((e) -> e.getLocation().distanceSquared(impact)));
                    if (victims.size() > maxTargets) {
                        victims = victims.subList(0, maxTargets);
                    }

                    for (LivingEntity victim : victims) {
                        Unit victimUnit = entityToUnit.get(victim);
                        if (victimUnit != null) {
                            double damage = combatManager.calculateSkillDamage(attackerUnit, victimUnit, skill.getDamageMultiplier());
                            applySkillDamage(victim, victimUnit, damage, true);
                            Vector knockback = victim.getLocation().toVector().subtract(impact.toVector()).setY(0);
                            if (knockback.lengthSquared() > 0.01) {
                                knockback.normalize().multiply(0.7);
                                victim.setVelocity(knockback.setY(0.35));
                            }
                        }
                    }

                    Objects.requireNonNull(impact.getWorld()).spawnParticle(Particle.EXPLOSION_NORMAL, impact.add(0.0, 0.2, 0.0), 30, radius / 2.5, 0.25, radius / 2.5, 0.05);
                    impact.getWorld().playSound(impact, Sound.ENTITY_HOGLIN_ATTACK, 1.2F, 0.8F);
                }
            }, 6L);
        }
    }

    private void handleDragonBreathSkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        if (!enemies.isEmpty()) {
            Location center = attacker.getLocation().add(0.0, 1.0, 0.0);
            double radius = Math.max(7.0, skill.getEffectRadius());
            int maxTargets = Math.max(3, skill.getMaxTargets());
            double multiplier = Math.max(1.8, skill.getDamageMultiplier());
            Objects.requireNonNull(center.getWorld()).spawnParticle(Particle.DRAGON_BREATH, center, 120, radius / 3.0, 1.2, radius / 3.0, 0.05);
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 0.7F);
            List<LivingEntity> victims = new ArrayList<>();
            for (LivingEntity potentialTarget : enemies.values()) {
                if (potentialTarget != null && !potentialTarget.isDead() && potentialTarget.getLocation().distanceSquared(center) <= radius * radius) {
                    victims.add(potentialTarget);
                }
            }

            victims.sort(Comparator.comparingDouble((e) -> e.getLocation().distanceSquared(center)));
            if (victims.size() > maxTargets) {
                victims = victims.subList(0, maxTargets);
            }

            for (LivingEntity victim : victims) {
                Unit victimUnit = entityToUnit.get(victim);
                if (victimUnit != null) {
                    double damage = combatManager.calculateSkillDamage(attackerUnit, victimUnit, multiplier);
                    applySkillDamage(victim, victimUnit, damage, true);
                    double dotDamage = damage * 0.2;
                    dotEffects.put(victim, new DotEffect(dotDamage, 6, "Dragon Breath"));
                }
            }

        }
    }

    private void applySkillDamage(LivingEntity target, Unit targetUnit, double damage, boolean impactEffects) {
        if (target != null && targetUnit != null && !target.isDead()) {
            double remaining = applyDamageVirtual(target, targetUnit, damage);
            if (!(remaining <= 0.0) && !target.isDead()) {
                boolean isBlueTeam = team1Entities.containsValue(target);
                boardManager.updateHealthBar(target, targetUnit, isBlueTeam);
                if (impactEffects) {
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0.0, 1.2, 0.0), 6, 0.2, 0.4, 0.2, 0.02);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.7F, 0.9F);
                }

            } else {
                checkUnitDeath(targetUnit, target);
            }
        }
    }

    private void handleTeleportSkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        LivingEntity backlineTarget = null;
        double lowestHealth = Double.MAX_VALUE;

        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead()) {
                Unit targetUnit = entityToUnit.get(enemy);
                double currentHealth = targetUnit != null ? targetUnit.getCurrentHealth() : enemy.getHealth();
                if (currentHealth < lowestHealth) {
                    lowestHealth = currentHealth;
                    backlineTarget = enemy;
                }
            }
        }

        if (backlineTarget != null) {
            attacker.getWorld().spawnParticle(Particle.PORTAL, attacker.getLocation().add(0.0, 1.0, 0.0), 30, 0.3, 0.5, 0.3, 0.1);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
            Location targetLoc = backlineTarget.getLocation();
            Location teleportLoc = targetLoc.clone().add(targetLoc.getDirection().multiply(-2));
            teleportLoc.setY(targetLoc.getY());
            attacker.teleport(teleportLoc);
            attacker.getWorld().spawnParticle(Particle.PORTAL, attacker.getLocation().add(0.0, 1.0, 0.0), 30, 0.3, 0.5, 0.3, 0.1);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
            Unit targetUnit = entityToUnit.get(backlineTarget);
            if (targetUnit != null) {
                double skillDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, targetUnit, skill.getDamageMultiplier());
                applySkillDamage(backlineTarget, targetUnit, skillDamage, true);
                if (!(targetUnit.getCurrentHealth() <= 0.0) && backlineTarget.isValid()) {
                    backlineTarget.getWorld().spawnParticle(Particle.SWEEP_ATTACK, backlineTarget.getLocation().add(0.0, 1.0, 0.0), 5, 0.3, 0.5, 0.3, 0.0);
                    backlineTarget.getWorld().playSound(backlineTarget.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
                } else {
                    backlineTarget.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, backlineTarget.getLocation().add(0.0, 1.0, 0.0), 1);
                }
            }

        }
    }

    private void handleSingleTargetSkill(Unit attackerUnit, LivingEntity target, Unit targetUnit, UnitSkill skill) {
        double skillDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, targetUnit, skill.getDamageMultiplier());
        Location hitLocation = target.getLocation().clone();
        double remaining = applyDamageVirtual(target, targetUnit, skillDamage);
        boolean killed = remaining <= 0.0 || target.isDead();
        if (killed) {
            target.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hitLocation.add(0.0, 1.0, 0.0), 1);
            checkUnitDeath(targetUnit, target);
        } else {
            boolean isBlueTeam = team1Entities.containsValue(target);
            boardManager.updateHealthBar(target, targetUnit, isBlueTeam);
            if (skill.appliesDot()) {
                double dotDamage = skillDamage * 0.2;
                dotEffects.put(target, new DotEffect(dotDamage, 6, skill.getName()));
                target.getWorld().spawnParticle(Particle.DRIP_LAVA, target.getLocation().add(0.0, 2.0, 0.0), 10, 0.3, 0.5, 0.3, 0.0);
            }

            if (skill.appliesStun()) {
                stunnedEntities.put(target, 4);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 2.0, 0.0), 15, 0.3, 0.5, 0.3, 0.0);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0F, 1.5F);
            }
        }

        if (target.isValid()) {
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation().add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.5F);
        }

    }

    // 광역 스킬을 발동하고 최대 타겟 수만큼 피해와 부가 효과를 적용한다.
    private void handleAOESkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        Location center = attacker.getLocation();
        double radius = skill.getEffectRadius();
        int hitCount = 0;
        int maxTargets = skill.getMaxTargets();

        for(int i = 0; i < 360; i += 30) {
            double radian = Math.toRadians(i);
            Location particleLoc = center.clone().add(Math.cos(radian) * radius, 0.5, Math.sin(radian) * radius);
            attacker.getWorld().spawnParticle(Particle.FLAME, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
        }

        List<LivingEntity> potentialTargets = new ArrayList<>(enemies.values());
        for (LivingEntity enemy : potentialTargets) {
            if (enemy == null || enemy.isDead()) {
                continue;
            }

            if (hitCount >= maxTargets) {
                break;
            }

            double distance = enemy.getLocation().distance(center);
            if (distance > radius) {
                continue;
            }

            Unit enemyUnit = entityToUnit.get(enemy);
            if (enemyUnit == null) {
                continue;
            }

            double skillDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, enemyUnit, skill.getDamageMultiplier());
            double remaining = applyDamageVirtual(enemy, enemyUnit, skillDamage);
            boolean killed = remaining <= 0.0 || enemy.isDead();
            if (killed) {
                checkUnitDeath(enemyUnit, enemy);
            } else {
                boolean isBlueTeam = team1Entities.containsValue(enemy);
                boardManager.updateHealthBar(enemy, enemyUnit, isBlueTeam);
                if (skill.appliesDot()) {
                    double dotDamage = skillDamage * 0.15;
                    dotEffects.put(enemy, new DotEffect(dotDamage, 6, skill.getName()));
                }

                if (skill.appliesStun()) {
                    stunnedEntities.put(enemy, 3);
                    enemy.getWorld().spawnParticle(Particle.CRIT, enemy.getLocation().add(0.0, 2.0, 0.0), 10, 0.3, 0.5, 0.3, 0.0);
                }
            }

            enemy.getWorld().spawnParticle(Particle.LAVA, enemy.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.0);
            ++hitCount;
        }

        attacker.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 0.8F);
    }

    private void handleBuffSkill(LivingEntity attacker, Unit attackerUnit, UnitSkill skill) {
        attackerUnit.setAttackDamage(attackerUnit.getAttackDamage() * skill.getDamageMultiplier());
        attackerUnit.setAttackSpeed(attackerUnit.getAttackSpeed() * 1.5);
        attacker.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, attacker.getLocation().add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.0);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.5F);
    }

    private void handleDebuffSkill(LivingEntity attacker, Map<UUID, LivingEntity> enemies, UnitSkill skill) {
        int hitCount = 0;
        int maxTargets = skill.getMaxTargets();

        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead() && hitCount < maxTargets) {
                Unit enemyUnit = entityToUnit.get(enemy);
                if (enemyUnit != null) {
                    enemyUnit.setAttackDamage(enemyUnit.getAttackDamage() * 0.7);
                    enemyUnit.setArmor(enemyUnit.getArmor() * 0.7);
                    enemy.getWorld().spawnParticle(Particle.SMOKE_NORMAL, enemy.getLocation().add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.05);
                    ++hitCount;
                }
            }
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0F, 0.8F);
    }

    // 회복 스킬로 아군 전체를 치유하고 체력바를 갱신한다.
    private void handleHealSkill(LivingEntity attacker, Unit attackerUnit, UnitSkill skill) {
        Map<UUID, LivingEntity> allies = team1Entities.containsValue(attacker) ? team1Entities : team2Entities;

        for (LivingEntity ally : allies.values()) {
            if (ally != null && !ally.isDead()) {
                Unit allyUnit = entityToUnit.get(ally);
                if (allyUnit != null) {
                    double healAmount = attackerUnit.getAttackDamage() * skill.getDamageMultiplier();
                    applyHealingVirtual(ally, allyUnit, healAmount);
                    boolean isBlueTeam = team1Entities.containsValue(ally);
                    boardManager.updateHealthBar(ally, allyUnit, isBlueTeam);
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0.0, 2.0, 0.0), 5, 0.5, 0.5, 0.5, 0.0);
                }
            }
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.5F);
    }

    private void endCombat(boolean draw) {
        if (!isFinished) {
            isFinished = true;
            if (combatTask != null) {
                combatTask.cancel();
            }

            if (draw) {
                player1.getPlayer().sendMessage("§e§l무승부!");
                if (!isPVE && player2 != null) {
                    player2.getPlayer().sendMessage("§e§l무승부!");
                }
            } else {
                boolean player1Wins = !team1Entities.isEmpty();
                int damage;
                boolean stillAlive;
                GameInstance game;
                if (isPVE) {
                    if (player1Wins) {
                        player1.getPlayer().sendMessage("§a§l승리!");
                        player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                        player1.recordWin();
                    } else {
                        player1.getPlayer().sendMessage("§c§l패배!");
                        player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                        player1.recordLoss();
                        damage = calculateDamage();
                        stillAlive = player1.takeDamage(damage);
                        player1.getPlayer().sendMessage("§c-" + damage + " HP");
                        if (!stillAlive) {
                            game = plugin.getGameManager().getGame(player1.getGameId());
                            if (game != null) {
                                game.eliminatePlayer(player1, "§c체력이 0이 되어 게임에서 탈락했습니다.", "§c{player} §7님이 체력이 0이 되어 탈락했습니다.", true, false);
                            }
                        }
                    }
                } else if (player1Wins) {
                    player1.getPlayer().sendMessage("§a§l승리!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                    player2.getPlayer().sendMessage("§c§l패배!");
                    player2.getPlayer().playSound(player2.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                    player1.recordWin();
                    player2.recordLoss();
                    damage = calculateDamage();
                    stillAlive = player2.takeDamage(damage);
                    player2.getPlayer().sendMessage("§c-" + damage + " HP");
                    if (!stillAlive) {
                        game = plugin.getGameManager().getGame(player2.getGameId());
                        if (game != null) {
                            game.eliminatePlayer(player2, "§c체력이 0이 되어 게임에서 탈락했습니다.", "§c{player} §7님이 전투에서 탈락했습니다.", true, false);
                        }
                    }
                } else {
                    player1.getPlayer().sendMessage("§c§l패배!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
                    player2.getPlayer().sendMessage("§a§l승리!");
                    player2.getPlayer().playSound(player2.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                    player1.recordLoss();
                    player2.recordWin();
                    damage = calculateDamage();
                    stillAlive = player1.takeDamage(damage);
                    player1.getPlayer().sendMessage("§c-" + damage + " HP");
                    if (!stillAlive) {
                        game = plugin.getGameManager().getGame(player1.getGameId());
                        if (game != null) {
                            game.eliminatePlayer(player1, "§c체력이 0이 되어 게임에서 탈락했습니다.", "§c{player} §7님이 전투에서 탈락했습니다.", true, false);
                        }
                    }
                }
            }

            if (completionCallback != null) {
                try {
                    completionCallback.run();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Combat completion callback error", ex);
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getCombatManager().endCombat(combatId);
            }, 40L);
        }
    }

    private void handleRangedAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        double baseDamage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit);
        double damage = applyCriticalStrike(attackerUnit, target, baseDamage);
        Location startLoc = attacker.getEyeLocation();
        Location endLoc = target.getEyeLocation();
        Particle particleType;
        if (attacker instanceof Skeleton) {
            particleType = Particle.CRIT;
        } else if (attacker instanceof Drowned) {
            particleType = Particle.WATER_SPLASH;
        } else if (attacker instanceof Blaze) {
            particleType = Particle.FLAME;
        } else if (attacker instanceof Creeper) {
            particleType = Particle.EXPLOSION_NORMAL;
        } else {
            particleType = Particle.SPELL_WITCH;
        }

        Vector direction = endLoc.toVector().subtract(startLoc.toVector()).normalize();
        double distance = startLoc.distance(endLoc);

        for(double d = 0.0; d < distance; d += 0.5) {
            Location particleLoc = startLoc.clone().add(direction.clone().multiply(d));
            attacker.getWorld().spawnParticle(particleType, particleLoc, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (attacker instanceof Skeleton) {
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8F, 1.0F);
        } else if (attacker instanceof Drowned) {
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.8F, 1.0F);
        } else if (attacker instanceof Blaze) {
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8F, 1.0F);
        } else if (attacker instanceof Creeper) {
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.6F, 1.4F);
        }

        applySkillDamage(target, targetUnit, damage, true);
        attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0.0, 1.0, 0.0), 1, 0.3, 0.3, 0.3, 0.0);
        attackerUnit.addMana(attackerUnit.getManaPerAttack());
        boolean isBlueTeam = team1Entities.containsValue(attacker);
        boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);
    }

    private double applyCriticalStrike(Unit attackerUnit, LivingEntity target, double baseDamage) {
        double critChance = Math.max(0.0, attackerUnit.getCriticalChance() / 100.0);
        if (!plugin.getCombatManager().isCriticalHit(attackerUnit, critChance)) {
            return baseDamage;
        } else {
            double critMultiplier = Math.max(2.0, 1.0 + attackerUnit.getCriticalDamage() / 100.0);
            double critDamage = baseDamage * critMultiplier;
            Location effectLoc = target.getLocation().add(0.0, 1.0, 0.0);
            target.getWorld().spawnParticle(Particle.CRIT, effectLoc, 6, 0.25, 0.3, 0.25, 0.02);
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, effectLoc, 4, 0.25, 0.3, 0.25, 0.01);
            target.getWorld().playSound(effectLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.85F, 1.35F);
            return critDamage;
        }
    }

    private int calculateDamage() {
        int survivingUnits = Math.max(team1Entities.size(), team2Entities.size());
        int baseDamage = 5;
        return baseDamage + survivingUnits * 2;
    }

    public void cleanup() {
        if (combatTask != null) {
            combatTask.cancel();
        }

        boardManager.clearAllUnits();
        team1Entities.clear();
        team2Entities.clear();
        entityToUnit.clear();
        entityBoardPositions.clear();
    }

    public void forfeitPlayer(UUID playerId) {
        if (!isFinished) {
            if (player1.getPlayerId().equals(playerId)) {
                clearTeamEntities(team1Entities);
                endCombat(false);
            } else {
                if (!isPVE && player2 != null && player2.getPlayerId().equals(playerId)) {
                    clearTeamEntities(team2Entities);
                    endCombat(false);
                }

            }
        }
    }

    private void clearTeamEntities(Map<UUID, LivingEntity> team) {
        List<Map.Entry<UUID, LivingEntity>> entries = new ArrayList<>(team.entrySet());

        for (Map.Entry<UUID, LivingEntity> entry : entries) {
            LivingEntity entity = entry.getValue();
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }

            entityToUnit.remove(entity);
            entityBoardPositions.remove(entity);
            dotEffects.remove(entity);
            stunnedEntities.remove(entity);
            lastAttackTick.remove(entry.getKey());
        }

        team.clear();
    }

    public UUID getCombatId() {
        return combatId;
    }

    public GamePlayer getPlayer1() {
        return player1;
    }

    public GamePlayer getPlayer2() {
        return player2;
    }

    public boolean isPVE() {
        return isPVE;
    }

    public int getPveRound() {
        return pveRound;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public BoardInstance getCombatBoard() {
        return combatBoard;
    }

    // 암살자 시너지를 가진 유닛을 시작 시 적 후방으로 순간 이동시킨다.
    private void teleportAssassinsToBackline(boolean isTeam1) {
        if (combatBoard != null) {
            Map<UUID, LivingEntity> assassinTeam = isTeam1 ? team1Entities : team2Entities;
            Map<UUID, LivingEntity> enemyTeam = isTeam1 ? team2Entities : team1Entities;

            for (LivingEntity assassin : assassinTeam.values()) {
                if (assassin == null || assassin.isDead()) {
                    continue;
                }

                Unit unit = entityToUnit.get(assassin);
                if (unit == null || !unit.getTraits().contains(UnitTrait.ASSASSIN)) {
                    continue;
                }

                Position origin = entityBoardPositions.get(assassin);
                Location targetLocation = origin != null ? getAssassinTargetLocation(origin, isTeam1) : null;
                LivingEntity focus = null;
                if (targetLocation == null) {
                    focus = findClosestEnemy(assassin, enemyTeam);
                    if (focus != null) {
                        targetLocation = focus.getLocation().clone();
                    }
                }

                if (targetLocation == null) {
                    continue;
                }

                double offsetX = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
                double offsetZ = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
                targetLocation.add(offsetX, 0.05, offsetZ);
                clampLocationToBoard(targetLocation);
                assassin.getWorld().spawnParticle(Particle.PORTAL, assassin.getLocation().add(0.0, 1.0, 0.0), 28, 0.45, 0.6, 0.45, 0.12);
                assassin.getWorld().playSound(assassin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.45F);
                assassin.teleport(targetLocation);
                assassin.setVelocity(new Vector(0, 0, 0));
                assassin.getWorld().spawnParticle(Particle.PORTAL, assassin.getLocation().add(0.0, 1.0, 0.0), 28, 0.45, 0.6, 0.45, 0.12);
                assassin.getWorld().playSound(assassin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.9F);
                if (focus == null) {
                    focus = findClosestEnemy(assassin, enemyTeam);
                }

                if (focus != null && assassin instanceof Mob mob) {
                    mob.setTarget(focus);
                }
            }
        }
    }

    private Location getAssassinTargetLocation(Position origin, boolean fromTeam1) {
        Location base = combatBoard.getBaseLocation();
        if (base == null) {
            return null;
        } else {
            int column = Math.max(0, Math.min(7, origin.getX()));
            int row = Math.max(0, Math.min(2, origin.getY()));
            double spawnX = base.getX() + (double)(column * 4) + 2.0;
            double spawnY = base.getY() + 1.05;
            double spawnZ;
            double zOffset;
            if (fromTeam1) {
                zOffset = base.getZ() + 12.0;
                spawnZ = zOffset + (double)(row * 4) + 2.0;
            } else {
                zOffset = base.getZ();
                int mirroredRow = 2 - row;
                spawnZ = zOffset + (double)(mirroredRow * 4) + 2.0;
            }

            Location target = new Location(base.getWorld(), spawnX, spawnY, spawnZ);
            clampLocationToBoard(target);
            return target;
        }
    }

    private void clampLocationToBoard(Location location) {
        if (location != null && combatBoard != null) {
            Location corner1 = combatBoard.getCorner1();
            Location corner2 = combatBoard.getCorner2();
            double minX = Math.min(corner1.getX(), corner2.getX()) + 0.5;
            double maxX = Math.max(corner1.getX(), corner2.getX()) - 0.5;
            double minZ = Math.min(corner1.getZ(), corner2.getZ()) + 0.5;
            double maxZ = Math.max(corner1.getZ(), corner2.getZ()) - 0.5;
            double boardY = Math.min(corner1.getY(), corner2.getY()) + 1.0;
            double maxY = boardY + 2.8;
            location.setX(Math.max(minX, Math.min(maxX, location.getX())));
            location.setZ(Math.max(minZ, Math.min(maxZ, location.getZ())));
            location.setY(Math.max(boardY, Math.min(maxY, location.getY())));
        }
    }

    // 지상 유닛이 전장 밖으로 벗어나지 않도록 위치와 속도를 조정한다.
    private void enforceGroundUnitBounds() {
        if (combatBoard != null && !entityToUnit.isEmpty()) {
            Location corner1 = combatBoard.getCorner1();
            Location corner2 = combatBoard.getCorner2();
            double minX = Math.min(corner1.getX(), corner2.getX()) + 0.5;
            double maxX = Math.max(corner1.getX(), corner2.getX()) - 0.5;
            double minZ = Math.min(corner1.getZ(), corner2.getZ()) + 0.5;
            double maxZ = Math.max(corner1.getZ(), corner2.getZ()) - 0.5;
            double boardFloor = Math.min(corner1.getY(), corner2.getY()) + 1.0;
            double maxHeight = boardFloor + 4.0;
            List<LivingEntity> trackedEntities = new ArrayList<>(entityToUnit.keySet());

            for (LivingEntity entity : trackedEntities) {
                if (entity == null || entity.isDead() || entity instanceof Vex) {
                    continue;
                }

                Location current = entity.getLocation();
                if (current.getWorld() == null) {
                    continue;
                }

                double clampedX = Math.max(minX, Math.min(maxX, current.getX()));
                double clampedZ = Math.max(minZ, Math.min(maxZ, current.getZ()));
                double clampedY = Math.max(boardFloor, Math.min(maxHeight, current.getY()));
                boolean adjusted = Math.abs(clampedX - current.getX()) > 0.05
                    || Math.abs(clampedZ - current.getZ()) > 0.05
                    || Math.abs(clampedY - current.getY()) > 0.05;
                if (!adjusted) {
                    continue;
                }

                Location safe = new Location(current.getWorld(), clampedX, clampedY, clampedZ, current.getYaw(), current.getPitch());
                entity.teleport(safe);
                Vector velocity = entity.getVelocity();
                if (velocity == null) {
                    continue;
                }

                if (clampedX <= minX + 0.05 || clampedX >= maxX - 0.05) {
                    velocity.setX(-velocity.getX() * 0.35);
                }

                if (clampedZ <= minZ + 0.05 || clampedZ >= maxZ - 0.05) {
                    velocity.setZ(-velocity.getZ() * 0.35);
                }

                if (clampedY <= boardFloor + 0.02) {
                    velocity.setY(Math.max(0.0, velocity.getY()));
                }

                entity.setVelocity(velocity);
            }
        }
    }

    private record DotEffect(double damagePerTick, int remainingTicks, String effectName) {
    }
}
