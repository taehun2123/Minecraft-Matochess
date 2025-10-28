package com.matochess.combat;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.data.Unit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

        // 🚨 핵심 1: 전투가 벌어질 아레나를 결정합니다. (여기서는 player1의 아레나를 사용)
        UUID gameId = player1.getGameId(); // GamePlayer에 getGameId()가 있다고 가정합니다.
        if (gameId == null) {
            plugin.getLogger().severe("Player1 has no active game ID!");
            return;
        }

        // GameManager에서 GameInstance를 가져와 player1의 아레나를 얻습니다.
        var gameInstance = plugin.getGameManager().getGame(gameId); // GameManager에 getGame()이 있다고 가정합니다.
        if (gameInstance == null) return;

        var combatArena = gameInstance.getPlayerArena(player1.getPlayerId());
        if (combatArena == null) {
            plugin.getLogger().severe("Could not find arena for player1!");
            return;
        }

        // CombatInstance에 아레나 정보를 전달합니다. (CombatInstance 생성자 수정 필요)
        CombatInstance combat = new CombatInstance(plugin, combatId, player1, player2, combatArena);
        activeCombats.put(combatId, combat);
        combat.start();

        plugin.getLogger().info("Started PVP combat: " + player1.getPlayer().getName() +
                " vs " + player2.getPlayer().getName() + " at Arena " + combatArena.getId());
    }

    /**
     * 몬스터와의 PVE 전투 시작
     */
    public void startPVECombat(GamePlayer player, int round) {
        UUID combatId = UUID.randomUUID();

        // 🚨 핵심 2: PVE도 player의 아레나를 전투 장소로 지정합니다.
        UUID gameId = player.getGameId();
        if (gameId == null) {
            plugin.getLogger().severe("Player has no active game ID!");
            return;
        }

        var gameInstance = plugin.getGameManager().getGame(gameId);
        if (gameInstance == null) return;

        var combatArena = gameInstance.getPlayerArena(player.getPlayerId());
        if (combatArena == null) {
            plugin.getLogger().severe("Could not find arena for PVE player!");
            return;
        }

        CombatInstance combat = new CombatInstance(plugin, combatId, player, round, combatArena);
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

    // =========================================================
    // 🚨 스코어보드 색상 결정을 위해 추가된 메서드
    // =========================================================

    /**
     * 특정 플레이어가 현재 참여하고 있는 CombatInstance를 찾아 반환합니다.
     * GameInstance에서 스코어보드 색상을 동적으로 결정하는 데 사용됩니다.
     */
    public CombatInstance getCombatByPlayer(UUID playerId) {
        Optional<CombatInstance> combat = activeCombats.values().stream()
                .filter(c -> c.getPlayer1().getPlayerId().equals(playerId) ||
                        (c.getPlayer2() != null && c.getPlayer2().getPlayerId().equals(playerId)))
                .findFirst();

        return combat.orElse(null);
    }

    // =========================================================

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
