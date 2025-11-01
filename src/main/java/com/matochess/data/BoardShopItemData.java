package com.matochess.data;

/**
 * 상점 GUI에 표시될 아이템 데이터
 * 관리자가 설정한 NBT 아이템 정보를 저장합니다
 */
public class BoardShopItemData {

    private final String templateId;
    private final String itemBase64; // Base64로 인코딩된 ItemStack (NBT 포함)
    private final int slot; // GUI 슬롯 위치
    private final int price; // BT 가격 (0이면 무료)

    public BoardShopItemData(String templateId, String itemBase64, int slot, int price) {
        this.templateId = templateId;
        this.itemBase64 = itemBase64;
        this.slot = slot;
        this.price = price;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getItemBase64() {
        return itemBase64;
    }

    public int getSlot() {
        return slot;
    }

    public int getPrice() {
        return price;
    }
}
