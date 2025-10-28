package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.core.SynergyManager;
import com.matochess.data.*;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a single game instance with 8 players
 * Manages rounds, phases, and game flow
 */
public class GameInstance {

    private final MatoChessPlugin plugin;
    private final UUID gameId;
    private final Map<UUID, GamePlayer> players;
    private final List<UUID> playerIds;

    // 각 플레이어에게 할당된 아레나
    private final Map<UUID, Arena> playerArenas;

    private GamePhase currentPhase;
    private int currentRound;
    private BukkitTask phaseTask;

    // 보스바
    private BossBar bossBar;
    private int phaseTimeRemaining;
    private BukkitTask bossBarTask; // 보스바 타이머 태스크

    // 스코어보드 (플레이어별)
    private final Map<UUID, Scoreboard> playerScoreboards;

    // Configuration from config.yml
    private final int preparationTime;
    private final int combatTime;
    private final int startingGold;
    private final int startingLevel;
    private final int startingHp;
    private final int goldPerRound;
    private final int xpPerRound;
    private final int[] xpRequired;

    public GameInstance(MatoChessPlugin plugin, UUID gameId, List<UUID> playerIds) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.playerIds = new ArrayList<>(playerIds);
        this.players = new HashMap<>();
        this.playerArenas = new HashMap<>();
        this.playerScoreboards = new HashMap<>();
        this.currentPhase = GamePhase.WAITING;
        this.currentRound = 0;

        // 보스바 생성
        this.bossBar = Bukkit.createBossBar("§6준비 중...", BarColor.YELLOW, BarStyle.SOLID);
        this.bossBar.setVisible(true);

        // Load configuration
        this.preparationTime = plugin.getConfig().getInt("game.preparation-time", 30);
        this.combatTime = plugin.getConfig().getInt("game.combat-time", 60);
        this.startingGold = plugin.getConfig().getInt("game.starting-gold", 5);
        this.startingLevel = plugin.getConfig().getInt("game.starting-level", 1);
        this.startingHp = plugin.getConfig().getInt("game.starting-hp", 100);
        this.goldPerRound = plugin.getConfig().getInt("game.gold-per-round", 1);
        this.xpPerRound = plugin.getConfig().getInt("game.xp-per-round", 2);

        // Load XP requirements
        this.xpRequired = new int[9];
        for (int i = 1; i <= 8; i++) {
            xpRequired[i] = plugin.getConfig().getInt("game.xp-required." + i, i * 2);
        }

