package com.matochess.combat;

import com.matochess.MatoChessPlugin;
import com.matochess.core.SynergyManager;
import com.matochess.data.Arena;
import com.matochess.data.GamePlayer;
import com.matochess.data.TraitBonus;
import com.matochess.data.Unit;
import com.matochess.game.BoardManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * 두 플레이어 간 또는 플레이어 대 PVE 간의 단일 전투 인스턴스를 나타냅니다.
 */
public class CombatInstance {

    private final MatoChessPlugin plugin;
    private final UUID combatId;
    private final GamePlayer player1;
    private final GamePlayer player2; // null for PVE
    private final boolean isPVE;
    private final int pveRound;

    private final BoardManager boardManager;
    private final Arena combatArena; // 🚨 새 필드: 전투가 일어날 아레나
    private final Map<LivingEntity, Unit> entityToUnit;
    private final Map<UUID, LivingEntity> team1Entities; // Blue team
    private final Map<UUID, LivingEntity> team2Entities; // Red team

    // DOT (Damage Over Time) 관리
    private final Map<LivingEntity, DotEffect> dotEffects; // 지속 피해 효과

    // CC (Crowd Control) 관리
    private final Map<LivingEntity, Integer> stunnedEntities; // 스턴 효과 (틱 카운트)

    private BukkitTask combatTask;
    private boolean isFinished;
    private int tickCount;

    /**
     * DOT 효과 데이터 클래스
     */
    private static class DotEffect {
        final double damagePerTick;
        final int remainingTicks;
        final String effectName;

        DotEffect(double damagePerTick, int remainingTicks, String effectName) {
            this.damagePerTick = damagePerTick;
            this.remainingTicks = remainingTicks;
            this.effectName = effectName;
        }
    }

    /**
     * Constructor for PVP combat
     */
    public CombatInstance(MatoChessPlugin plugin, UUID combatId, GamePlayer player1, GamePlayer player2, Arena combatArena) {
        this.plugin = plugin;
        this.combatId = combatId;
        this.player1 = player1;
        this.player2 = player2;
        this.combatArena = combatArena;
        this.isPVE = false;
        this.pveRound = 0;
        this.isFinished = false;
        this.tickCount = 0;

        this.boardManager = new BoardManager(plugin);
        this.entityToUnit = new HashMap<>();
        this.team1Entities = new HashMap<>();
        this.team2Entities = new HashMap<>();
        this.dotEffects = new HashMap<>();
        this.stunnedEntities = new HashMap<>();
    }

    /**
     * Constructor for PVE combat
     */
    public CombatInstance(MatoChessPlugin plugin, UUID combatId, GamePlayer player, int round, Arena combatArena) {
        this.plugin = plugin;
        this.combatId = combatId;
        this.player1 = player;
        this.combatArena = combatArena;
        this.player2 = null;
        this.isPVE = true;
        this.pveRound = round;
        this.isFinished = false;
        this.tickCount = 0;

        this.boardManager = new BoardManager(plugin);
        this.entityToUnit = new HashMap<>();
        this.team1Entities = new HashMap<>();
        this.team2Entities = new HashMap<>();
        this.dotEffects = new HashMap<>();
        this.stunnedEntities = new HashMap<>();
    }

