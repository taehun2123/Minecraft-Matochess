package com.matochess.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 데이터베이스에 저장될 플레이어의 데이터
 * 보드판 위치 데이터, 템플릿, 보드판 주인의 정보가 포함됩니다.
 */
public class BoardData {

    private final UUID playerId;
    private final String worldName;
    private final int positionX;
    private final int positionY;
    private final int positionZ;
    private final int positionIndex;
    private String activeTemplateId;
    private List<String> ownedTemplates;

    public BoardData(UUID playerId, String worldName, int positionX, int positionY,
                    int positionZ, int positionIndex, String activeTemplateId) {
        this.playerId = playerId;
        this.worldName = worldName;
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.positionIndex = positionIndex;
        this.activeTemplateId = activeTemplateId;
        this.ownedTemplates = new ArrayList<>();
        this.ownedTemplates.add("default"); // Everyone owns default
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getPositionX() {
        return positionX;
    }

    public int getPositionY() {
        return positionY;
    }

    public int getPositionZ() {
        return positionZ;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public String getActiveTemplateId() {
        return activeTemplateId;
    }

    public void setActiveTemplateId(String activeTemplateId) {
        this.activeTemplateId = activeTemplateId;
    }

    public List<String> getOwnedTemplates() {
        return new ArrayList<>(ownedTemplates);
    }

    public void setOwnedTemplates(List<String> ownedTemplates) {
        this.ownedTemplates = ownedTemplates;
    }

    public void addOwnedTemplate(String templateId) {
        if (!ownedTemplates.contains(templateId)) {
            ownedTemplates.add(templateId);
        }
    }

    public boolean ownsTemplate(String templateId) {
        return ownedTemplates.contains(templateId);
    }
}
