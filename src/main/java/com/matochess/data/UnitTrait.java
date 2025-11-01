package com.matochess.data;

import org.bukkit.ChatColor;

/**
 * Unit traits (특성) for synergy system
 * TFT-style trait bonuses
 */
public enum UnitTrait {
    // 종족 특성
    CAVE("동굴", ChatColor.DARK_BLUE, "동굴에서 숨 참으며 살았던 자들"),
    UNDEAD("언데드", ChatColor.DARK_GREEN, "죽음을 두려워하지 않는 자들"),
    VILLAGER("주민", ChatColor.GOLD, "한 때는 평범한 주민이였던 존재들"),
    NETHER("네더", ChatColor.DARK_RED, "지옥에서 온 존재들"),
    OCEAN("바다", ChatColor.AQUA, "깊은 바다의 생명체"),
    CONSTRUCT("구조물", ChatColor.GRAY, "인공적으로 만들어진 존재"),
    END("엔드", ChatColor.DARK_PURPLE, "차원 너머의 존재"),

    // 직업 특성
    WARRIOR("전사", ChatColor.RED, "근접 전투의 달인"),
    MAGE("마법사", ChatColor.LIGHT_PURPLE, "마법의 힘을 다루는 자"),
    ASSASSIN("암살자", ChatColor.DARK_GRAY, "은밀하고 치명적인 공격"),
    TANK("탱커", ChatColor.BLUE, "전장의 방패"),
    RANGER("사수", ChatColor.GREEN, "원거리 공격 전문가"),
    SUMMONER("소환사", ChatColor.YELLOW, "다른 존재를 불러내는 자");

    private final String displayName;
    private final ChatColor color;
    private final String description;

    UnitTrait(String displayName, ChatColor color, String description) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public String getFormattedName() {
        return color + displayName;
    }
}
