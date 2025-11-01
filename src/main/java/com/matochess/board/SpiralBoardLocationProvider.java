package com.matochess.board;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Provides board locations using Ulam Spiral pattern
 * Ensures infinite expansion with consistent spacing
 *
 * Pattern example (spacing=200):
 * [4] [3] [2]
 * [5] [0] [1]
 * [6] [7] [8]
 */
public class SpiralBoardLocationProvider {

    private final World world;
    private final int boardSpacing;
    private final Location centerPoint;

    /**
     * Create a new spiral location provider
     * @param world The world to place boards in
     * @param spacing Distance between board centers in blocks
     */
    public SpiralBoardLocationProvider(World world, int spacing) {
        this.world = world;
        this.boardSpacing = spacing;
        this.centerPoint = new Location(world, 0, 100, 0);
    }

    /**
     * Calculate the location for a specific index in the spiral
     * @param index The board index (0 = center)
     * @return Location for this board
     */
    public Location calculateSpiralPosition(int index) {
        if (index == 0) {
            return centerPoint.clone();
        }

        // Improved Ulam Spiral algorithm
        // 각 레이어의 시작 인덱스 계산
        int layer = (int) Math.ceil((Math.sqrt(index) - 1) / 2.0);

        // layer가 0이 되는 것을 방지 (최소값 1)
        if (layer < 1) {
            layer = 1;
        }

        int layerStart = (2 * layer - 1) * (2 * layer - 1);
        int offset = index - layerStart;
        int layerSize = 2 * layer;

        int x = 0, z = 0;

        // 레이어 크기로 나누기 전에 0 체크
        if (layerSize == 0) {
            // 예외 상황: index 1-8을 직접 매핑
            switch (index) {
                case 1: x = 1; z = 0; break;
                case 2: x = 1; z = 1; break;
                case 3: x = 0; z = 1; break;
                case 4: x = -1; z = 1; break;
                case 5: x = -1; z = 0; break;
                case 6: x = -1; z = -1; break;
                case 7: x = 0; z = -1; break;
                case 8: x = 1; z = -1; break;
                default: x = 0; z = 0; break;
            }
        } else {
            int leg = offset / layerSize;
            int position = offset % layerSize;

            switch (leg) {
                case 0: // Right
                    x = layer;
                    z = position - layer;
                    break;
                case 1: // Up
                    x = layer - position;
                    z = layer;
                    break;
                case 2: // Left
                    x = -layer;
                    z = layer - position;
                    break;
                case 3: // Down
                    x = position - layer;
                    z = -layer;
                    break;
            }
        }

        // Scale by spacing
        double worldX = centerPoint.getX() + (x * boardSpacing);
        double worldZ = centerPoint.getZ() + (z * boardSpacing);

        return new Location(world, worldX, centerPoint.getY(), worldZ);
    }

    /**
     * Get the spacing between boards
     */
    public int getBoardSpacing() {
        return boardSpacing;
    }

    /**
     * Get the center point of the spiral
     */
    public Location getCenterPoint() {
        return centerPoint.clone();
    }

    /**
     * Calculate the grid coordinates for a specific index
     * Useful for visualization/debugging
     * @return [x, z] grid coordinates
     */
    public int[] getGridCoordinates(int index) {
        if (index == 0) {
            return new int[]{0, 0};
        }

        int layer = (int) Math.ceil((Math.sqrt(index) - 1) / 2.0);

        // layer가 0이 되는 것을 방지 (최소값 1)
        if (layer < 1) {
            layer = 1;
        }

        int layerStart = (2 * layer - 1) * (2 * layer - 1);
        int offset = index - layerStart;
        int layerSize = 2 * layer;

        int x = 0, z = 0;

        // 레이어 크기로 나누기 전에 0 체크
        if (layerSize == 0) {
            // 예외 상황: index 1-8을 직접 매핑
            switch (index) {
                case 1: x = 1; z = 0; break;
                case 2: x = 1; z = 1; break;
                case 3: x = 0; z = 1; break;
                case 4: x = -1; z = 1; break;
                case 5: x = -1; z = 0; break;
                case 6: x = -1; z = -1; break;
                case 7: x = 0; z = -1; break;
                case 8: x = 1; z = -1; break;
                default: x = 0; z = 0; break;
            }
        } else {
            int leg = offset / layerSize;
            int position = offset % layerSize;

            switch (leg) {
                case 0:
                    x = layer;
                    z = position - layer;
                    break;
                case 1:
                    x = layer - position;
                    z = layer;
                    break;
                case 2:
                    x = -layer;
                    z = layer - position;
                    break;
                case 3:
                    x = position - layer;
                    z = -layer;
                    break;
            }
        }

        return new int[]{x, z};
    }
}
