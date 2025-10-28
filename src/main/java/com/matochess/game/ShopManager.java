package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.core.UnitRegistry;
import com.matochess.data.Unit;
import com.matochess.data.UnitTier;

import java.util.*;

/**
 * Manages shop unit generation and refresh
 */
public class ShopManager {

    private final MatoChessPlugin plugin;
    private final UnitRegistry unitRegistry;
    private static final int SHOP_SIZE = 5;

    public ShopManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.unitRegistry = plugin.getUnitRegistry();
    }

    /**
     * Generate a shop for a player based on their level
     */
    public List<Unit> generateShop(int playerLevel) {
        List<Unit> shop = new ArrayList<>();
        int[] probabilities = getProbabilityForLevel(playerLevel);

        for (int i = 0; i < SHOP_SIZE; i++) {
            UnitTier tier = selectTierByProbability(probabilities);
            Unit unit = unitRegistry.getRandomUnitOfTier(tier);
            if (unit != null) {
                shop.add(unit);
            }
        }

        return shop;
    }

    /**
     * Get probability table for player level
     */
    private int[] getProbabilityForLevel(int level) {
        String path = "units.probability.level-" + level;
        List<Integer> probs = plugin.getConfig().getIntegerList(path);

        if (probs.isEmpty()) {
            // Default fallback
            return switch (level) {
                case 1 -> new int[]{90, 10, 0, 0, 0};
                case 2 -> new int[]{75, 25, 0, 0, 0};
                case 3 -> new int[]{40, 50, 10, 0, 0};
                case 4 -> new int[]{30, 55, 15, 0, 0};
                case 5 -> new int[]{20, 40, 30, 10, 0};
                case 6 -> new int[]{10, 30, 40, 18, 2};
                case 7 -> new int[]{5, 20, 40, 30, 5};
                case 8 -> new int[]{3, 15, 35, 35, 12};
                default -> new int[]{90, 10, 0, 0, 0};
            };
        }

        return probs.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Select a unit tier based on probability distribution
     */
    private UnitTier selectTierByProbability(int[] probabilities) {
        int total = Arrays.stream(probabilities).sum();
        if (total == 0) {
            return UnitTier.ONE_STAR;
        }

        int random = new Random().nextInt(total);
        int cumulative = 0;

        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (random < cumulative) {
                return UnitTier.fromTier(i + 1);
            }
        }

        return UnitTier.ONE_STAR;
    }

    /**
     * Get reroll cost
     */
    public int getRerollCost() {
        return plugin.getConfig().getInt("units.reroll-cost", 2);
    }

    /**
     * Get shop size
     */
    public int getShopSize() {
        return SHOP_SIZE;
    }
}
