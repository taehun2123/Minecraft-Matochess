package com.matochess.listeners;

import com.matochess.MatoChessPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerMoveEvent;

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

    // ----------------------------------------------------------------------
    // 🎯 블록 가장자리 낙하 방지 (Shift 강제 효과) 로직 추가
    // ----------------------------------------------------------------------

    /**
     * 플레이어 이동 이벤트 처리
     * 아레나 월드에서 블록 가장자리 낙하를 방지합니다.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 1. 아레나 월드인지 확인
        if (!isArenaWorld(player.getWorld().getName())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // 블록 단위 이동이 없으면 (시점 변경만 있으면) 무시
        if (to.getBlockX() == from.getBlockX() &&
                to.getBlockY() == from.getBlockY() &&
                to.getBlockZ() == from.getBlockZ()) {
            return;
        }

        // 2. 이동하려는 위치 바로 아래에 블록이 있는지 확인
        // 플레이어의 새로운 위치(to)에서 Y좌표를 1만큼 뺀 위치를 확인합니다.
        Location blockBelowDestination = to.clone().subtract(0, 1, 0);

        // 3. 바로 아래 블록이 단단한 블록이 아니면 낙하할 상황이므로 차단
        // isSolid()는 블록이 단단한지(밟고 설 수 있는지) 확인합니다.
        if (!blockBelowDestination.getBlock().getType().isSolid()) {

            // 웅크리기(Shift)를 강제하는 것과 같은 효과로, 이동을 취소합니다.
            event.setCancelled(true);
        }
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