        initializePlayers();
    }

    /**
     * Initialize game players
     */
    private void initializePlayers() {
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                GamePlayer gamePlayer = new GamePlayer(player, gameId, startingGold, startingLevel, startingHp);
                players.put(playerId, gamePlayer);

                // 각 플레이어에게 아레나 할당
                Optional<Arena> arena = plugin.getArenaManager().allocateArena();
                if (arena.isPresent()) {
                    playerArenas.put(playerId, arena.get());
                    plugin.getLogger().info("Player " + player.getName() + " assigned to arena " + arena.get().getId());
                } else {
                    plugin.getLogger().warning("No arena available for player " + player.getName());
                }

                // 플레이어에게 보스바 표시
                bossBar.addPlayer(player);

                // 스코어보드 초기화
                setupScoreboard(player, gamePlayer);
            }
        }
    }

    /**
     * Start the game
     */
    public void startGame() {
        currentPhase = GamePhase.STARTING;
        currentRound = 1;

        // Teleport all players to lobby spawn
        teleportPlayersToLobby();

        // 모든 플레이어에게 알림
        broadcast("&a게임 시작! 라운드 1이 시작됩니다...");

        // Start first round
        Bukkit.getScheduler().runTaskLater(plugin, this::startPreparationPhase, 60L); // 3 seconds
    }

    /**
     * Teleport all players to lobby spawn
     */
    private void teleportPlayersToLobby() {
        String worldName = plugin.getConfig().getString("arena.lobby-spawn.world", "world");
        double x = plugin.getConfig().getDouble("arena.lobby-spawn.x", 0);
        double y = plugin.getConfig().getDouble("arena.lobby-spawn.y", 64);
        double z = plugin.getConfig().getDouble("arena.lobby-spawn.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("arena.lobby-spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("arena.lobby-spawn.pitch", 0);

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby world not found: " + worldName);
            return;
        }

        org.bukkit.Location lobbyLoc = new org.bukkit.Location(world, x, y, z, yaw, pitch);

        for (GamePlayer gp : players.values()) {
            Player player = gp.getPlayer();
            if (player != null && player.isOnline()) {
                // 관전자 모드로 설정
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(lobbyLoc);
                player.sendMessage("§a게임 로비로 이동하였습니다!");
            }
        }
    }

    /**
     * Start preparation phase
     */
    private void startPreparationPhase() {
        currentPhase = GamePhase.PREPARATION;

        // 보스바 업데이트
        updateBossBar();

        // Give resources and open shop
        for (GamePlayer gp : players.values()) {
            if (gp.isAlive()) {
                Player player = gp.getPlayer();

                // 옵저버 모드로 설정
                player.setGameMode(GameMode.SPECTATOR);

                // 플레이어를 자신의 보드판으로 텔레포트 및 시점 고정
                Arena arena = playerArenas.get(player.getUniqueId());
                if (arena != null) {
                    teleportToArenaView(player, arena);
                }

                // 큰 글자로 안내 메시지 표시
                player.sendTitle("§6§l준비 단계", "§eE키를 눌러 병력을 준비하세요!", 10, 60, 20);

                // Give gold
                int gold = goldPerRound + gp.getWinStreakBonus();
                gp.addGold(gold);

                // Give XP
                if (gp.addExperience(xpPerRound, xpRequired)) {
                    player.sendMessage("§a레벨 업! 현재 레벨: " + gp.getLevel());
                }

                // 준비 단계 시작 시 자동 합성 체크 (벤치 + 보드의 모든 유닛)
                int upgraded = gp.autoUpgradeUnits();
                if (upgraded > 0) {
                    player.sendMessage("§a" + upgraded + "개 유닛이 자동 합성되었습니다!");
                }

                // Send notification
                player.sendMessage("§6=== 라운드 " + currentRound + " ===");
                player.sendMessage("§e준비 단계: " + preparationTime + "초");
                player.sendMessage("§6골드: +" + gold + " (총: " + gp.getGold() + ")");
                player.sendMessage("");
                player.sendMessage("§a§lE키를 눌러 인벤토리를 열고 배치/상점을 사용하세요!");

                // 새 라운드 시작 시 상점 리셋 (새로운 유닛 풀 생성)
                plugin.getInventoryGUIManager().resetPlayerShop(player.getUniqueId());

                // Setup inventory-based GUI system
                plugin.getInventoryGUIManager().setupGameInventory(player, gp);
            }
        }

        broadcast("&e라운드 " + currentRound + " - 준비 단계 (" + preparationTime + "초)");

        // 모든 플레이어 스코어보드 업데이트
        updateAllScoreboards();

        // 남은 시간 초기화 및 타이머 시작
        phaseTimeRemaining = preparationTime;
        startBossBarTimer();

        // Schedule combat phase
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
            this::startCombatPhase,
            preparationTime * 20L);
    }

    /**
     * 플레이어를 아레나 시점으로 텔레포트
     * 8x6 보드판이 보이는 위치로 이동 (시점은 자유롭게)
     */
    private void teleportToArenaView(Player player, Arena arena) {
        Location pos1 = arena.getPos1();
        Location pos2 = arena.getPos2();

        // 8x6 보드판의 중심 좌표 계산
        double centerX = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;
        double boardY = Math.min(pos1.getY(), pos2.getY());

        // 카메라 위치: 보드판 중심 위쪽
        // 플레이어가 자유롭게 시점을 돌려볼 수 있도록 위치만 설정
        double cameraY = boardY + 8; // 8블록 위
        double cameraZ = centerZ + 5; // 보드 뒤쪽으로 5블록

        Location cameraPos = new Location(pos1.getWorld(), centerX, cameraY, cameraZ);

        // 시점은 설정하지 않음 - 플레이어가 자유롭게 돌려볼 수 있음
        // pitch와 yaw를 설정하지 않으면 플레이어의 현재 시점 유지

        player.teleport(cameraPos);
        plugin.getLogger().info("Player " + player.getName() + " teleported to arena at " +
                               String.format("%.1f, %.1f, %.1f", centerX, cameraY, cameraZ));
    }

    /**
     * Start combat phase
     */
    private void startCombatPhase() {
        // Determine if PVE or PVP
        boolean isPVE = (currentRound % 4 == 0 || currentRound % 7 == 0);

        if (isPVE) {
            currentPhase = GamePhase.COMBAT_PVE;
            broadcast("&cPVE 전투 단계 - 몬스터와 싸우세요!");
            startPVECombats();
        } else {
            currentPhase = GamePhase.COMBAT_PVP;
            broadcast("&cPVP 전투 단계 - 다른 플레이어와 싸우세요!");
            matchPlayers();
        }

        // 보스바 업데이트 및 타이머 시작
        phaseTimeRemaining = combatTime;
        updateBossBar();
        startBossBarTimer();

        // Schedule round end
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
            this::endRound,
            combatTime * 20L);
    }

    /**
     * Start PVE combat for all alive players
     */
    private void startPVECombats() {
        for (GamePlayer gp : players.values()) {
            if (gp.isAlive()) {
                plugin.getCombatManager().startPVECombat(gp, currentRound);
            }
        }
    }

    /**
     * Match players for PVP combat
     */
    private void matchPlayers() {
        List<GamePlayer> alivePlayers = players.values().stream()
            .filter(GamePlayer::isAlive)
            .collect(Collectors.toList());

        Collections.shuffle(alivePlayers);

        // Simple pairing algorithm
        for (int i = 0; i < alivePlayers.size() - 1; i += 2) {
            GamePlayer p1 = alivePlayers.get(i);
            GamePlayer p2 = alivePlayers.get(i + 1);

            p1.getPlayer().sendMessage("§c상대: §e" + p2.getPlayer().getName());
            p2.getPlayer().sendMessage("§c상대: §e" + p1.getPlayer().getName());

            // Start combat
            plugin.getCombatManager().startPVPCombat(p1, p2);
        }

        // 홀수 플레이어인 경우, 한 명은 부전승
        if (alivePlayers.size() % 2 == 1) {
            GamePlayer lastPlayer = alivePlayers.get(alivePlayers.size() - 1);
            lastPlayer.getPlayer().sendMessage("§e이번 라운드는 부전승입니다!");
            lastPlayer.recordWin(); // 부전승 처리
        }
    }

    /**
     * End current round
     */
    private void endRound() {
        currentPhase = GamePhase.ROUND_END;

        // Check for game end
        long aliveCount = players.values().stream().filter(GamePlayer::isAlive).count();

        if (aliveCount <= 1) {
            endGame();
            return;
        }

        // Move to next round
        currentRound++;
        Bukkit.getScheduler().runTaskLater(plugin, this::startPreparationPhase, 60L);
    }

    /**
     * End the game
     */
    public void endGame() {
        currentPhase = GamePhase.GAME_END;

        if (phaseTask != null) {
            phaseTask.cancel();
        }

        // Calculate final placements
        List<GamePlayer> rankedPlayers = players.values().stream()
            .sorted(Comparator.comparingInt(GamePlayer::getHealth).reversed())
            .collect(Collectors.toList());

        for (int i = 0; i < rankedPlayers.size(); i++) {
            rankedPlayers.get(i).setPlacement(i + 1);
        }

        // 실제 게임 인원 수
        int totalPlayers = rankedPlayers.size();

        // Update ratings and notify players
        for (GamePlayer gp : rankedPlayers) {
            int placement = gp.getPlacement();
            Player player = gp.getPlayer();

            if (player != null && player.isOnline()) {
                player.sendMessage("§6=== 게임 종료 ===");
                player.sendMessage("§e순위: §a#" + placement + " §7/ " + totalPlayers + "명");

                // Update profile
                PlayerProfile profile = plugin.getDataManager().getCachedProfile(gp.getPlayerId());
                if (profile != null) {
                    // 인원 수에 따른 점수 가져오기
                    String configPath = "ranking.placement-points-" + totalPlayers + "." + placement;
                    int ratingChange = plugin.getConfig().getInt(configPath, 0);

                    profile.recordGame(placement);

                    if (profile.addRating(ratingChange)) {
                        player.sendMessage("§a등급 변경! " + profile.getRankString());
                    }

                    player.sendMessage("§6점수: " + (ratingChange >= 0 ? "+" : "") + ratingChange +
                                     " §7(" + profile.getRankString() + " - " + profile.getRatingPoints() + "/100)");

                    plugin.getDataManager().saveProfile(profile);
                }
            }
        }

        broadcast("&a게임 종료! 우승자: " + rankedPlayers.get(0).getPlayer().getName());

        // 보스바 제거
        bossBar.removeAll();

        // 아레나 반환
        for (Arena arena : playerArenas.values()) {
            plugin.getArenaManager().releaseArena(arena);
        }
        playerArenas.clear();

        // 플레이어 데이터 복구 및 원래 위치로 복귀
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (GamePlayer gp : rankedPlayers) {
                Player player = gp.getPlayer();
                if (player != null && player.isOnline()) {
                    // 플레이어 데이터 복구 (인벤토리, 위치 등)
                    if (plugin.getPlayerDataManager().restorePlayerData(player)) {
                        player.sendMessage("§a게임이 종료되었습니다. 원래 위치로 돌아갑니다.");
                    } else {
                        // 백업 데이터가 없는 경우 로비로 이동
                        teleportPlayersToLobby();
                        player.sendMessage("§a게임이 종료되었습니다. 로비로 돌아갑니다.");
                    }
                }
            }
        }, 100L); // 5초

        // Remove game after delay
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            plugin.getGameManager().removeGame(gameId), 200L);
    }

    /**
     * 보스바 업데이트
     */
    private void updateBossBar() {
        String phaseType = "";
        BarColor color = BarColor.YELLOW;

        switch (currentPhase) {
            case PREPARATION:
                phaseType = "§e준비 단계";
                color = BarColor.YELLOW;
                break;
            case COMBAT_PVE:
                phaseType = "§cPVE 전투";
                color = BarColor.RED;
                break;
            case COMBAT_PVP:
                phaseType = "§cPVP 전투";
                color = BarColor.RED;
                break;
            default:
                phaseType = "§6게임 진행 중";
                color = BarColor.YELLOW;
                break;
        }

        String title = String.format("§6[라운드 %d] %s §f- 남은 시간: §e%d초",
                                     currentRound, phaseType, phaseTimeRemaining);
        bossBar.setTitle(title);
        bossBar.setColor(color);
    }

    /**
     * 보스바 타이머 시작 (1초마다 업데이트)
     */
    private void startBossBarTimer() {
        // 기존 타이머가 있으면 취소
        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
        }

        // 새 타이머 시작 (정확히 1초마다 = 20틱)
        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (phaseTimeRemaining > 0) {
                    phaseTimeRemaining--;
                    updateBossBar();

                    // 진행 바 업데이트
                    double progress = 0.0;
                    if (currentPhase == GamePhase.PREPARATION) {
                        progress = (double) phaseTimeRemaining / preparationTime;
                    } else if (currentPhase == GamePhase.COMBAT_PVE || currentPhase == GamePhase.COMBAT_PVP) {
                        progress = (double) phaseTimeRemaining / combatTime;
                    }
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                } else {
                    // 시간이 0이 되면 타이머 종료
                    if (bossBarTask != null) {
                        bossBarTask.cancel();
                    }
                }
            }
        }, 20L, 20L); // 1초 후 시작, 1초마다 실행 (20틱 = 1초)
    }

    /**
     * Broadcast message to all players in this game
     */
    public void broadcast(String message) {
        message = message.replace('&', '§');
        for (GamePlayer gp : players.values()) {
            if (gp.getPlayer() != null && gp.getPlayer().isOnline()) {
                gp.getPlayer().sendMessage(message);
            }
        }
    }

    // Getters
    public UUID getGameId() {
        return gameId;
    }

    public Map<UUID, GamePlayer> getPlayers() {
        return new HashMap<>(players);
    }

    public GamePlayer getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public List<UUID> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public Arena getPlayerArena(UUID playerId) {
        return playerArenas.get(playerId);
    }

    /**
     * 스코어보드 설정 (초기화)
     */
    private void setupScoreboard(Player player, GamePlayer gamePlayer) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("matochess", "dummy", "§6§l마토체스");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        playerScoreboards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);

        // 초기 업데이트
        updateScoreboard(player, gamePlayer);
    }

    /**
     * 스코어보드 업데이트 (시너지 + 모든 플레이어 HP/연승/연패)
     */
    private void updateScoreboard(Player player, GamePlayer gamePlayer) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("matochess");
        if (objective == null) return;

        // 기존 스코어 초기화
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int line = 15; // 스코어보드는 아래에서 위로 (15 -> 1)

        // === 시너지 정보 ===
        objective.getScore("§e§l━━━━━ 시너지 ━━━━━").setScore(line--);

        // 보드에 있는 유닛들로 시너지 계산
        List<Unit> boardUnits = new ArrayList<>(gamePlayer.getBoard().values());
        Map<UnitTrait, SynergyManager.ActiveSynergy> activeSynergies =
            plugin.getSynergyManager().calculateSynergies(boardUnits);

        // 모든 시너지 표시 (활성/비활성 모두)
        Map<UnitTrait, Integer> traitCounts = new HashMap<>();
        for (Unit unit : boardUnits) {
            for (UnitTrait trait : unit.getTraits()) {
                traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
            }
        }

        // 모든 시너지를 순회하며 표시
        for (UnitTrait trait : UnitTrait.values()) {
            int count = traitCounts.getOrDefault(trait, 0);

            // 시너지가 하나도 없는 경우는 표시하지 않음
            if (count == 0) continue;

            // 해당 시너지의 모든 티어 정보 가져오기
            List<TraitBonus> bonuses = plugin.getSynergyManager().getTraitBonuses(trait);
            if (bonuses.isEmpty()) continue;

            // 활성화된 시너지인지 확인
            SynergyManager.ActiveSynergy activeSynergy = activeSynergies.get(trait);

            if (activeSynergy != null) {
                // 활성화된 시너지 (골드 색상)
                TraitBonus activeBonus = activeSynergy.getActiveBonus();
                String display = String.format("§6%s §f(%d/%d) §a✓",
                    trait.getDisplayName(),
                    count,
                    activeBonus.getRequiredCount());
                objective.getScore(display).setScore(line--);
            } else {
                // 비활성화된 시너지 (회색)
                // 다음 티어 목표 표시
                TraitBonus nextTier = bonuses.stream()
                    .filter(b -> count < b.getRequiredCount())
                    .min(Comparator.comparingInt(TraitBonus::getRequiredCount))
                    .orElse(null);

                if (nextTier != null) {
                    String display = String.format("§7%s §f(%d/%d)",
                        trait.getDisplayName(),
                        count,
                        nextTier.getRequiredCount());
                    objective.getScore(display).setScore(line--);
                }
            }
        }

        // 시너지가 없으면 메시지 표시
        if (traitCounts.isEmpty()) {
            objective.getScore("§7유닛을 배치하세요").setScore(line--);
        }

        objective.getScore("§r").setScore(line--); // 빈 줄

        // === 플레이어 정보 ===
        objective.getScore("§e§l━━━ 플레이어 ━━━").setScore(line--);

        // 모든 플레이어 정보 표시 (HP 내림차순)
        List<GamePlayer> sortedPlayers = players.values().stream()
            .sorted(Comparator.comparingInt(GamePlayer::getHealth).reversed())
            .collect(Collectors.toList());

        for (GamePlayer gp : sortedPlayers) {
            Player p = gp.getPlayer();
            if (p == null) continue;

            // 자신은 파란색, 다른 플레이어는 회색
            String nameColor = gp.getPlayerId().equals(player.getUniqueId()) ? "§b" : "§7";

            // 생존/탈락 표시
            String status = gp.isAlive() ? "§a❤" : "§c✖";

            // 연승/연패 표시
            String streak = "";
            if (gp.getWinStreak() > 0) {
                streak = " §e⬆" + gp.getWinStreak();
            } else if (gp.getLoseStreak() > 0) {
                streak = " §c⬇" + gp.getLoseStreak();
            }

            String display = String.format("%s%s §f%dHP%s %s",
                nameColor,
                p.getName().length() > 8 ? p.getName().substring(0, 8) : p.getName(),
                gp.getHealth(),
                streak,
                status);

            objective.getScore(display).setScore(line--);
        }

        objective.getScore("§r§r").setScore(line--); // 빈 줄
        objective.getScore("§7라운드: §f" + currentRound).setScore(line--);
    }

    /**
     * 모든 플레이어의 스코어보드 업데이트
     */
    public void updateAllScoreboards() {
        for (GamePlayer gp : players.values()) {
            Player player = gp.getPlayer();
            if (player != null && player.isOnline()) {
                updateScoreboard(player, gp);
            }
        }
    }
}
