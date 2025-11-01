package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 전투 중 플레이어 및 같은 팀 보호 리스너
 * - 몹이 플레이어를 타겟으로 하는 것을 방지
 * - 플레이어가 몹으로부터 데미지를 받는 것을 방지
 * - 유닛 사망 시 아이템 드롭 방지
 */
public class CombatProtectionListener implements Listener {

    private final MatoChessPlugin plugin;

    public CombatProtectionListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 몹이 플레이어를 타겟으로 하는 것을 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetEvent event) {
        // 타겟이 플레이어인 경우 타겟팅 취소
        if (event.getTarget() instanceof Player) {
            event.setCancelled(true);
        }
    }

    /**
     * 플레이어가 몹으로부터 데미지를 받는 것을 방지
     * 몹끼리의 데미지는 허용 (전투 시스템, 발사체 포함)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 실제 공격자 확인 (발사체의 경우 shooter 확인)
        Player attackerPlayer = null;

        if (event.getDamager() instanceof Player) {
            // 직접 공격
            attackerPlayer = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            // 발사체 공격 (화살, 삼지창 등)
            Projectile projectile = (Projectile) event.getDamager();
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                attackerPlayer = (Player) shooter;
            }
        }

        // 피해자가 플레이어인 경우 데미지 취소
        if (event.getEntity() instanceof Player) {
            // 게임 중인 플레이어만 보호
            Player player = (Player) event.getEntity();
            if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
                event.setCancelled(true);
                return;
            }
        }

        // 공격자가 플레이어인 경우 데미지 취소 (플레이어가 몹을 공격하지 못하게)
        if (attackerPlayer != null) {
            if (plugin.getGameManager().getPlayerGame(attackerPlayer.getUniqueId()) != null) {
                event.setCancelled(true);
                return;
            }
        }

        // 몹끼리의 전투는 허용 (플레이어가 관여하지 않는 경우)
        // 발사체를 포함한 모든 몹의 공격이 다른 몹에게 적용됨

        // 발사체 데미지는 바닐라 시스템을 사용 (유닛 스탯 기반이 아님)
        // 원격 유닛의 attackDamage는 발사체 기본 데미지로 사용됨
        // CombatInstance에 등록된 엔티티인지 확인하고 데미지를 재계산해야 함
    }

    /**
     * 바닐라 발사체 차단 (아레나 월드에서 몹이 쏘는 발사체 차단)
     * 커스텀 발사체 시스템 사용
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        String worldName = projectile.getWorld().getName();
        String arenaWorldName = plugin.getConfig().getString("arena.arena-world", "matochessWorld");

        if (!worldName.equals(arenaWorldName)) {
            return;
        }

        // 아레나 월드에서 몹이 쏘는 발사체는 차단 (커스텀 시스템 사용)
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof LivingEntity && !(shooter instanceof Player)) {
            LivingEntity mob = (LivingEntity) shooter;

            // 원거리 유닛인지 확인
            boolean isRangedUnit = mob instanceof org.bukkit.entity.Skeleton ||
                                   mob instanceof org.bukkit.entity.Drowned ||
                                   mob instanceof org.bukkit.entity.Witch ||
                                   mob instanceof org.bukkit.entity.Pillager ||
                                   mob instanceof org.bukkit.entity.Blaze;

            if (isRangedUnit) {
                // 바닐라 발사체 차단 (커스텀 시스템으로 대체)
                event.setCancelled(true);
            }
        }
    }

    /**
     * 활 발사 이벤트 차단 (스켈레톤 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityShootBow(EntityShootBowEvent event) {
        LivingEntity shooter = event.getEntity();
        String worldName = shooter.getWorld().getName();
        String arenaWorldName = plugin.getConfig().getString("arena.arena-world", "matochessWorld");

        if (!worldName.equals(arenaWorldName)) {
            return;
        }

        // 아레나 월드에서 몹의 활 발사 차단 (커스텀 시스템 사용)
        if (!(shooter instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /**
     * 유닛 사망 시 아이템 드롭 방지
     * 게임 중인 플레이어의 전투 유닛이 죽을 때 바닐라 아이템이 드롭되지 않도록 함
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // 게임 월드에서 죽은 엔티티인지 확인
        String worldName = entity.getWorld().getName();
        String arenaWorldName = plugin.getConfig().getString("arena.arena-world", "matochessWorld");

        if (worldName.equals(arenaWorldName)) {
            // 아레나 월드에서 죽은 모든 엔티티는 드롭 제거
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * 마토체스 유닛 크리퍼의 자폭을 차단하여 전투 로직만 사용하도록 함
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper)) {
            return;
        }

        Creeper creeper = (Creeper) event.getEntity();
        if (creeper.getScoreboardTags().contains("matochess_unit_protected")) {
            event.setCancelled(true);
            creeper.setFuseTicks(creeper.getMaxFuseTicks());
        }
    }

    /**
     * 피글린/호글린이 환경 때문에 다른 형태로 변환되는 것을 차단
     * (오버월드에서 좀비화/조글린 변환 시도 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();
        if (!entity.getScoreboardTags().contains("matochess_unit_protected")) {
            return;
        }

        EntityType type = entity.getType();
        boolean isPiglinFamily = type == EntityType.PIGLIN ||
                                  type == EntityType.PIGLIN_BRUTE ||
                                  type == EntityType.ZOMBIFIED_PIGLIN;
        boolean isHoglinFamily = type == EntityType.HOGLIN || type == EntityType.ZOGLIN;

        if (!isPiglinFamily && !isHoglinFamily) {
            return;
        }

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity instanceof PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
                piglin.setConversionTime(-1);
            } else if (entity instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
                hoglin.setIsAbleToBeHunted(false);
            }
        });
    }
}
