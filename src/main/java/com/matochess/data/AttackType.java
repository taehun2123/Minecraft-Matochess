package com.matochess.data;

/**
 * 유닛의 공격 타입
 * 물리 공격과 마법 공격을 구분
 */
public enum AttackType {
    PHYSICAL("물리", "일반 공격에 의존하며 물리 방어력에 영향받음"),
    MAGICAL("마법", "스킬 중심 공격이며 마법 저항력에 영향받음");

    private final String displayName;
    private final String description;

    AttackType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