    /**
     * 전투 시작
     */
    public void start() {
        teleportPlayersToCombatArena();

        player1.getPlayer().sendMessage("§c§l전투 시작!");
        player1.getPlayer().playSound(player1.getPlayer().getLocation(),
            org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        if (!isPVE && player2 != null) {
            player2.getPlayer().sendMessage("§c§l전투 시작!");
            player2.getPlayer().playSound(player2.getPlayer().getLocation(),
                org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }

        // Spawn units for both teams
        spawnUnits();

        // Apply synergy bonuses
        applySynergyBonuses();

        // Combat start visual effects
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnCombatStartEffects, 10L);

        // Start combat simulation (runs every 0.5 seconds = 10 ticks)
        combatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);

        // Auto-end after max time
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isFinished) {
                endCombat(true); // Draw
            }
        }, plugin.getConfig().getInt("game.combat-time", 60) * 20L);
    }

    /**
     * 플레이어를 전투 아레나로 텔레포트
     * 보드판 위에 평범하게 스폰 (걸어다닐 수 있음)
     */
    private void teleportPlayersToCombatArena() {
        // player1 텔레포트
        teleportSinglePlayerToCombatArena(player1);

        // player2 텔레포트 (PVP일 경우)
        if (!isPVE && player2 != null) {
            teleportSinglePlayerToCombatArena(player2);
        }
    }

    private void teleportSinglePlayerToCombatArena(GamePlayer gp) {
        org.bukkit.entity.Player player = gp.getPlayer();
        if (player == null || !player.isOnline() || combatArena == null) return;

        Location pos1 = combatArena.getPos1();
        Location pos2 = combatArena.getPos2();

        // 32x24 보드판의 중심 좌표 계산
        double centerX = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;
        double boardY = Math.min(pos1.getY(), pos2.getY());

        // 보드판 위에 스폰 (보드판 표면 + 1블록)
        Location spawnPos = new Location(pos1.getWorld(), centerX, boardY + 1, centerZ);

        // Adventure 모드로 설정 및 이동 가능하도록 설정
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setWalkSpeed(0.2f); // 기본 걷기 속도
        player.setFlySpeed(0.1f); // 기본 날기 속도
        player.setAllowFlight(false);
        player.setFlying(false);

        player.teleport(spawnPos);
        plugin.getLogger().info("Player " + player.getName() + " teleported to combat arena at " +
                               String.format("%.1f, %.1f, %.1f", centerX, boardY + 1, centerZ));
    }

    /**
     * Spawn combat start visual effects
     */
    private void spawnCombatStartEffects() {
        // Create dramatic effect at the center of the battlefield
        for (LivingEntity entity : team1Entities.values()) {
            if (entity != null && !entity.isDead()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                    entity.getLocation().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
                entity.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                    entity.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.5);
            }
        }

        for (LivingEntity entity : team2Entities.values()) {
            if (entity != null && !entity.isDead()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME,
                    entity.getLocation().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
                entity.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                    entity.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.5);
            }
        }
    }

    /**
     * Spawn all units for both teams
     */
    private void spawnUnits() {
        // Spawn team 1 (blue team)
        player1.getBoard().forEach((position, unit) -> {
            LivingEntity entity = boardManager.spawnUnit(unit, position, true, combatArena);
            if (entity != null) {
                team1Entities.put(unit.getInstanceId(), entity);
                entityToUnit.put(entity, unit);
            }
        });

        // Spawn team 2 (red team) or PVE monsters
        if (!isPVE && player2 != null) {
            player2.getBoard().forEach((position, unit) -> {
                LivingEntity entity = boardManager.spawnUnit(unit, position, false, combatArena);
                if (entity != null) {
                    team2Entities.put(unit.getInstanceId(), entity);
                    entityToUnit.put(entity, unit);
                }
            });
        } else {
            // Spawn PVE monsters
            spawnPVEMonsters();
        }
    }

    /**
     * Apply synergy bonuses to all units
     */
    private void applySynergyBonuses() {
        SynergyManager synergyManager = new SynergyManager();

        // Calculate synergies for team 1
        List<Unit> team1Units = new ArrayList<>();
        for (LivingEntity entity : team1Entities.values()) {
            Unit unit = entityToUnit.get(entity);
            if (unit != null) {
                team1Units.add(unit);
            }
        }

        if (!team1Units.isEmpty()) {
            Map<com.matochess.data.UnitTrait, SynergyManager.ActiveSynergy> team1Synergies =
                synergyManager.calculateSynergies(team1Units);

            // Apply bonuses to team 1 entities
            for (LivingEntity entity : team1Entities.values()) {
                Unit unit = entityToUnit.get(entity);
                if (unit != null) {
                    applyBonusesToEntity(entity, unit, team1Synergies);
                }
            }

            // Notify player of active synergies
            if (!team1Synergies.isEmpty()) {
                player1.getPlayer().sendMessage("§a§l활성 시너지:");
                for (SynergyManager.ActiveSynergy synergy : team1Synergies.values()) {
                    player1.getPlayer().sendMessage("§e" + synergy.getDisplayString());
                }

                // Synergy activation particles
                for (LivingEntity entity : team1Entities.values()) {
                    if (entity != null && !entity.isDead()) {
                        entity.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                            entity.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                        entity.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                            entity.getLocation().add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.05);
                    }
                }
            }
        }

        // Calculate synergies for team 2 (if PVP)
        if (!isPVE && player2 != null) {
            List<Unit> team2Units = new ArrayList<>();
            for (LivingEntity entity : team2Entities.values()) {
                Unit unit = entityToUnit.get(entity);
                if (unit != null) {
                    team2Units.add(unit);
                }
            }

            if (!team2Units.isEmpty()) {
                Map<com.matochess.data.UnitTrait, SynergyManager.ActiveSynergy> team2Synergies =
                    synergyManager.calculateSynergies(team2Units);

                // Apply bonuses to team 2 entities
                for (LivingEntity entity : team2Entities.values()) {
                    Unit unit = entityToUnit.get(entity);
                    if (unit != null) {
                        applyBonusesToEntity(entity, unit, team2Synergies);
                    }
                }

                // Notify player of active synergies
                if (!team2Synergies.isEmpty()) {
                    player2.getPlayer().sendMessage("§a§l활성 시너지:");
                    for (SynergyManager.ActiveSynergy synergy : team2Synergies.values()) {
                        player2.getPlayer().sendMessage("§e" + synergy.getDisplayString());
                    }

                    // Synergy activation particles
                    for (LivingEntity entity : team2Entities.values()) {
                        if (entity != null && !entity.isDead()) {
                            entity.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                                entity.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                            entity.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                                entity.getLocation().add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.05);
                        }
                    }
                }
            }
        }
    }

    /**
     * Apply synergy bonuses to a single entity
     */
    private void applyBonusesToEntity(LivingEntity entity, Unit unit,
                                     Map<com.matochess.data.UnitTrait, SynergyManager.ActiveSynergy> synergies) {
        for (com.matochess.data.UnitTrait trait : unit.getTraits()) {
            SynergyManager.ActiveSynergy synergy = synergies.get(trait);
            if (synergy != null) {
                TraitBonus bonus = synergy.getActiveBonus();
                applyBonus(entity, unit, bonus);
            }
        }
    }

    /**
     * Apply a single bonus to entity
     */
    private void applyBonus(LivingEntity entity, Unit unit, TraitBonus bonus) {
        double value = bonus.getBonusValue();

        switch (bonus.getBonusType()) {
            case HEALTH_FLAT:
                entity.setMaxHealth(entity.getMaxHealth() + value);
                entity.setHealth(entity.getMaxHealth());
                break;
            case HEALTH_PERCENT:
                double healthMultiplier = 1.0 + (value / 100.0);
                entity.setMaxHealth(entity.getMaxHealth() * healthMultiplier);
                entity.setHealth(entity.getMaxHealth());
                break;
            case ATTACK_DAMAGE_FLAT:
                unit.setAttackDamage(unit.getAttackDamage() + value);
                break;
            case ATTACK_DAMAGE_PERCENT:
                unit.setAttackDamage(unit.getAttackDamage() * (1.0 + value / 100.0));
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
                break;
            // Note: Some bonuses like HEAL_PER_SECOND, DODGE_CHANCE, etc.
            // would need to be handled during combat ticks
            default:
                // Store bonus for runtime application
                break;
        }
    }

    /**
     * Spawn PVE monsters based on round
     */
    private void spawnPVEMonsters() {
        var monsterRegistry = plugin.getMonsterRegistry();
        var wave = monsterRegistry.getWaveForRound(pveRound);

        if (wave == null) {
            plugin.getLogger().warning("No monster wave found for round " + pveRound);
            return;
        }

        List<Unit> monsters = wave.getMonsters();
        plugin.getLogger().info("Spawning " + monsters.size() + " monsters for round " + pveRound);

        // Spawn monsters on red team side (facing the player's units)
        int gridSize = 8;
        int monstersPerRow = Math.min(4, monsters.size()); // Max 4 per row

        for (int i = 0; i < monsters.size(); i++) {
            Unit monster = monsters.get(i);

            // Calculate position in grid (spread across the back rows)
            int row = i / monstersPerRow;
            int col = (i % monstersPerRow) * 2 + 1; // Spread out: positions 1, 3, 5, 7

            com.matochess.data.Position position = new com.matochess.data.Position(col, row);

            LivingEntity entity = boardManager.spawnUnit(monster, position, false, combatArena); // Red team side
            if (entity != null) {
                team2Entities.put(monster.getInstanceId(), entity);
                entityToUnit.put(entity, monster);
            }
        }

        player1.getPlayer().sendMessage("§c" + wave.getMonsterCount() + "마리의 몬스터가 나타났습니다!");
    }

    /**
     * Combat tick (runs every 0.5 seconds)
     */
    private void tick() {
        if (isFinished) {
            return;
        }

        tickCount++;

        // Process DOT effects
        processDotEffects();

        // Process CC effects
        processCCEffects();

        // Remove dead entities
        removeDeadEntities();

        // Check for combat end
        if (team1Entities.isEmpty() || team2Entities.isEmpty()) {
            endCombat(false);
            return;
        }

        // Perform combat AI for each entity
        performCombatAI();
    }

    /**
     * Process DOT (Damage Over Time) effects
     */
    private void processDotEffects() {
        List<LivingEntity> toRemove = new ArrayList<>();

        for (Map.Entry<LivingEntity, DotEffect> entry : new HashMap<>(dotEffects).entrySet()) {
            LivingEntity entity = entry.getKey();
            DotEffect dot = entry.getValue();

            if (entity == null || entity.isDead()) {
                toRemove.add(entity);
                continue;
            }

            // Apply DOT damage
            double newHealth = entity.getHealth() - dot.damagePerTick;
            if (newHealth <= 0) {
                entity.setHealth(0);
                entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                    entity.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                entity.remove();
                toRemove.add(entity);
            } else {
                entity.setHealth(newHealth);

                // DOT 파티클 (독=초록, 화상=빨강, 위더=검정)
                org.bukkit.Particle particle = org.bukkit.Particle.VILLAGER_ANGRY;
                if (dot.effectName.contains("독") || dot.effectName.contains("poison")) {
                    particle = org.bukkit.Particle.SLIME;
                } else if (dot.effectName.contains("화염") || dot.effectName.contains("fire")) {
                    particle = org.bukkit.Particle.FLAME;
                } else if (dot.effectName.contains("위더") || dot.effectName.contains("wither")) {
                    particle = org.bukkit.Particle.SMOKE_LARGE;
                }

                entity.getWorld().spawnParticle(particle,
                    entity.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);

                // Update health bar
                Unit unit = entityToUnit.get(entity);
                if (unit != null) {
                    boolean isBlueTeam = team1Entities.containsValue(entity);
                    boardManager.updateHealthBar(entity, unit, isBlueTeam);
                }
            }

            // Decrease remaining ticks
            int newTicks = dot.remainingTicks - 1;
            if (newTicks <= 0) {
                toRemove.add(entity);
            } else {
                dotEffects.put(entity, new DotEffect(dot.damagePerTick, newTicks, dot.effectName));
            }
        }

        // Remove expired effects
        for (LivingEntity entity : toRemove) {
            dotEffects.remove(entity);
        }
    }

    /**
     * Process CC (Crowd Control) effects
     */
    private void processCCEffects() {
        List<LivingEntity> toRemove = new ArrayList<>();

        for (Map.Entry<LivingEntity, Integer> entry : new HashMap<>(stunnedEntities).entrySet()) {
            LivingEntity entity = entry.getKey();
            int remainingTicks = entry.getValue();

            if (entity == null || entity.isDead()) {
                toRemove.add(entity);
                continue;
            }

            // 스턴 파티클
            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                entity.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);

            // Decrease remaining ticks
            remainingTicks--;
            if (remainingTicks <= 0) {
                toRemove.add(entity);
                // 스턴 해제 이펙트
                entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL,
                    entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            } else {
                stunnedEntities.put(entity, remainingTicks);
            }
        }

        // Remove expired effects
        for (LivingEntity entity : toRemove) {
            stunnedEntities.remove(entity);
        }
    }

    /**
     * Remove dead entities from tracking
     */
    private void removeDeadEntities() {
        team1Entities.values().removeIf(entity -> entity == null || entity.isDead());
        team2Entities.values().removeIf(entity -> entity == null || entity.isDead());
        entityToUnit.keySet().removeIf(entity -> entity == null || entity.isDead());
    }

    /**
     * Perform combat AI for all entities
     */
    private void performCombatAI() {
        // Team 1 attacks Team 2
        for (LivingEntity attacker : new ArrayList<>(team1Entities.values())) {
            if (attacker != null && !attacker.isDead()) {
                performEntityAction(attacker, team2Entities);
            }
        }

        // Team 2 attacks Team 1
        for (LivingEntity attacker : new ArrayList<>(team2Entities.values())) {
            if (attacker != null && !attacker.isDead()) {
                performEntityAction(attacker, team1Entities);
            }
        }
    }

    /**
     * Perform action for a single entity (AI 기반 자연스러운 움직임)
     */
    private void performEntityAction(LivingEntity attacker, Map<UUID, LivingEntity> enemies) {
        Unit attackerUnit = entityToUnit.get(attacker);
        if (attackerUnit == null) {
            return;
        }

        // 스턴 상태 체크 - 스턴되어 있으면 행동 불가
        if (stunnedEntities.containsKey(attacker)) {
            return;
        }

        // Find closest enemy
        LivingEntity target = findClosestEnemy(attacker, enemies);
        if (target == null) {
            return;
        }

        Unit targetUnit = entityToUnit.get(target);
        if (targetUnit == null) {
            return;
        }

        // AI 기반 타겟 설정 (Mob 엔티티만 해당)
        if (attacker instanceof Mob) {
            Mob mob = (Mob) attacker;

            // 중요: 현재 타겟이 적 팀인지 확인
            LivingEntity currentTarget = mob.getTarget();
            boolean needsNewTarget = false;

            if (currentTarget != null) {
                // 타겟이 죽었거나 같은 팀이면 새 타겟 필요
                if (currentTarget.isDead()) {
                    needsNewTarget = true;
                } else {
                    boolean attackerIsTeam1 = team1Entities.containsValue(attacker);
                    boolean targetIsTeam1 = team1Entities.containsValue(currentTarget);

                    if (attackerIsTeam1 == targetIsTeam1) {
                        // 같은 팀이면 타겟 해제하고 새 타겟 필요
                        mob.setTarget(null);
                        needsNewTarget = true;
                    }
                }
            } else {
                // 타겟이 없으면 새 타겟 필요
                needsNewTarget = true;
            }

            // 새 타겟이 필요하거나 더 가까운 적이 있으면 타겟 재설정
            if (needsNewTarget || currentTarget != target) {
                mob.setTarget(target);

                // 타겟 설정 강제 (AI가 확실히 인식하도록)
                if (mob.getTarget() == null || mob.getTarget().isDead()) {
                    // 타겟이 제대로 설정되지 않았다면 재시도
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!mob.isDead() && !target.isDead()) {
                            mob.setTarget(target);
                        }
                    }, 1L);
                }
            }

            // Follow Range를 매우 크게 설정 (타겟을 절대 놓치지 않도록)
            if (attacker.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
                attacker.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(128.0);
            }

            // 이동 속도 설정 (attackSpeed 기반, 최소 속도 보장)
            double speedMultiplier = Math.min(attackerUnit.getAttackSpeed() / 100.0, 2.0); // 최대 2배속
            speedMultiplier = Math.max(speedMultiplier, 0.5); // 최소 0.5배속 보장
            if (attacker.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                double baseSpeed = 0.3; // 기본 이동 속도 (바닐라보다 약간 빠름)
                attacker.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(baseSpeed * speedMultiplier);
            }
        }

        double distance = attacker.getLocation().distance(target.getLocation());

        // 원거리 유닛 확인 (스켈레톤, 드라운드 등)
        boolean isRangedUnit = attacker instanceof org.bukkit.entity.Skeleton ||
                               attacker instanceof org.bukkit.entity.Drowned ||
                               attacker instanceof org.bukkit.entity.Witch ||
                               attacker instanceof org.bukkit.entity.Pillager ||
                               attacker instanceof org.bukkit.entity.Blaze;

        // 공격 범위 설정 (원거리는 더 넓음)
        double attackRange = isRangedUnit ? 15.0 : 3.0;

        // 공격 범위 내에 있으면 공격
        if (distance <= attackRange) {
            // 스킬 발동 체크 (마나 100 이상)
            if (attackerUnit.canCastSkill()) {
                castSkill(attacker, attackerUnit, target, targetUnit, enemies);
                return;
            }

            // 크리퍼 특수 처리: 폭발 공격 (광역 데미지, 자신은 체력 소모 없음)
            if (attacker instanceof org.bukkit.entity.Creeper) {
                handleCreeperExplosion(attacker, attackerUnit, enemies);
                return;
            }

            // 원거리 유닛 처리: 커스텀 발사체 이펙트
            if (isRangedUnit) {
                handleRangedAttack(attacker, attackerUnit, target, targetUnit);
                return;
            }

            // 근접 Attack
            double damage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit);

            // Check for critical hit
            if (plugin.getCombatManager().isCriticalHit(attackerUnit, attackerUnit.getCriticalChance() / 100.0)) {
                damage *= (1.0 + attackerUnit.getCriticalDamage() / 100.0);
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 5);
                target.getWorld().playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.2f);
            }

            // Apply damage
            double newHealth = target.getHealth() - damage;
            if (newHealth <= 0) {
                target.setHealth(0);

                // Death particles
                target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL,
                    target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                target.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                    target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);

                // Death sound
                target.getWorld().playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_GENERIC_DEATH, 0.8f, 0.9f);

                target.remove();
            } else {
                target.setHealth(newHealth);

                // 체력바 업데이트 (팀 색상 유지)
                boolean isBlueTeam = team1Entities.containsValue(target);
                boardManager.updateHealthBar(target, targetUnit, isBlueTeam);

                // Damage particles
                target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0);

                // Damage sound
                target.getWorld().playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.4f, 1.0f);
            }

            // 공격 성공 시 마나 증가
            attackerUnit.addMana(attackerUnit.getManaPerAttack());

            // Attack particles
            attacker.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                attacker.getLocation().add(0, 1, 0), 1);

            // Attack sound
            attacker.getWorld().playSound(attacker.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.0f);

            // Apply life steal
            if (attackerUnit.getLifeSteal() > 0) {
                double heal = damage * (attackerUnit.getLifeSteal() / 100.0);
                double newAttackerHealth = Math.min(attacker.getHealth() + heal, attacker.getMaxHealth());
                attacker.setHealth(newAttackerHealth);

                // 체력바 업데이트 (팀 색상 유지)
                boolean isBlueTeam = team1Entities.containsValue(attacker);
                boardManager.updateHealthBar(attacker, attackerUnit, isBlueTeam);

                // Heal particles
                if (heal > 0) {
                    attacker.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                        attacker.getLocation().add(0, 2, 0), 2, 0.3, 0.3, 0.3, 0);
                    attacker.getWorld().playSound(attacker.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
                }
            }
        }
        // AI가 자동으로 타겟을 향해 이동하므로 별도의 이동 로직 불필요
    }

    /**
     * Find the closest enemy to attack
     */
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

    /**
     * Cast a unit's skill
     */
    private void castSkill(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit, Map<UUID, LivingEntity> enemies) {
        com.matochess.data.UnitSkill skill = attackerUnit.getSkill();
        if (skill == null) {
            return;
        }

        // 마나 소모 및 초기화
        attackerUnit.resetMana();

        // 스킬 발동 이펙트
        attacker.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH,
            attacker.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        attacker.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
            attacker.getLocation().add(0, 2, 0), 20, 0.5, 1.0, 0.5, 0.5);
        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.2f);

        // 스킬 타입에 따라 처리
        switch (skill.getType()) {
            case DAMAGE:
            case BACKSTAB:
                // 단일 대상 스킬 데미지
                handleSingleTargetSkill(attacker, attackerUnit, target, targetUnit, skill);
                break;

            case AOE_DAMAGE:
                // 광역 스킬 데미지
                handleAOESkill(attacker, attackerUnit, enemies, skill);
                break;

            case BUFF:
                // 자신 강화
                handleBuffSkill(attacker, attackerUnit, skill);
                break;

            case DEBUFF:
                // 적 약화
                handleDebuffSkill(attacker, enemies, skill);
                break;

            case HEAL:
                // 아군 힐
                handleHealSkill(attacker, attackerUnit, skill);
                break;

            case SUMMON:
                // 소환 스킬
                handleSummonSkill(attacker, attackerUnit, skill);
                break;

            case TELEPORT:
                // 순간이동 후 공격
                handleTeleportSkill(attacker, attackerUnit, enemies, skill);
                break;

            default:
                break;
        }
    }

    /**
     * Handle summon skill
     */
    private void handleSummonSkill(LivingEntity attacker, Unit attackerUnit, com.matochess.data.UnitSkill skill) {
        // 소환 이펙트
        attacker.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
            attacker.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.5);
        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 1.0f);

        // 소환된 유닛은 임시로 작은 vex 생성 (3마리)
        Location summonLoc = attacker.getLocation();
        boolean isTeam1 = team1Entities.containsValue(attacker);
        Map<UUID, LivingEntity> summonerTeam = isTeam1 ? team1Entities : team2Entities;

        for (int i = 0; i < 3; i++) {
            // 소환 위치 (소환사 주변)
            double angle = (i * 120) * Math.PI / 180.0;
            Location spawnLoc = summonLoc.clone().add(
                Math.cos(angle) * 2,
                0,
                Math.sin(angle) * 2
            );

            // Vex 소환
            org.bukkit.entity.Vex vex = (org.bukkit.entity.Vex) summonLoc.getWorld().spawnEntity(
                spawnLoc, org.bukkit.entity.EntityType.VEX);

            // 소환된 유닛 설정
            vex.setCustomName("§e소환된 정령");
            vex.setCustomNameVisible(true);
            vex.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(50);
            vex.setHealth(50);

            // 팀에 추가
            summonerTeam.put(UUID.randomUUID(), vex);

            // 소환 파티클
            vex.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL,
                vex.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
        }

        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.5f);
    }

    /**
     * Handle teleport skill (backstab)
     */
    private void handleTeleportSkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, com.matochess.data.UnitSkill skill) {
        // 후방의 적 찾기 (체력이 가장 낮은 적)
        LivingEntity backlineTarget = null;
        double lowestHealth = Double.MAX_VALUE;

        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead()) {
                if (enemy.getHealth() < lowestHealth) {
                    lowestHealth = enemy.getHealth();
                    backlineTarget = enemy;
                }
            }
        }

        if (backlineTarget == null) {
            return;
        }

        // 텔레포트 전 위치에 파티클
        attacker.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
            attacker.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        // 적 뒤로 텔레포트
        Location targetLoc = backlineTarget.getLocation();
        Location teleportLoc = targetLoc.clone().add(
            targetLoc.getDirection().multiply(-2)
        );
        teleportLoc.setY(targetLoc.getY());
        attacker.teleport(teleportLoc);

        // 텔레포트 후 파티클
        attacker.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
            attacker.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // 암살 데미지
        Unit targetUnit = entityToUnit.get(backlineTarget);
        if (targetUnit != null) {
            double skillDamage = plugin.getCombatManager().calculateSkillDamage(
                attackerUnit, targetUnit, skill.getDamageMultiplier());

            double newHealth = backlineTarget.getHealth() - skillDamage;
            if (newHealth <= 0) {
                backlineTarget.setHealth(0);
                backlineTarget.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                    backlineTarget.getLocation().add(0, 1, 0), 1);
                backlineTarget.remove();
            } else {
                backlineTarget.setHealth(newHealth);

                // 체력바 업데이트
                boolean isBlueTeam = team1Entities.containsValue(backlineTarget);
                boardManager.updateHealthBar(backlineTarget, targetUnit, isBlueTeam);
            }

            // 암살 이펙트
            backlineTarget.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                backlineTarget.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
            backlineTarget.getWorld().playSound(backlineTarget.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        }
    }

    /**
     * Handle single target skill
     */
    private void handleSingleTargetSkill(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit, com.matochess.data.UnitSkill skill) {
        double skillDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, targetUnit, skill.getDamageMultiplier());

        // 스킬 데미지 적용
        double newHealth = target.getHealth() - skillDamage;
        if (newHealth <= 0) {
            target.setHealth(0);
            target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                target.getLocation().add(0, 1, 0), 1);
            target.remove();
        } else {
            target.setHealth(newHealth);

            // 체력바 업데이트
            boolean isBlueTeam = team1Entities.containsValue(target);
            boardManager.updateHealthBar(target, targetUnit, isBlueTeam);

            // DOT 효과 적용
            if (skill.appliesDot()) {
                double dotDamage = skillDamage * 0.2; // 스킬 데미지의 20%를 지속 피해로
                dotEffects.put(target, new DotEffect(dotDamage, 6, skill.getName())); // 3초간 지속 (6틱)

                // DOT 시작 이펙트
                target.getWorld().spawnParticle(org.bukkit.Particle.DRIP_LAVA,
                    target.getLocation().add(0, 2, 0), 10, 0.3, 0.5, 0.3, 0);
            }

            // 스턴 효과 적용
            if (skill.appliesStun()) {
                stunnedEntities.put(target, 4); // 2초간 스턴 (4틱)

                // 스턴 시작 이펙트
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    target.getLocation().add(0, 2, 0), 15, 0.3, 0.5, 0.3, 0);
                target.getWorld().playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.5f);
            }
        }

        // 스킬 히트 이펙트
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC,
            target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        target.getWorld().playSound(target.getLocation(),
            org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
    }

    /**
     * Handle AOE skill
     */
    private void handleAOESkill(LivingEntity attacker, Unit attackerUnit, Map<UUID, LivingEntity> enemies, com.matochess.data.UnitSkill skill) {
        Location center = attacker.getLocation();
        double radius = skill.getEffectRadius();
        int hitCount = 0;
        int maxTargets = skill.getMaxTargets();

        // 광역 이펙트
        for (int i = 0; i < 360; i += 30) {
            double radian = Math.toRadians(i);
            Location particleLoc = center.clone().add(
                Math.cos(radian) * radius,
                0.5,
                Math.sin(radian) * radius
            );
            attacker.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
        }

        // 범위 내 적들에게 데미지
        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead() && hitCount < maxTargets) {
                double distance = enemy.getLocation().distance(center);
                if (distance <= radius) {
                    Unit enemyUnit = entityToUnit.get(enemy);
                    if (enemyUnit != null) {
                        double skillDamage = plugin.getCombatManager().calculateSkillDamage(attackerUnit, enemyUnit, skill.getDamageMultiplier());

                        double newHealth = enemy.getHealth() - skillDamage;
                        if (newHealth <= 0) {
                            enemy.setHealth(0);
                            enemy.remove();
                        } else {
                            enemy.setHealth(newHealth);

                            // 체력바 업데이트
                            boolean isBlueTeam = team1Entities.containsValue(enemy);
                            boardManager.updateHealthBar(enemy, enemyUnit, isBlueTeam);
                        }

                        // 히트 이펙트
                        enemy.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                            enemy.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0);

                        // DOT 효과 적용
                        if (skill.appliesDot()) {
                            double dotDamage = skillDamage * 0.15; // AOE는 DOT 15%
                            dotEffects.put(enemy, new DotEffect(dotDamage, 6, skill.getName()));
                        }

                        // 스턴 효과 적용
                        if (skill.appliesStun()) {
                            stunnedEntities.put(enemy, 3); // AOE는 1.5초 스턴 (3틱)

                            // 스턴 이펙트
                            enemy.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                                enemy.getLocation().add(0, 2, 0), 10, 0.3, 0.5, 0.3, 0);
                        }

                        hitCount++;
                    }
                }
            }
        }

        // 광역 사운드
        attacker.getWorld().playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
    }

    /**
     * Handle buff skill
     */
    private void handleBuffSkill(LivingEntity attacker, Unit attackerUnit, com.matochess.data.UnitSkill skill) {
        // 공격력 및 공격속도 증가 (임시)
        attackerUnit.setAttackDamage(attackerUnit.getAttackDamage() * skill.getDamageMultiplier());
        attackerUnit.setAttackSpeed(attackerUnit.getAttackSpeed() * 1.5);

        // 버프 이펙트
        attacker.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
            attacker.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5, 0);
        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    /**
     * Handle debuff skill
     */
    private void handleDebuffSkill(LivingEntity attacker, Map<UUID, LivingEntity> enemies, com.matochess.data.UnitSkill skill) {
        int hitCount = 0;
        int maxTargets = skill.getMaxTargets();

        for (LivingEntity enemy : enemies.values()) {
            if (enemy != null && !enemy.isDead() && hitCount < maxTargets) {
                Unit enemyUnit = entityToUnit.get(enemy);
                if (enemyUnit != null) {
                    // 공격력 및 방어력 감소 (임시)
                    enemyUnit.setAttackDamage(enemyUnit.getAttackDamage() * 0.7);
                    enemyUnit.setArmor(enemyUnit.getArmor() * 0.7);

                    // 디버프 이펙트
                    enemy.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL,
                        enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);

                    hitCount++;
                }
            }
        }

        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.8f);
    }

    /**
     * Handle heal skill
     */
    private void handleHealSkill(LivingEntity attacker, Unit attackerUnit, com.matochess.data.UnitSkill skill) {
        // 아군 찾기 (같은 팀)
        Map<UUID, LivingEntity> allies = team1Entities.containsValue(attacker) ? team1Entities : team2Entities;

        for (LivingEntity ally : allies.values()) {
            if (ally != null && !ally.isDead()) {
                Unit allyUnit = entityToUnit.get(ally);
                if (allyUnit != null) {
                    double healAmount = attackerUnit.getAttackDamage() * skill.getDamageMultiplier();
                    double newHealth = Math.min(ally.getHealth() + healAmount, ally.getMaxHealth());
                    ally.setHealth(newHealth);

                    // 체력바 업데이트
                    boolean isBlueTeam = team1Entities.containsValue(ally);
                    boardManager.updateHealthBar(ally, allyUnit, isBlueTeam);

                    // 힐 이펙트
                    ally.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                        ally.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5, 0);
                }
            }
        }

        attacker.getWorld().playSound(attacker.getLocation(),
            org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    /**
     * End the combat
     */
    private void endCombat(boolean draw) {
        if (isFinished) {
            return;
        }

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
            // 생존한 엔티티 기반으로 승자 결정
            boolean player1Wins = !team1Entities.isEmpty();

            if (isPVE) {
                if (player1Wins) {
                    player1.getPlayer().sendMessage("§a§l승리!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player1.recordWin();
                } else {
                    player1.getPlayer().sendMessage("§c§l패배!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(),
                        org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                    player1.recordLoss();
                    int damage = calculateDamage();
                    player1.takeDamage(damage);
                    player1.getPlayer().sendMessage("§c-" + damage + " HP");
                }
            } else {
                if (player1Wins) {
                    player1.getPlayer().sendMessage("§a§l승리!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player2.getPlayer().sendMessage("§c§l패배!");
                    player2.getPlayer().playSound(player2.getPlayer().getLocation(),
                        org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                    player1.recordWin();
                    player2.recordLoss();
                    int damage = calculateDamage();
                    player2.takeDamage(damage);
                    player2.getPlayer().sendMessage("§c-" + damage + " HP");
                } else {
                    player1.getPlayer().sendMessage("§c§l패배!");
                    player1.getPlayer().playSound(player1.getPlayer().getLocation(),
                        org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                    player2.getPlayer().sendMessage("§a§l승리!");
                    player2.getPlayer().playSound(player2.getPlayer().getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player1.recordLoss();
                    player2.recordWin();
                    int damage = calculateDamage();
                    player1.takeDamage(damage);
                    player1.getPlayer().sendMessage("§c-" + damage + " HP");
                }
            }
        }

        // Cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            plugin.getCombatManager().endCombat(combatId), 40L);
    }

    /**
     * 원거리 공격 처리 (커스텀 발사체 이펙트, 유닛 스탯 기반 데미지)
     */
    private void handleRangedAttack(LivingEntity attacker, Unit attackerUnit, LivingEntity target, Unit targetUnit) {
        // 유닛 스탯 기반 데미지 계산
        double damage = plugin.getCombatManager().calculateDamage(attackerUnit, targetUnit);

        // 크리티컬 체크
        if (plugin.getCombatManager().isCriticalHit(attackerUnit, attackerUnit.getCriticalChance() / 100.0)) {
            damage *= (1.0 + attackerUnit.getCriticalDamage() / 100.0);
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 5);
            target.getWorld().playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.2f);
        }

        // 커스텀 발사체 이펙트 (파티클로 시각화)
        Location startLoc = attacker.getEyeLocation();
        Location endLoc = target.getEyeLocation();

        // 발사체 타입에 따라 다른 파티클
        org.bukkit.Particle particleType;
        if (attacker instanceof org.bukkit.entity.Skeleton) {
            particleType = org.bukkit.Particle.CRIT; // 화살 이펙트
        } else if (attacker instanceof org.bukkit.entity.Drowned) {
            particleType = org.bukkit.Particle.WATER_SPLASH; // 삼지창 이펙트
        } else if (attacker instanceof org.bukkit.entity.Blaze) {
            particleType = org.bukkit.Particle.FLAME; // 불 이펙트
        } else {
            particleType = org.bukkit.Particle.SPELL_WITCH; // 기본 마법 이펙트
        }

        // 발사체 경로 파티클 생성
        org.bukkit.util.Vector direction = endLoc.toVector().subtract(startLoc.toVector()).normalize();
        double distance = startLoc.distance(endLoc);
        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = startLoc.clone().add(direction.clone().multiply(d));
            attacker.getWorld().spawnParticle(particleType, particleLoc, 1, 0, 0, 0, 0);
        }

        // 발사 사운드
        if (attacker instanceof org.bukkit.entity.Skeleton) {
            attacker.getWorld().playSound(attacker.getLocation(),
                org.bukkit.Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.0f);
        } else if (attacker instanceof org.bukkit.entity.Drowned) {
            attacker.getWorld().playSound(attacker.getLocation(),
                org.bukkit.Sound.ITEM_TRIDENT_THROW, 0.8f, 1.0f);
        } else if (attacker instanceof org.bukkit.entity.Blaze) {
            attacker.getWorld().playSound(attacker.getLocation(),
                org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.0f);
        }

        // 데미지 적용
        double newHealth = target.getHealth() - damage;
        if (newHealth <= 0) {
            target.setHealth(0);

            // 사망 이펙트
            target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL,
                target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
            target.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);

            // 사망 사운드
            target.getWorld().playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_GENERIC_DEATH, 0.8f, 0.9f);

            target.remove();
        } else {
            target.setHealth(newHealth);

            // 체력바 업데이트
            boolean isBlueTeam = team1Entities.containsValue(target);
            boardManager.updateHealthBar(target, targetUnit, isBlueTeam);

            // 데미지 이펙트
            target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                target.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0);

            // 피격 사운드
            target.getWorld().playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.4f, 1.0f);
        }

        // 공격 이펙트
        attacker.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
            attacker.getLocation().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0);
    }

    /**
     * 크리퍼 폭발 처리 (광역 데미지, 자신은 체력 소모 없음)
     */
    private void handleCreeperExplosion(LivingEntity creeper, Unit creeperUnit, Map<UUID, LivingEntity> enemies) {
        Location explosionLoc = creeper.getLocation();
        double explosionRadius = 5.0; // 폭발 반경 (약 1.25칸)

        // 폭발 이펙트
        creeper.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
            explosionLoc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        creeper.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
            explosionLoc.clone().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.1);
        creeper.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
            explosionLoc.clone().add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0.05);

        // 폭발 사운드
        creeper.getWorld().playSound(explosionLoc,
            org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

        // 광역 데미지 (반경 내 모든 적에게)
        for (LivingEntity enemy : enemies.values()) {
            if (enemy == null || enemy.isDead()) continue;

            double distance = enemy.getLocation().distance(explosionLoc);
            if (distance <= explosionRadius) {
                Unit enemyUnit = entityToUnit.get(enemy);
                if (enemyUnit == null) continue;

                // 거리 기반 데미지 감소 (가까울수록 더 강함)
                double damageMultiplier = 1.0 - (distance / explosionRadius * 0.5); // 50%~100% 데미지
                double damage = plugin.getCombatManager().calculateDamage(creeperUnit, enemyUnit) * damageMultiplier;

                // 크리티컬 체크
                if (plugin.getCombatManager().isCriticalHit(creeperUnit, creeperUnit.getCriticalChance() / 100.0)) {
                    damage *= (1.0 + creeperUnit.getCriticalDamage() / 100.0);
                    enemy.getWorld().spawnParticle(org.bukkit.Particle.CRIT, enemy.getLocation().add(0, 1, 0), 5);
                }

                // 데미지 적용
                double newHealth = enemy.getHealth() - damage;
                if (newHealth <= 0) {
                    enemy.setHealth(0);

                    // 사망 이펙트
                    enemy.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL,
                        enemy.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                    enemy.getWorld().playSound(enemy.getLocation(),
                        org.bukkit.Sound.ENTITY_GENERIC_DEATH, 0.8f, 0.9f);

                    enemy.remove();
                } else {
                    enemy.setHealth(newHealth);

                    // 체력바 업데이트
                    boolean isBlueTeam = team1Entities.containsValue(enemy);
                    boardManager.updateHealthBar(enemy, enemyUnit, isBlueTeam);

                    // 데미지 이펙트
                    enemy.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                        enemy.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0);
                    enemy.getWorld().playSound(enemy.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.4f, 1.0f);
                }
            }
        }

        // 크리퍼는 체력 소모 없이 쿨다운만 적용 (다음 공격까지 대기)
        // AI가 자동으로 다시 타겟을 향해 이동하고 폭발 반복
    }

    /**
     * Calculate damage based on surviving units
     */
    private int calculateDamage() {
        int survivingUnits = Math.max(team1Entities.size(), team2Entities.size());
        int baseDamage = 5;
        return baseDamage + survivingUnits * 2; // 5 + 2 per surviving unit
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (combatTask != null) {
            combatTask.cancel();
        }

        // Remove all spawned entities
        boardManager.clearAllUnits();

        // Clear tracking maps
        team1Entities.clear();
        team2Entities.clear();
        entityToUnit.clear();
    }

    // Getters
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

    public Arena getCombatArena() {
        return combatArena;
    }
}
