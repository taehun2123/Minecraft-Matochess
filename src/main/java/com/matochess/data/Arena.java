package com.matochess.data;

import org.bukkit.Location;

/**
 * 전투 체스판 데이터
 * 8x6 체스판 영역 정보를 저장합니다
 */
public class Arena {

    private final int id;
    private final Location pos1;
    private final Location pos2;
    private boolean inUse;

    public Arena(int id, Location pos1, Location pos2) {
        this.id = id;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.inUse = false;
    }

    public int getId() {
        return id;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    /**
     * 체스판 중앙 위치 계산
     */
    public Location getCenterLocation() {
        double x = (pos1.getX() + pos2.getX()) / 2;
        double y = pos1.getY();
        double z = (pos1.getZ() + pos2.getZ()) / 2;
        return new Location(pos1.getWorld(), x, y, z);
    }

    /**
     * 플레이어 1 시작 위치 (8x3 영역의 중앙)
     * 0-2행 중앙
     */
    public Location getPlayer1SpawnLocation() {
        double x = (pos1.getX() + pos2.getX()) / 2;
        double y = pos1.getY() + 1; // 블록 위
        double z = pos1.getZ() + 1.5; // 1행 중앙
        return new Location(pos1.getWorld(), x, y, z);
    }

    /**
     * 플레이어 2 시작 위치 (8x3 영역의 중앙)
     * 3-5행 중앙
     */
    public Location getPlayer2SpawnLocation() {
        double x = (pos1.getX() + pos2.getX()) / 2;
        double y = pos1.getY() + 1; // 블록 위
        double z = pos1.getZ() + 4.5; // 4행 중앙
        return new Location(pos1.getWorld(), x, y, z);
    }
}
