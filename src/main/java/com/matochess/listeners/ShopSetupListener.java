package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import com.matochess.data.BoardShopItemData;
import com.matochess.gui.AdminShopGUI;
import com.matochess.gui.ShopSetupSession;
import com.matochess.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 상점 설정 프로세스를 처리하는 리스너
 * 관리자가 아이템 우클릭 및 채팅 입력으로 상점을 설정합니다
 */
public class ShopSetupListener implements Listener {

    private final MatoChessPlugin plugin;
    private final AdminShopGUI adminShopGUI;

    public ShopSetupListener(MatoChessPlugin plugin, AdminShopGUI adminShopGUI) {
        this.plugin = plugin;
        this.adminShopGUI = adminShopGUI;
    }

    /**
     * 아이템 우클릭 감지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player admin = event.getPlayer();
        ShopSetupSession session = adminShopGUI.getSession(admin);

        // 아이템 선택 대기 상태가 아니면 무시
        if (session.getState() != ShopSetupSession.SetupState.WAITING_FOR_ITEM) {
            return;
        }

        // 우클릭만 처리
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            admin.sendMessage("§c유효한 아이템을 우클릭하세요!");
            return;
        }

        // 아이템 저장
        session.setSelectedItem(item.clone());
        session.setState(ShopSetupSession.SetupState.WAITING_FOR_PRICE);

        event.setCancelled(true);

        admin.sendMessage("§a아이템이 선택되었습니다: §f" + item.getType().name());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            admin.sendMessage("§7이름: §f" + item.getItemMeta().getDisplayName());
        }
        admin.sendMessage("");
        admin.sendMessage("§e이제 채팅에 가격을 입력하세요!");
        admin.sendMessage("§7(0을 입력하면 무료 템플릿)");
    }

    /**
     * 채팅 입력 감지
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        ShopSetupSession session = adminShopGUI.getSession(admin);

        // 세션이 활성화되지 않았으면 무시
        if (!session.isActive()) {
            return;
        }

        // 가격 입력 대기
        if (session.getState() == ShopSetupSession.SetupState.WAITING_FOR_PRICE) {
            event.setCancelled(true);

            String input = event.getMessage().trim();

            // 취소 명령어
            if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("취소")) {
                session.reset();
                admin.sendMessage("§c상점 아이템 등록이 취소되었습니다.");
                return;
            }

            // 가격 파싱
            try {
                int price = Integer.parseInt(input);
                if (price < 0) {
                    admin.sendMessage("§c가격은 0 이상이어야 합니다!");
                    admin.sendMessage("§7다시 입력하세요 (취소: §ccancel§7)");
                    return;
                }

                session.setPrice(price);
                session.setState(ShopSetupSession.SetupState.WAITING_FOR_TEMPLATE_ID);

                admin.sendMessage("§a가격이 설정되었습니다: §e" + price + " BT");
                admin.sendMessage("");
                admin.sendMessage("§e마지막으로 템플릿 ID를 입력하세요!");
                admin.sendMessage("§7사용 가능한 템플릿: §edefault, neon, medieval, nature");

            } catch (NumberFormatException e) {
                admin.sendMessage("§c올바른 숫자를 입력하세요!");
                admin.sendMessage("§7다시 입력하세요 (취소: §ccancel§7)");
            }

            return;
        }

        // 템플릿 ID 입력 대기
        if (session.getState() == ShopSetupSession.SetupState.WAITING_FOR_TEMPLATE_ID) {
            event.setCancelled(true);

            String templateId = event.getMessage().trim().toLowerCase();

            // 취소 명령어
            if (templateId.equalsIgnoreCase("cancel") || templateId.equalsIgnoreCase("취소")) {
                session.reset();
                admin.sendMessage("§c상점 아이템 등록이 취소되었습니다.");
                return;
            }

            // 템플릿 존재 확인
            if (!plugin.getTemplateManager().templateExists(templateId)) {
                admin.sendMessage("§c존재하지 않는 템플릿 ID입니다: " + templateId);
                admin.sendMessage("§7사용 가능한 템플릿: §edefault, neon, medieval, nature");
                admin.sendMessage("§7다시 입력하세요 (취소: §ccancel§7)");
                return;
            }

            session.setTemplateId(templateId);

            // 모든 정보 수집 완료 - DB에 저장
            saveShopItem(admin, session);
        }
    }

    /**
     * 상점 아이템 저장
     */
    private void saveShopItem(Player admin, ShopSetupSession session) {
        // ItemStack을 Base64로 인코딩
        String base64 = ItemSerializer.itemToBase64(session.getSelectedItem());

        if (base64 == null) {
            admin.sendMessage("§c아이템 저장에 실패했습니다!");
            session.reset();
            return;
        }

        // ShopItemData 생성
        BoardShopItemData shopItem = new BoardShopItemData(
            session.getTemplateId(),
            base64,
            session.getTargetSlot(),
            session.getPrice()
        );

        // DB에 저장
        plugin.getDataManager().saveShopItem(shopItem).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage("§a§l━━━━━━━━━━━━━━━━━━━━━━");
                admin.sendMessage("§a§l상점 아이템이 등록되었습니다!");
                admin.sendMessage("");
                admin.sendMessage("§e템플릿 ID: §f" + session.getTemplateId());
                admin.sendMessage("§e슬롯: §f" + session.getTargetSlot());
                admin.sendMessage("§e가격: §f" + session.getPrice() + " BT");
                admin.sendMessage("§e아이템: §f" + session.getSelectedItem().getType().name());
                if (session.getSelectedItem().hasItemMeta() && session.getSelectedItem().getItemMeta().hasDisplayName()) {
                    admin.sendMessage("§e이름: §f" + session.getSelectedItem().getItemMeta().getDisplayName());
                }
                admin.sendMessage("§a§l━━━━━━━━━━━━━━━━━━━━━━");

                // 세션 초기화
                session.reset();
            });
        });
    }

    /**
     * 플레이어 로그아웃 시 세션 정리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        adminShopGUI.removeSession(event.getPlayer());
    }
}
