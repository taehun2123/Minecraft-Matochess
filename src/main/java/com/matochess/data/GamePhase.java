package com.matochess.data;

/**
 * 라운드의 현재 단계를 표시합니다.
 */
public enum GamePhase {
    WAITING("대기 중"),
    STARTING("시작 중"),
    PREPARATION("준비 단계"),
    COMBAT_PVP("전투 단계 (PVP)"),
    COMBAT_PVE("전투 단계 (PVE)"),
    SUSHI_SELECTION("초밥 선택"),
    ROUND_END("라운드 종료"),
    GAME_END("게임 종료");

    private final String displayName;

    GamePhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCombat() {
        return this == COMBAT_PVP || this == COMBAT_PVE;
    }
    public boolean isPVPCombat() {
        return this == COMBAT_PVP;
    }
    public boolean isPreparation() {
        return this == PREPARATION;
    }
}
