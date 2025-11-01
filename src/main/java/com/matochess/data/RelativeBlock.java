package com.matochess.data;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * base location으로의 상대적인 좌표를 표시합니다.
 * 효율적으로 보드 템플릿을 보관되는 데에 사용됩니다.
 */
public class RelativeBlock {

    private final int x;
    private final int y;
    private final int z;
    private final Material material;
    private final BlockData blockData;

    public RelativeBlock(int x, int y, int z, Material material) {
        this(x, y, z, material, null);
    }

    public RelativeBlock(int x, int y, int z, Material material, BlockData blockData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.blockData = blockData;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Material getMaterial() {
        return material;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    @Override
    public String toString() {
        return String.format("RelativeBlock{x=%d, y=%d, z=%d, material=%s}", x, y, z, material);
    }
}
