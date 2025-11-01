package com.matochess.board.management;

import com.matochess.MatoChessPlugin;
import com.matochess.board.BoardTemplate;
import com.matochess.data.RelativeBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages board templates
 * Handles registration, loading, and creation of templates
 */
public class BoardTemplateManager {

    private final MatoChessPlugin plugin;
    private final Map<String, BoardTemplate> templates;

    public BoardTemplateManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.templates = new ConcurrentHashMap<>();

        // Register default templates
        registerDefaultTemplates();
    }

    /**
     * Register all default templates
     */
    private void registerDefaultTemplates() {
        registerTemplate(BoardTemplate.createDefault());
        registerTemplate(BoardTemplate.createNeon());
        registerTemplate(BoardTemplate.createMedieval());
        registerTemplate(BoardTemplate.createNature());

        plugin.getLogger().info("Registered " + templates.size() + " default board templates");
    }

    /**
     * Register a new template
     */
    public void registerTemplate(BoardTemplate template) {
        templates.put(template.getTemplateId(), template);
        plugin.getLogger().info("Registered template: " + template.getName() + " (" + template.getBlockCount() + " blocks)");
    }

    /**
     * Get template by ID, returns default if not found
     */
    public BoardTemplate getTemplate(String templateId) {
        return templates.getOrDefault(templateId, templates.get("default"));
    }

    /**
     * Get all templates
     */
    public List<BoardTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Get templates by category
     */
    public List<BoardTemplate> getTemplatesByCategory(String category) {
        return templates.values().stream()
            .filter(t -> t.getCategory().equalsIgnoreCase(category))
            .collect(Collectors.toList());
    }

    /**
     * Check if a template exists
     */
    public boolean templateExists(String templateId) {
        return templates.containsKey(templateId);
    }

    /**
     * Save a template from world selection (admin command)
     * @param templateId Template ID
     * @param name Template name
     * @param pos1 First corner
     * @param pos2 Second corner
     * @param price Template price
     * @param category Template category
     */
    public CompletableFuture<BoardTemplate> saveTemplateFromWorld(
        String templateId, String name, Location pos1, Location pos2,
        int price, String category) {

        return CompletableFuture.supplyAsync(() -> {
            List<RelativeBlock> blocks = new ArrayList<>();

            int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
            int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

            World world = pos1.getWorld();

            // Read all blocks from the selection
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Location loc = new Location(world, x, y, z);
                        Material material = loc.getBlock().getType();

                        if (material != Material.AIR) {
                            int relX = x - minX;
                            int relY = y - minY;
                            int relZ = z - minZ;

                            BlockData blockData = loc.getBlock().getBlockData();
                            blocks.add(new RelativeBlock(relX, relY, relZ, material, blockData));
                        }
                    }
                }
            }

            plugin.getLogger().info("Captured " + blocks.size() + " blocks for template " + templateId);

            Material preview = blocks.isEmpty() ? Material.WHITE_WOOL : blocks.get(0).getMaterial();

            BoardTemplate template = new BoardTemplate(
                templateId,
                name,
                "관리자가 생성한 커스텀 보드",
                price,
                blocks,
                preview,
                category
            );

            registerTemplate(template);

            // Save to database
            plugin.getDataManager().saveTemplate(template);

            return template;
        });
    }

    /**
     * Load custom templates from database
     */
    public CompletableFuture<Void> loadCustomTemplates() {
        return plugin.getDataManager().loadAllTemplates()
            .thenAccept(loadedTemplates -> {
                for (BoardTemplate template : loadedTemplates) {
                    registerTemplate(template);
                }
                plugin.getLogger().info("Loaded " + loadedTemplates.size() + " custom templates from database");
            });
    }

    /**
     * Get all categories
     */
    public Set<String> getAllCategories() {
        return templates.values().stream()
            .map(BoardTemplate::getCategory)
            .collect(Collectors.toSet());
    }

    /**
     * Get free (price = 0) templates
     */
    public List<BoardTemplate> getFreeTemplates() {
        return templates.values().stream()
            .filter(t -> t.getPrice() == 0)
            .collect(Collectors.toList());
    }

    /**
     * Get premium (price > 0) templates
     */
    public List<BoardTemplate> getPremiumTemplates() {
        return templates.values().stream()
            .filter(t -> t.getPrice() > 0)
            .sorted(Comparator.comparingInt(BoardTemplate::getPrice))
            .collect(Collectors.toList());
    }
}
