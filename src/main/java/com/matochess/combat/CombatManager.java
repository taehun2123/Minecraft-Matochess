package com.matochess.combat;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.data.Unit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어 간 또는 PVE 적과의 전투를 관리합니다. 유닛 AI, 전투 계산 및 전투 결과를 처리합니다.
 */
public class CombatManager {

    private final MatoChessPlugin plugin;
    private final Map<UUID, CombatInstance> activeCombats;

    public CombatManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.activeCombats = new HashMap<>();
    }

    /**
     * 두 플레이어 간의 PVP 전투 시작
     */
    public void startPVPCombat(GamePlayer player1, GamePlayer player2) {
        UUID combatId = UUID.randomUUID();
        CombatInstance combat = new CombatInstance(plugin, combatId, player1, player2);

        activeCombats.put(combatId, combat);
        combat.start();

        plugin.getLogger().info("Started PVP combat: " + player1.getPlayer().getName() +
                               " vs " + player2.getPlayer().getName());
    }

    /**
     * 몬스터와의 PVE 전투 시작
     */
    public void startPVECombat(GamePlayer player, int round) {
        UUID combatId = UUID.randomUUID();
        CombatInstance combat = new CombatInstance(plugin, combatId, player, round);

        activeCombats.put(combatId, combat);
        combat.start();

        plugin.getLogger().info("Started PVE combat for: " + player.getPlayer().getName() +
                               " (Round " + round + ")");
    }

    /**
     * End a combat instance
     */
    public void endCombat(UUID combatId) {
        CombatInstance combat = activeCombats.remove(combatId);
        if (combat != null) {
            combat.cleanup();
        }
    }

    /**
     * Get active combat
     */
    public CombatInstance getCombat(UUID combatId) {
        return activeCombats.get(combatId);
    }

    /**
     * Calculate damage for a unit attack
     */
    public double calculateDamage(Unit attacker, Unit defender) {
        double baseDamage = attacker.getAttackDamage();
        double armor = defender.getArmor();

        // Simple damage formula: damage * (100 / (100 + armor))
        double damageMultiplier = 100.0 / (100.0 + armor);
        return baseDamage * damageMultiplier;
    }

    /**
     * Check if attack is critical
     */
    public boolean isCriticalHit(Unit attacker, double critChance) {
        return Math.random() < critChance;
    }
}
