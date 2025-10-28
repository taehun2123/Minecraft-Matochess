package com.matochess.gui;

import com.matochess.MatoChessPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 큐 선택 GUI
 * 5개의 방을 선택할 수 있는 인터페이스
 */
public class QueueGUI {

    private final MatoChessPlugin plugin;

    public QueueGUI(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 큐 선택 GUI 열기
     */
    public void openQueueGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "§6§l큐 선택");

        int maxRooms = plugin.getConfig().getInt("matchmaking.max-rooms", 5);
        int minPlayers = getMinPlayers();
        int maxPlayers = plugin.getConfig().getInt("game.max-players", 8);

        // 각 방에 대한 아이템 생성
        for (int room = 0; room < maxRooms; room++) {
            int queueSize = plugin.getMatchmakingManager().getRoomQueueSize(room);

            ItemStack item = createRoomItem(room + 1, queueSize, minPlayers, maxPlayers);
            gui.setItem(room + 2, item); // 슬롯 2-6에 배치 (중앙)
        }

        player.openInventory(gui);
    }

    /**
     * 방 아이템 생성
     */
    private ItemStack createRoomItem(int roomNumber, int currentPlayers, int minPlayers, int maxPlayers) {
        // 방 상태에 따라 아이템 종류 변경
        Material material;
        String status;

        if (currentPlayers >= maxPlayers) {
            material = Material.RED_WOOL;
            status = "§c만원";
        } else if (currentPlayers >= minPlayers) {
            material = Material.YELLOW_WOOL;
            status = "§e곧 시작";
        } else if (currentPlayers > 0) {
            material = Material.LIME_WOOL;
            status = "§a대기 중";
        } else {
            material = Material.WHITE_WOOL;
            status = "§7비어있음";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6§l방 #" + roomNumber);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§e현재 인원: §f" + currentPlayers + " §7/ §f" + maxPlayers + "명");
        lore.add("§e최소 인원: §f" + minPlayers + "명");
        lore.add("");
        lore.add("§7상태: " + status);
        lore.add("");

        if (currentPlayers >= maxPlayers) {
            lore.add("§c방이 가득 찼습니다!");
        } else {
            lore.add("§a클릭하여 참가하기");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 최소 인원 가져오기 (테스트 모드 고려)
     */
    private int getMinPlayers() {
        boolean testMode = plugin.getConfig().getBoolean("game.test-mode", false);
        if (testMode) {
            return plugin.getConfig().getInt("game.test-min-players", 2);
        }
        return plugin.getConfig().getInt("game.min-players", 4);
    }
}
