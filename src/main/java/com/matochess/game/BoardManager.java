package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.board.BoardInstance;
import com.matochess.data.Position;
import com.matochess.data.Unit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Stray;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 전투판에 있는 유닛 배치 관리
 */
public class BoardManager {

    private final MatoChessPlugin plugin;

    // Track spawned entities
    private final Map<UUID, LivingEntity> spawnedEntities;

    public BoardManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.spawnedEntities = new HashMap<>();
    }

    /**
     * 특정 자리에 유닛을 스폰합니다.
     */
    public LivingEntity spawnUnit(Unit unit, Position position, boolean isBlueTeam, BoardInstance boardInstance) {
        Location baseLocation = boardInstance.getBaseLocation();
        World world = baseLocation.getWorld();

        if (world == null) {
            plugin.getLogger().warning("Combat world not found");
            return null;
        }

        // 보드판 베이스 위치 기준으로 계산
        double minX = baseLocation.getX();
        double minZ = baseLocation.getZ();
        double spawnY = baseLocation.getY();

        // 보드판 레이아웃 (각 칸 = 4x4 블록):
        // - X축: 8칸 × 4블록 = 32블록
        // - Z축: Blue Team 3칸 × 4블록 = 12블록, Red Team 3칸 × 4블록 = 12블록

        // Blue Team은 앞쪽 12블록 영역 (minZ ~ minZ+12)
        // Red Team은 뒤쪽 12블록 영역 (minZ+12 ~ minZ+24)
        double zOffset = isBlueTeam ? minZ : (minZ + 12);

        // GUI의 각 칸(1칸)을 4x4 블록으로 매핑
        // position.getX()는 GUI X좌표 (0~7) → 실제 월드 X축
        // position.getY()는 GUI Y좌표 (0~2)
        // Blue 팀은 자연스럽게 보드의 앞/뒤가 뒤집히도록 처리하며,
        // Red 팀은 배치판에서 본 행 순서를 그대로 유지한다.
        // 유닛은 4x4 영역의 중앙(+2.0블록)에 스폰
        double spawnX = minX + (position.getX() * 4) + 2.0;

        int rowIndex = Math.max(0, Math.min(2, position.getY()));
        int combatRow = isBlueTeam ? (2 - rowIndex) : rowIndex;
        double spawnZ = zOffset + (combatRow * 4) + 2.0;

        Location spawnLoc = new Location(world, spawnX, spawnY + 1, spawnZ);

        // 청크가 로드되어 있지 않다면, 동기적으로 로드합니다.
        Chunk spawnChunk = spawnLoc.getChunk();
        if (!spawnChunk.isLoaded()) {
            // force=true는 청크가 로드될 때까지 현재 스레드를 블로킹합니다.
            spawnChunk.load(true);
            plugin.getLogger().info("Forcibly loaded chunk for unit spawn at " + spawnChunk.getX() + ", " + spawnChunk.getZ());
        }

        plugin.getLogger().info("Spawning " + unit.getId() + " at " + String.format("%.1f, %.1f, %.1f", spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()) + " in world " + world.getName());

        // 스폰
        LivingEntity entity = (LivingEntity) world.spawnEntity(spawnLoc, unit.getEntityType());

        // 체력 설정
        double targetHealth = unit.getHealth();
        double appliedHealth = targetHealth;

        // 바닐라 체력 1024 넘어가면 오류 발생으로 인해 Cap 적용
        // 유닛 체력에 깎인 것에 비례하여 일정한 비율로 1024가 깎임
        final double healthCap = 1024.0;
        if (targetHealth > healthCap) {
            appliedHealth = healthCap;
            plugin.getLogger().fine("Clamped health for unit " + unit.getId() + " to " + healthCap);
        }

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(appliedHealth);
        }
        entity.setHealth(appliedHealth);
        unit.setCurrentHealth(targetHealth);

        // Update health bar (팀별 색상으로 구분, 발광 효과 없음)
        updateHealthBar(entity, unit, isBlueTeam);

        // AI 활성화 - 자연스러운 움직임과 공격을 위해
        entity.setAI(true);

        // Follow Range 대폭 증가 (타겟을 매우 먼 거리에서도 따라가도록)
        if (entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
            entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(128.0); // 128블록까지 따라감 (전체 보드판 커버)
        }

        // 공격력 설정 (AI가 공격할 수 있도록)
        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(unit.getAttackDamage());
        }

        // 이동 속도 설정 (attackSpeed 기반)
        if (entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            double baseSpeed = 0.5;
            double speedMultiplier = Math.min(unit.getAttackSpeed() / 100.0, 2.0);
            speedMultiplier = Math.max(speedMultiplier, 0.5);
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(baseSpeed * speedMultiplier);
        }

        // 중력 적용 (땅에 착지)
        entity.setGravity(true);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2400000,2400000));

        // 몹이 자연스럽게 소멸되지 않도록 설정
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        // Mob 엔티티의 경우 어그로 및 AI 설정 강화
        if (entity instanceof org.bukkit.entity.Mob) {
            org.bukkit.entity.Mob mob = (org.bukkit.entity.Mob) entity;
            mob.setAware(true); // AI 인식 활성화
            mob.setCollidable(true); // 충돌 가능
        }

        if (entity instanceof org.bukkit.entity.PiglinAbstract) {
            org.bukkit.entity.PiglinAbstract piglin = (org.bukkit.entity.PiglinAbstract) entity;
            piglin.setImmuneToZombification(true);
            piglin.setConversionTime(-1);
        }

        if (entity instanceof org.bukkit.entity.Hoglin) {
            org.bukkit.entity.Hoglin hoglin = (org.bukkit.entity.Hoglin) entity;
            hoglin.setImmuneToZombification(true);
            hoglin.setIsAbleToBeHunted(false);
        }

        // 크리퍼 유닛은 자폭하지 않도록 퓨즈/폭발 반경을 제한
        if (entity instanceof org.bukkit.entity.Creeper) {
            org.bukkit.entity.Creeper creeper = (org.bukkit.entity.Creeper) entity;
            creeper.setPowered(false);
            creeper.setExplosionRadius(0);
            creeper.setMaxFuseTicks(2000);
            creeper.setFuseTicks(creeper.getMaxFuseTicks());
        }

        // 엔티티 상태 확인
        plugin.getLogger().info("Entity spawned successfully - Health: " + entity.getHealth() + "/" + entity.getMaxHealth() + ", Dead: " + entity.isDead() + ", Valid: " + entity.isValid());

        // 스트레이 전용: 활을 들고 있으나 기본 활 사격은 차단하고 이펙트 기반 평타만 사용
        if (entity instanceof Stray stray) {
            disableStrayDefaultAttack(stray);
        }

        if (world != null && !world.isChunkLoaded(spawnLoc.getChunk().getX(), spawnLoc.getChunk().getZ())) {
            world.loadChunk(spawnLoc.getChunk().getX(), spawnLoc.getChunk().getZ());
            plugin.getLogger().info("Loaded chunk for unit spawn at " + spawnLoc.getChunk().getX() + ", " + spawnLoc.getChunk().getZ());
        }

        // Store reference
        spawnedEntities.put(unit.getInstanceId(), entity);

        entity.addScoreboardTag("matochess_unit_protected");

        return entity;
    }

    /**
     * 스트레이가 기본 활 공격을 하지 못하도록 장비와 드롭 확률을 초기화합니다.
     */
    private void disableStrayDefaultAttack(Stray stray) {
        EntityEquipment equipment = stray.getEquipment();
        if (equipment != null) {
            ItemStack visualBow = new ItemStack(org.bukkit.Material.BOW);
            equipment.setItemInMainHand(visualBow);
            equipment.setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setItemInOffHandDropChance(0.0f);
        }
        stray.setCanPickupItems(false);
        stray.setPersistent(true);
    }

    /**
     * 엔티티의 체력바 업데이트 (팀별 색상 구분 - 체력에 관계없이 색상 고정)
     */
    public void updateHealthBar(LivingEntity entity, Unit unit, boolean isBlueTeam) {
        double currentHealth = Math.max(0.0, unit.getCurrentHealth());
        double maxHealth = Math.max(1.0, unit.getHealth());
        double currentMana = Math.max(0.0, unit.getMana());
        double maxMana = Math.max(1.0, Unit.getMaxMana());

        int totalBars = 10;
        String healthBar = buildBar(currentHealth, maxHealth, totalBars, isBlueTeam ? ChatColor.AQUA : ChatColor.RED);
        String manaBar = buildBar(currentMana, maxMana, totalBars, ChatColor.BLUE);

        String nameColor = isBlueTeam ? "§b" : "§c";
        // 커스텀 네임태그를 3줄로 분리하여 유닛 정보, HP, MP를 각각 표시
        StringBuilder display = new StringBuilder();
        display.append(nameColor).append(unit.getDisplayName())
            .append("§8| ").append(healthBar).append("§c HP §f")
            .append((int) currentHealth).append("/").append((int) maxHealth)
            .append("§8| ").append(manaBar).append("§9 MP §f")
            .append((int) Math.round(currentMana)).append("/")
            .append((int) maxMana).append("§r");

        entity.setCustomName(display.toString());
        entity.setCustomNameVisible(true);
    }

    /**
     * 모든 스폰된 유닛 정리
     */
    public void clearAllUnits() {
        spawnedEntities.values().forEach(entity -> {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        });
        spawnedEntities.clear();
    }

    /**
     * Get spawned entity for a unit
     */
    public LivingEntity getEntity(Unit unit) {
        return spawnedEntities.get(unit.getInstanceId());
    }

    /**
     * 엔티티 제거
     */
    public void removeEntity(Unit unit) {
        LivingEntity entity = spawnedEntities.remove(unit.getInstanceId());
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    /**
     * HealthBar 생성을 위한 빌더
     * @param current 현재의 체력상태
     * @param max 최대 체력
     * @param totalBars 전체 체력바 개수
     * @param filledColor 팀 컬러
     * @return
     */
    private String buildBar(double current, double max, int totalBars, ChatColor filledColor) {
        int filled = (int) Math.round((Math.max(0.0, current) / Math.max(1.0, max)) * totalBars);
        filled = Math.max(0, Math.min(totalBars, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < filled) {
                bar.append(filledColor).append("█");
            } else {
                bar.append("§7█");
            }
        }
        bar.append("§r");
        return bar.toString();
    }
}
