package com.matochess.data;

/**
 * Represents the current phase of a game round
 */
public enum GamePhase {
    WAITING("대기 중", false),
    STARTING("시작 중", false),
    PREPARATION("준비 단계", true),
    COMBAT_PVP("전투 단계 (PVP)", false),
    COMBAT_PVE("전투 단계 (PVE)", false),
    SUSHI_SELECTION("초밥 선택", true),
    ROUND_END("라운드 종료", false),
    GAME_END("게임 종료", false);

    private final String displayName;
    private final boolean canInteract;

    GamePhase(String displayName, boolean canInteract) {
        this.displayName = displayName;
        this.canInteract = canInteract;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether players can interact with GUI during this phase
     */
    public boolean canInteract() {
        return canInteract;
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
