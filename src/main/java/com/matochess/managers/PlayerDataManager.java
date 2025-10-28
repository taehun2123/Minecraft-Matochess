package com.matochess.managers;

import com.matochess.data.PlayerBackupData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어 데이터 저장 및 복구 관리자
 * 큐 참가 시 데이터를 백업하고, 게임 종료 후 복구합니다
 */
public class PlayerDataManager {

    private final Map<UUID, PlayerBackupData> backupData;

    public PlayerDataManager() {
        this.backupData = new HashMap<>();
    }

    /**
     * 플레이어 데이터 백업
     * 큐 참가 시 호출됩니다
     */
    public void backupPlayerData(Player player) {
        UUID playerId = player.getUniqueId();

        // 이미 백업된 데이터가 있으면 무시 (중복 방지)
        if (backupData.containsKey(playerId)) {
            return;
        }

        // 현재 위치 저장
        org.bukkit.Location location = player.getLocation().clone();

        // 인벤토리 저장 (복사본)
        ItemStack[] inventory = player.getInventory().getContents().clone();
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        ItemStack offHand = player.getInventory().getItemInOffHand().clone();

        // 게임 상태 저장
        GameMode gameMode = player.getGameMode();
        double health = player.getHealth();
        int foodLevel = player.getFoodLevel();
        float experience = player.getExp();
        int level = player.getLevel();

        // 백업 데이터 생성 및 저장
        PlayerBackupData data = new PlayerBackupData(
            location, inventory, armor, offHand,
            gameMode, health, foodLevel, experience, level
        );

        backupData.put(playerId, data);
    }

    /**
     * 플레이어 데이터 복구
     * 게임 종료 후 호출됩니다
     */
    public boolean restorePlayerData(Player player) {
        UUID playerId = player.getUniqueId();

        PlayerBackupData data = backupData.get(playerId);
        if (data == null) {
            return false;
        }

        // 인벤토리 복구
        player.getInventory().clear();
        player.getInventory().setContents(data.getInventoryContents());
        player.getInventory().setArmorContents(data.getArmorContents());
        player.getInventory().setItemInOffHand(data.getOffHandItem());

        // 게임 상태 복구
        player.setGameMode(data.getGameMode());
        player.setHealth(data.getHealth());
        player.setFoodLevel(data.getFoodLevel());
        player.setExp(data.getExperience());
        player.setLevel(data.getLevel());

        // 위치 복구 (텔레포트)
        player.teleport(data.getLocation());

        // 백업 데이터 제거
        backupData.remove(playerId);

        return true;
    }

    /**
     * 플레이어의 인벤토리 초기화
     * 큐 참가 후 호출됩니다
     */
    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.ADVENTURE);
    }

    /**
     * 백업 데이터 확인
     */
    public boolean hasBackupData(UUID playerId) {
        return backupData.containsKey(playerId);
    }

    /**
     * 백업 데이터 제거 (강제 종료 시 사용)
     */
    public void removeBackupData(UUID playerId) {
        backupData.remove(playerId);
    }
}
