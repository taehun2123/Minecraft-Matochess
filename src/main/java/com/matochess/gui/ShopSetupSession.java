package com.matochess.gui;

import org.bukkit.inventory.ItemStack;

/**
 * 관리자의 상점 설정 세션을 추적하는 클래스
 * 등록/삭제 프로세스 동안의 상태를 관리합니다
 */
public class ShopSetupSession {

    public enum SetupState {
        NONE,              // 대기 중
        WAITING_FOR_ITEM,  // 아이템 우클릭 대기
        WAITING_FOR_PRICE, // 가격 입력 대기
        WAITING_FOR_TEMPLATE_ID // 템플릿 ID 입력 대기
    }

    public enum SetupType {
        ADD,    // 아이템 추가
        REMOVE  // 아이템 삭제
    }

    private SetupState state;
    private SetupType type;
    private int targetSlot; // 설정할 슬롯 번호
    private ItemStack selectedItem; // 선택한 아이템
    private int price; // 설정한 가격
    private String templateId; // 설정한 템플릿 ID

    public ShopSetupSession() {
        this.state = SetupState.NONE;
    }

    // Getters
    public SetupState getState() {
        return state;
    }

    public SetupType getType() {
        return type;
    }

    public int getTargetSlot() {
        return targetSlot;
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }

    public int getPrice() {
        return price;
    }

    public String getTemplateId() {
        return templateId;
    }

    // Setters
    public void setState(SetupState state) {
        this.state = state;
    }

    public void setType(SetupType type) {
        this.type = type;
    }

    public void setTargetSlot(int targetSlot) {
        this.targetSlot = targetSlot;
    }

    public void setSelectedItem(ItemStack selectedItem) {
        this.selectedItem = selectedItem;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 세션 초기화
     */
    public void reset() {
        this.state = SetupState.NONE;
        this.type = null;
        this.targetSlot = -1;
        this.selectedItem = null;
        this.price = 0;
        this.templateId = null;
    }

    /**
     * 세션이 활성화 상태인지 확인
     */
    public boolean isActive() {
        return state != SetupState.NONE;
    }
}
