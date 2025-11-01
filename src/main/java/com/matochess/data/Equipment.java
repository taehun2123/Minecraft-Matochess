package com.matochess.data;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 유닛에게 작용이 가능한 장비 데이터 라인입니다. (미구현)
 * 장비를 착용한 유닛은 스텟을 추가 부여받습니다.
 */
public class Equipment {

    private final String id;
    private final String name;
    private final Material material;
    private final Map<StatType, Double> statBonuses;
    private final UUID instanceId;

    public Equipment(String id, String name, Material material) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.statBonuses = new HashMap<>();
        this.instanceId = UUID.randomUUID();
    }

    /**
     * 해당 장비에 보너스 스텟 부여
     */
    public void addStatBonus(StatType statType, double value) {
        statBonuses.put(statType, statBonuses.getOrDefault(statType, 0.0) + value);
    }

    /**
     * 특정 스텟타입으로 보너스 정보 열람
     */
    public double getStatBonus(StatType statType) {
        return statBonuses.getOrDefault(statType, 0.0);
    }

    /**
     * 장비의 사본 복사
     */
    public Equipment copy() {
        Equipment copy = new Equipment(id, name, material);
        copy.statBonuses.putAll(this.statBonuses);
        return copy;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public Map<StatType, Double> getStatBonuses() {
        return new HashMap<>(statBonuses);
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * 장비가 가질 수 있는 스텟 타입을 나열하는 enum 타입
     */
    public enum StatType {
        HEALTH("체력"),
        ATTACK_DAMAGE("공격력"),
        ATTACK_SPEED("공격 속도"),
        ARMOR("방어력"),
        MAGIC_RESIST("마법 저항력"),
        CRITICAL_CHANCE("치명타 확률"),
        CRITICAL_DAMAGE("치명타 피해"),
        LIFE_STEAL("생명력 흡수"),
        MOVEMENT_SPEED("이동 속도");

        private final String displayName;

        StatType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
