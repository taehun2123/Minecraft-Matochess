package com.matochess.board;

import com.matochess.data.RelativeBlock;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a board design template
 * Stores only block data, not actual world blocks
 */
public class BoardTemplate {

    private final String templateId;
    private final String name;
    private final String description;
    private final int price;
    private final List<RelativeBlock> blockData;
    private final Material previewMaterial;
    private final String category;

    public BoardTemplate(String templateId, String name, String description,
                        int price, List<RelativeBlock> blockData,
                        Material previewMaterial, String category) {
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.blockData = new ArrayList<>(blockData);
        this.previewMaterial = previewMaterial;
        this.category = category;
    }

    /**
     * Create default checkerboard template (8x6 cells, 4 blocks per cell)
     */
    public static BoardTemplate createDefault() {
        List<RelativeBlock> blocks = new ArrayList<>();

        int width = 8;  // 8 cells wide
        int height = 6; // 6 cells tall
        int cellSize = 4; // 4 blocks per cell

        // Create checkerboard pattern
        for (int cellX = 0; cellX < width; cellX++) {
            for (int cellZ = 0; cellZ < height; cellZ++) {
                Material color = ((cellX + cellZ) % 2 == 0)
                    ? Material.WHITE_WOOL
                    : Material.BLACK_WOOL;

                // Fill each cell with 4x4 blocks
                for (int bx = 0; bx < cellSize; bx++) {
                    for (int bz = 0; bz < cellSize; bz++) {
                        int x = cellX * cellSize + bx;
                        int z = cellZ * cellSize + bz;
                        blocks.add(new RelativeBlock(x, 0, z, color));
                    }
                }
            }
        }

        return new BoardTemplate(
            "default",
            "기본 체스보드",
            "흰색과 검은색의 클래식한 체스보드",
            0,
            blocks,
            Material.WHITE_WOOL,
            "BASIC"
        );
    }

    /**
     * Create neon-style template with glowing borders
     */
    public static BoardTemplate createNeon() {
        List<RelativeBlock> blocks = new ArrayList<>();

        int width = 8, height = 6, cellSize = 4;

        for (int cellX = 0; cellX < width; cellX++) {
            for (int cellZ = 0; cellZ < height; cellZ++) {
                Material color = ((cellX + cellZ) % 2 == 0)
                    ? Material.CYAN_CONCRETE
                    : Material.MAGENTA_CONCRETE;

                for (int bx = 0; bx < cellSize; bx++) {
                    for (int bz = 0; bz < cellSize; bz++) {
                        int x = cellX * cellSize + bx;
                        int z = cellZ * cellSize + bz;

                        // Base floor
                        blocks.add(new RelativeBlock(x, 0, z, color));

                        // Glowing border on cell edges
                        if (bx == 0 || bx == cellSize - 1 || bz == 0 || bz == cellSize - 1) {
                            blocks.add(new RelativeBlock(x, 1, z, Material.SEA_LANTERN));
                        }
                    }
                }
            }
        }

        return new BoardTemplate(
            "neon",
            "네온 보드",
            "사이버펑크 스타일의 발광 보드",
            5000,
            blocks,
            Material.CYAN_CONCRETE,
            "PREMIUM"
        );
    }

    /**
     * Create medieval stone temple template
     */
    public static BoardTemplate createMedieval() {
        List<RelativeBlock> blocks = new ArrayList<>();

        int width = 8, height = 6, cellSize = 4;

        for (int cellX = 0; cellX < width; cellX++) {
            for (int cellZ = 0; cellZ < height; cellZ++) {
                Material floor = ((cellX + cellZ) % 2 == 0)
                    ? Material.STONE_BRICKS
                    : Material.CRACKED_STONE_BRICKS;

                for (int bx = 0; bx < cellSize; bx++) {
                    for (int bz = 0; bz < cellSize; bz++) {
                        int x = cellX * cellSize + bx;
                        int z = cellZ * cellSize + bz;

                        // Base floor
                        blocks.add(new RelativeBlock(x, 0, z, floor));

                        // Add some moss randomly
                        if ((x + z) % 7 == 0) {
                            blocks.add(new RelativeBlock(x, 0, z, Material.MOSSY_STONE_BRICKS));
                        }
                    }
                }
            }
        }

        return new BoardTemplate(
            "medieval",
            "중세 성당",
            "고풍스러운 돌 벽돌 보드",
            2500,
            blocks,
            Material.STONE_BRICKS,
            "PREMIUM"
        );
    }

    /**
     * Create grass nature template
     */
    public static BoardTemplate createNature() {
        List<RelativeBlock> blocks = new ArrayList<>();

        int width = 8, height = 6, cellSize = 4;

        for (int cellX = 0; cellX < width; cellX++) {
            for (int cellZ = 0; cellZ < height; cellZ++) {
                Material base = ((cellX + cellZ) % 2 == 0)
                    ? Material.GRASS_BLOCK
                    : Material.DIRT;

                for (int bx = 0; bx < cellSize; bx++) {
                    for (int bz = 0; bz < cellSize; bz++) {
                        int x = cellX * cellSize + bx;
                        int z = cellZ * cellSize + bz;

                        blocks.add(new RelativeBlock(x, 0, z, base));

                        // Add flowers randomly
                        if ((x * z) % 11 == 0) {
                            Material flower = (x % 2 == 0) ? Material.DANDELION : Material.POPPY;
                            blocks.add(new RelativeBlock(x, 1, z, flower));
                        }
                    }
                }
            }
        }

        return new BoardTemplate(
            "nature",
            "자연 정원",
            "풀과 꽃이 어우러진 자연 보드",
            3000,
            blocks,
            Material.GRASS_BLOCK,
            "PREMIUM"
        );
    }

    // Getters
    public String getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPrice() {
        return price;
    }

    public List<RelativeBlock> getBlockData() {
        return new ArrayList<>(blockData);
    }

    public Material getPreviewMaterial() {
        return previewMaterial;
    }

    public String getCategory() {
        return category;
    }

    public int getBlockCount() {
        return blockData.size();
    }

    @Override
    public String toString() {
        return String.format("BoardTemplate{id=%s, name=%s, blocks=%d, price=%d}",
            templateId, name, blockData.size(), price);
    }
}
