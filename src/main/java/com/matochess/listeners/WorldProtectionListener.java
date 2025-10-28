package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * 아레나 월드 보호 리스너
 * - 블록 파괴/설치 방지
 * - 화염 번짐 방지
 * - 폭발로 인한 블록 파괴 방지
 * - 자연 몹 스폰 방지 (플러그인이 생성한 몹만 허용)
 */
public class WorldProtectionListener implements Listener {

    private final MatoChessPlugin plugin;

    public WorldProtectionListener(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 아레나 월드인지 확인
     */
    private boolean isArenaWorld(String worldName) {
        String arenaWorldName = plugin.getConfig().getString("arena.arena-world", "matochessWorld");
        return worldName.equals(arenaWorldName);
    }

    /**
     * 자연 몹 스폰 방지 (플러그인이 생성한 몹만 허용)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isArenaWorld(event.getLocation().getWorld().getName())) {
            // CUSTOM, SPAWNER_EGG, PLUGIN 등은 허용 (우리가 생성한 몹)
            // NATURAL, CHUNK_GEN 등은 차단 (자연 생성)
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

            switch (reason) {
                case CUSTOM:
                case SPAWNER_EGG:
                case COMMAND:
                    // 플러그인이나 관리자가 생성한 몹은 허용
                    break;
                default:
                    // 나머지 모든 자연 스폰은 차단
                    event.setCancelled(true);
                    break;
            }
        }
    }

    /**
     * 블록 파괴 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 블록 설치 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 화염 번짐 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 화염 발화 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getBlock() != null && isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 폭발로 인한 블록 파괴 방지 (크리퍼, TNT 등)
     * 폭발 자체는 허용하되 블록 파괴만 막음
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isArenaWorld(event.getLocation().getWorld().getName())) {
            // 폭발은 허용하되 블록 파괴만 방지
            event.blockList().clear();
        }
    }

    /**
     * 엔티티에 의한 블록 변경 방지 (엔더맨 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 블록 자연 변화 방지 (물, 용암 흐름 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 블록 페이드 방지 (눈, 얼음 녹기 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 블록 성장 방지 (농작물, 나무 등)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockGrow(BlockGrowEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * 나뭇잎 자연 소멸 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isArenaWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }
}
