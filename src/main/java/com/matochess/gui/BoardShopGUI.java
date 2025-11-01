package com.matochess.gui;

import com.matochess.MatoChessPlugin;
import com.matochess.board.BoardTemplate;
import com.matochess.data.BoardShopItemData;
import com.matochess.data.BoardData;
import com.matochess.data.PlayerProfile;
import com.matochess.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * 보드 템플릿 상점 GUI (DB 기반)
 * 플레이어가 시각적으로 템플릿을 미리보고 구매할 수 있습니다
 */
public class BoardShopGUI implements Listener {

    private final MatoChessPlugin plugin;
    private final Map<String, BoardShopItemData> itemMap = new HashMap<>();

    private static final String GUI_TITLE = "§6§l보드 템플릿 상점";
    private static final int BT_INFO_SLOT = 49; // 중앙 하단

    public BoardShopGUI(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 상점 GUI 열기
     */
    public void openShop(Player player) {
        plugin.getDataManager().loadProfile(player.getUniqueId(), player.getName()).thenAccept(profile -> {
            if (profile == null) {
                player.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            // BoardData 로드 (소유 템플릿 정보) - 동기 메소드를 비동기로 래핑
            BoardData boardData = plugin.getDataManager().getPlayerBoardSync(player.getUniqueId());

            // DB에서 상점 아이템 로드
            plugin.getDataManager().loadAllShopItems().thenAccept(shopItems -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

                    // 상점 아이템 표시
                    for (BoardShopItemData shopItem : shopItems) {
                        ItemStack displayItem = createShopDisplayItem(shopItem, profile, boardData);

                        if (displayItem != null && shopItem.getSlot() >= 0 && shopItem.getSlot() < 54) {
                            inv.setItem(shopItem.getSlot(), displayItem);
                            itemMap.put(player.getUniqueId() + ":" + shopItem.getSlot(), shopItem);
                        }
                    }

                    // BT 정보 아이템
                    ItemStack btInfo = createBTInfoItem(profile);
                    inv.setItem(BT_INFO_SLOT, btInfo);

                    player.openInventory(inv);
                });
            });
        });
    }

    /**
     * DB에서 가져온 아이템을 상점 표시용으로 변환
     */
    private ItemStack createShopDisplayItem(BoardShopItemData shopItem, PlayerProfile profile, BoardData boardData) {
        // Base64에서 ItemStack 복원
        ItemStack item = ItemSerializer.itemFromBase64(shopItem.getItemBase64());

        if (item == null) {
            plugin.getLogger().warning("Failed to deserialize shop item: " + shopItem.getTemplateId());
            return null;
        }

        // 템플릿 정보 가져오기
        BoardTemplate template = plugin.getTemplateManager().getTemplate(shopItem.getTemplateId());
        if (template == null) {
            plugin.getLogger().warning("Template not found: " + shopItem.getTemplateId());
            return null;
        }

        // 아이템에 상점 정보 로어 추가
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§6§l[ " + template.getName() + " ]");
        lore.add("§7카테고리: §f" + template.getCategory());
        lore.add("");

        // 소유 여부 확인 (BoardData에서)
        boolean owned = boardData != null && boardData.getOwnedTemplates().contains(shopItem.getTemplateId());

        if (owned) {
            lore.add("§a✓ 소유 중");
            lore.add("");
            lore.add("§e클릭하여 활성화");
        } else {
            if (shopItem.getPrice() == 0) {
                lore.add("§a무료 템플릿");
                lore.add("");
                lore.add("§e클릭하여 획득");
            } else {
                lore.add("§e가격: §6" + shopItem.getPrice() + " BT");
                lore.add("§7보유 BT: §a" + profile.getBoardPoints() + " BT");
                lore.add("");

                if (profile.hasBoardPoints(shopItem.getPrice())) {
                    lore.add("§e클릭하여 구매");
                } else {
                    lore.add("§c§lBT가 부족합니다!");
                }
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * BT 정보 아이템 생성
     */
    private ItemStack createBTInfoItem(PlayerProfile profile) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6§l내 BT 정보");

            List<String> lore = new ArrayList<>();
            lore.add("§7보유 BT: §a" + profile.getBoardPoints() + " BT");
            lore.add("");
            lore.add("§7BT는 관리자가 설정한");
            lore.add("§7특별한 아이템을 사용하여");
            lore.add("§7획득할 수 있습니다.");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // 상점 GUI인지 확인
        if (!event.getView().getTitle().equals(GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        int slot = event.getSlot();
        String key = player.getUniqueId() + ":" + slot;

        BoardShopItemData shopItem = itemMap.get(key);
        if (shopItem == null) {
            return; // BT 정보 아이템이거나 빈 슬롯
        }

        // 프로필 로드 및 구매/활성화 처리
        plugin.getDataManager().loadProfile(player.getUniqueId(), player.getName()).thenAccept(profile -> {
            if (profile == null) {
                player.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            plugin.getDataManager().hasTemplate(player.getUniqueId(), shopItem.getTemplateId()).thenAccept(owned -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (owned) {
                        // 이미 소유 중이면 활성화
                        activateTemplate(player, shopItem);
                    } else {
                        // 구매 시도
                        purchaseTemplate(player, shopItem, profile);
                    }
                });
            });
        });
    }

    /**
     * 템플릿 구매
     */
    private void purchaseTemplate(Player player, BoardShopItemData shopItem, PlayerProfile profile) {
        BoardTemplate template = plugin.getTemplateManager().getTemplate(shopItem.getTemplateId());

        if (template == null) {
            player.sendMessage("§c템플릿을 찾을 수 없습니다.");
            return;
        }

        // 무료 템플릿
        if (shopItem.getPrice() == 0) {
            plugin.getDataManager().addOwnedTemplate(player.getUniqueId(), shopItem.getTemplateId()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§a템플릿 '§e" + template.getName() + "§a'을(를) 획득했습니다!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player.closeInventory();
                });
            });
            return;
        }

        // BT 확인
        if (!profile.hasBoardPoints(shopItem.getPrice())) {
            player.sendMessage("§cBT가 부족합니다! (필요: " + shopItem.getPrice() + " BT, 보유: " + profile.getBoardPoints() + " BT)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // BT 차감
        if (profile.subtractBoardPoints(shopItem.getPrice())) {
            plugin.getDataManager().saveProfile(profile);

            // 템플릿 추가
            plugin.getDataManager().addOwnedTemplate(player.getUniqueId(), shopItem.getTemplateId()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§a템플릿 '§e" + template.getName() + "§a'을(를) 구매했습니다!");
                    player.sendMessage("§7- " + shopItem.getPrice() + " BT §7(남은: §e" + profile.getBoardPoints() + " BT§7)");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player.closeInventory();
                });
            });
        }
    }

    /**
     * 템플릿 활성화
     */
    private void activateTemplate(Player player, BoardShopItemData shopItem) {
        BoardTemplate template = plugin.getTemplateManager().getTemplate(shopItem.getTemplateId());

        if (template == null) {
            player.sendMessage("§c템플릿을 찾을 수 없습니다.");
            return;
        }

        plugin.getDataManager().setActiveTemplate(player.getUniqueId(), shopItem.getTemplateId()).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a활성 템플릿을 '§e" + template.getName() + "§a'(으)로 변경했습니다!");
                player.sendMessage("§7다음 게임부터 적용됩니다.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                player.closeInventory();
            });
        });
    }

    /**
     * GUI 닫을 때 맵 정리
     */
    public void cleanupPlayer(Player player) {
        itemMap.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUniqueId() + ":"));
    }
}
