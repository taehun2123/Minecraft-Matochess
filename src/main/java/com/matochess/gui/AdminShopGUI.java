package com.matochess.gui;

import com.matochess.MatoChessPlugin;
import com.matochess.data.BoardShopItemData;
import com.matochess.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 상점 관리 GUI
 * 상점 아이템을 시각적으로 등록/수정/삭제할 수 있습니다
 */
public class AdminShopGUI implements Listener {

    private final MatoChessPlugin plugin;
    private final Map<String, ShopSetupSession> sessions = new HashMap<>();

    private static final String GUI_TITLE = "§c§l[관리자] 상점 관리";
    private static final int ADD_BUTTON_SLOT = 45; // 하단 왼쪽
    private static final int REMOVE_BUTTON_SLOT = 53; // 하단 오른쪽

    public AdminShopGUI(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 관리자 상점 GUI 열기
     */
    public void openAdminShop(Player admin) {
        plugin.getDataManager().loadAllShopItems().thenAccept(shopItems -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

                // 등록된 상점 아이템 표시
                for (BoardShopItemData shopItem : shopItems) {
                    ItemStack displayItem = ItemSerializer.itemFromBase64(shopItem.getItemBase64());

                    if (displayItem != null && shopItem.getSlot() >= 0 && shopItem.getSlot() < 45) {
                        // 로어에 정보 추가
                        ItemMeta meta = displayItem.getItemMeta();
                        if (meta != null) {
                            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                            lore.add("");
                            lore.add("§7[관리자 정보]");
                            lore.add("§7템플릿ID: §e" + shopItem.getTemplateId());
                            lore.add("§7가격: §e" + shopItem.getPrice() + " BT");
                            lore.add("§7슬롯: §e" + shopItem.getSlot());
                            meta.setLore(lore);
                            displayItem.setItemMeta(meta);
                        }

                        inv.setItem(shopItem.getSlot(), displayItem);
                    }
                }

                // 등록 버튼
                ItemStack addButton = new ItemStack(Material.LIME_WOOL);
                ItemMeta addMeta = addButton.getItemMeta();
                if (addMeta != null) {
                    addMeta.setDisplayName("§a§l[+] 아이템 등록");
                    List<String> addLore = new ArrayList<>();
                    addLore.add("§7클릭하여 새 아이템을 등록합니다");
                    addLore.add("");
                    addLore.add("§e1. §7이 버튼 클릭");
                    addLore.add("§e2. §7등록할 슬롯 클릭");
                    addLore.add("§e3. §7원하는 아이템 우클릭");
                    addLore.add("§e4. §7채팅에 가격 입력");
                    addLore.add("§e5. §7채팅에 템플릿ID 입력");
                    addMeta.setLore(addLore);
                    addButton.setItemMeta(addMeta);
                }
                inv.setItem(ADD_BUTTON_SLOT, addButton);

                // 삭제 버튼
                ItemStack removeButton = new ItemStack(Material.RED_WOOL);
                ItemMeta removeMeta = removeButton.getItemMeta();
                if (removeMeta != null) {
                    removeMeta.setDisplayName("§c§l[-] 아이템 삭제");
                    List<String> removeLore = new ArrayList<>();
                    removeLore.add("§7클릭하여 아이템을 삭제합니다");
                    removeLore.add("");
                    removeLore.add("§e1. §7이 버튼 클릭");
                    removeLore.add("§e2. §7삭제할 아이템 슬롯 클릭");
                    removeMeta.setLore(removeLore);
                    removeButton.setItemMeta(removeMeta);
                }
                inv.setItem(REMOVE_BUTTON_SLOT, removeButton);

                admin.openInventory(inv);
            });
        });
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player admin = (Player) event.getWhoClicked();

        // 관리자 상점 GUI인지 확인
        if (!event.getView().getTitle().equals(GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ShopSetupSession session = sessions.computeIfAbsent(admin.getUniqueId().toString(), k -> new ShopSetupSession());

        // 등록 버튼 클릭
        if (slot == ADD_BUTTON_SLOT) {
            session.setType(ShopSetupSession.SetupType.ADD);
            session.setState(ShopSetupSession.SetupState.NONE); // 슬롯 선택 대기
            admin.sendMessage("§a등록할 슬롯을 클릭하세요! (0~44번 슬롯)");
            return;
        }

        // 삭제 버튼 클릭
        if (slot == REMOVE_BUTTON_SLOT) {
            session.setType(ShopSetupSession.SetupType.REMOVE);
            session.setState(ShopSetupSession.SetupState.NONE);
            admin.sendMessage("§c삭제할 아이템이 있는 슬롯을 클릭하세요!");
            return;
        }

        // 슬롯 클릭 (0~44)
        if (slot >= 0 && slot < 45) {
            handleSlotClick(admin, session, slot, clicked);
        }
    }

    /**
     * 슬롯 클릭 처리
     */
    private void handleSlotClick(Player admin, ShopSetupSession session, int slot, ItemStack clickedItem) {
        // 등록 모드
        if (session.getType() == ShopSetupSession.SetupType.ADD) {
            session.setTargetSlot(slot);
            session.setState(ShopSetupSession.SetupState.WAITING_FOR_ITEM);
            admin.closeInventory();
            admin.sendMessage("§a슬롯 §e" + slot + "§a번에 등록할 아이템을 §e우클릭§a하세요!");
            admin.sendMessage("§7취소하려면 §c/mcadmin shop cancel §7을 입력하세요.");
            return;
        }

        // 삭제 모드
        if (session.getType() == ShopSetupSession.SetupType.REMOVE) {
            // 해당 슬롯에 아이템이 있는지 DB에서 확인
            plugin.getDataManager().loadAllShopItems().thenAccept(items -> {
                BoardShopItemData targetItem = null;
                for (BoardShopItemData item : items) {
                    if (item.getSlot() == slot) {
                        targetItem = item;
                        break;
                    }
                }

                if (targetItem == null) {
                    admin.sendMessage("§c해당 슬롯에 등록된 아이템이 없습니다!");
                    session.reset();
                    return;
                }

                BoardShopItemData finalTarget = targetItem;
                plugin.getDataManager().deleteShopItem(targetItem.getTemplateId()).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        admin.sendMessage("§a슬롯 §e" + slot + "§a번의 아이템을 삭제했습니다!");
                        admin.sendMessage("§7템플릿ID: §e" + finalTarget.getTemplateId());
                        session.reset();
                        admin.closeInventory();

                        // GUI 새로고침
                        Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminShop(admin), 5L);
                    });
                });
            });
        }
    }

    /**
     * 세션 가져오기
     */
    public ShopSetupSession getSession(Player admin) {
        return sessions.computeIfAbsent(admin.getUniqueId().toString(), k -> new ShopSetupSession());
    }

    /**
     * 세션 제거
     */
    public void removeSession(Player admin) {
        sessions.remove(admin.getUniqueId().toString());
    }

    /**
     * 세션 초기화
     */
    public void resetSession(Player admin) {
        ShopSetupSession session = sessions.get(admin.getUniqueId().toString());
        if (session != null) {
            session.reset();
        }
    }
}
