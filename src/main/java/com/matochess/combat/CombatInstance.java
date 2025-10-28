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
import org.bukkit.entity.LivingEntity;
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

    private BukkitTask combatTask;
    private boolean isFinished;
    private int tickCount;

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
     * 🚨 새 메서드: 플레이어를 전투 아레나의 관전 위치로 텔레포트합니다.
     * (이 로직은 GameInstance의 teleportToArenaView 로직을 재사용해야 합니다.)
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

        // 8x6 보드판의 중심 좌표 계산 (GameInstance의 로직 재사용)
        double centerX = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;
        double boardY = Math.min(pos1.getY(), pos2.getY());

        // 카메라 위치: 보드판 중심 위쪽
        double cameraY = boardY + 8; // 8블록 위
        double cameraZ = centerZ + 5; // 보드 뒤쪽으로 5블록

        Location cameraPos = new Location(pos1.getWorld(), centerX, cameraY, cameraZ);

        // 시점 유지를 위해 플레이어의 현재 시점을 재사용 (이전 답변에서 수정했던 로직)
        float currentYaw = player.getLocation().getYaw();
        float currentPitch = player.getLocation().getPitch();

        Location teleportLoc = new Location(cameraPos.getWorld(), cameraPos.getX(), cameraPos.getY(), cameraPos.getZ(), currentYaw, currentPitch);

        player.teleport(teleportLoc);
        plugin.getLogger().info("Player " + player.getName() + " teleported to combat arena view.");
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
            // TODO: Spawn PVE monsters
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
     * Perform action for a single entity
     */
    private void performEntityAction(LivingEntity attacker, Map<UUID, LivingEntity> enemies) {
        Unit attackerUnit = entityToUnit.get(attacker);
        if (attackerUnit == null) {
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

        double distance = attacker.getLocation().distance(target.getLocation());
        double attackRange = 2.0; // Default attack range

        if (distance <= attackRange) {
            // Attack
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

                // 체력바 업데이트
                boardManager.updateHealthBar(target, targetUnit);

                // Damage particles
                target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0);

                // Damage sound
                target.getWorld().playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.4f, 1.0f);
            }

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

                // 체력바 업데이트
                boardManager.updateHealthBar(attacker, attackerUnit);

                // Heal particles
                if (heal > 0) {
                    attacker.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                        attacker.getLocation().add(0, 2, 0), 2, 0.3, 0.3, 0.3, 0);
                    attacker.getWorld().playSound(attacker.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
                }
            }
        } else {
            // Move towards target
            Location attackerLoc = attacker.getLocation();
            Location targetLoc = target.getLocation();

            org.bukkit.util.Vector direction = targetLoc.toVector().subtract(attackerLoc.toVector()).normalize();
            double moveSpeed = 0.3; // Movement speed per tick
            Location newLoc = attackerLoc.add(direction.multiply(moveSpeed));

            attacker.teleport(newLoc);
        }
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
}
