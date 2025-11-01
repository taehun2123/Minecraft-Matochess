package com.matochess.game;

import com.matochess.MatoChessPlugin;
import com.matochess.combat.CombatInstance;
import com.matochess.core.SynergyManager;
import com.matochess.data.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 하나의 게임 세션을 표현하는 컨트롤러 역할 클래스입니다.
 * 라운드 전환, 전투 매칭, 자원 배분, 탈락 처리 등 전체 흐름을 조율합니다.
 */
public class GameInstance {

    private final MatoChessPlugin plugin;
    private final UUID gameId;
    private final Map<UUID, GamePlayer> players;
    private final List<UUID> playerIds;

    // 각 플레이어에게 할당된 보드 인스턴스
    private final Map<UUID, com.matochess.board.BoardInstance> playerBoards;

    // 플레이어 원본 탭/스코어보드 복구용 저장소
    private final Map<UUID, String> originalPlayerListNames;
    private final Map<UUID, Scoreboard> originalScoreboards;

    private GamePhase currentPhase;
    private int currentRound;
    private BukkitTask phaseTask;

    // 보스바
    private BossBar bossBar;
    private int phaseTimeRemaining;
    private BukkitTask bossBarTask; // 보스바 타이머 태스크

    // 스코어보드 (플레이어별)
    private final Map<UUID, Scoreboard> playerScoreboards;

    // 현재 라운드 전투 매칭 정보 (플레이어 ID -> 상대 플레이어 ID)
    private final Map<UUID, UUID> currentMatchups;

    // 준비 단계 실시간 유닛 프리뷰 관리 (플레이어별)
    private final Map<UUID, BoardManager> previewBoardManagers;
    private final Map<UUID, java.util.concurrent.CompletableFuture<Void>> pendingBoardPreparations;

    // 현재 진행 중인 전투 수
    private int activeCombats;

    // 준비 단계까지 회수되지 않은 드랍 아이템 추적
    private final Map<UUID, List<PendingDrop>> pendingGroundDrops;

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
        this.playerBoards = new ConcurrentHashMap<>();
        this.playerScoreboards = new HashMap<>();
        this.originalPlayerListNames = new HashMap<>();
        this.originalScoreboards = new HashMap<>();
        this.currentMatchups = new HashMap<>();
        this.previewBoardManagers = new ConcurrentHashMap<>();
        this.pendingBoardPreparations = new ConcurrentHashMap<>();
        this.pendingGroundDrops = new HashMap<>();
        this.currentPhase = GamePhase.WAITING;
        this.currentRound = 0;

        // 보스바 생성
        this.bossBar = Bukkit.createBossBar("§6준비 중...", BarColor.YELLOW, BarStyle.SOLID);
        this.bossBar.setVisible(true);

        // 설정 로딩
        this.preparationTime = plugin.getConfig().getInt("game.preparation-time", 30);
        this.combatTime = plugin.getConfig().getInt("game.combat-time", 60);
        this.startingGold = plugin.getConfig().getInt("game.starting-gold", 5);
        this.startingLevel = plugin.getConfig().getInt("game.starting-level", 1);
        this.startingHp = plugin.getConfig().getInt("game.starting-hp", 100);
        this.goldPerRound = plugin.getConfig().getInt("game.gold-per-round", 1);
        this.xpPerRound = plugin.getConfig().getInt("game.xp-per-round", 2);

        // XP 조건 로딩
        this.xpRequired = new int[9];
        for (int i = 1; i <= 8; i++) {
            xpRequired[i] = plugin.getConfig().getInt("game.xp-required." + i, i * 2);
        }

        this.activeCombats = 0;
        initializePlayers();
    }

    /**
     * 게임 플레이어 초기화
     */
    private void initializePlayers() {
        // 모든 플레이어의 로딩된 보드판
        List<java.util.concurrent.CompletableFuture<Void>> loadFutures = new ArrayList<>();

        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                GamePlayer gamePlayer = new GamePlayer(player, gameId, startingGold, startingLevel, startingHp);
                players.put(playerId, gamePlayer);

                // 기존 탭 이름 및 스코어보드 저장 (게임 종료 시 복구)
                originalPlayerListNames.put(playerId, player.getPlayerListName());
                originalScoreboards.put(playerId, player.getScoreboard());

                // 플레이어의 로드판 로딩 (async)
                java.util.concurrent.CompletableFuture<Void> future = plugin.getBoardInstanceManager()
                    .loadPlayerBoard(playerId)
                    .thenAccept(boardInstance -> {
                        playerBoards.put(playerId, boardInstance);
                        plugin.getLogger().info("Player " + player.getName() + " assigned to board at index " +
                            boardInstance.getData().getPositionIndex());

                        // 실시간 프리뷰용 BoardManager 생성
                        previewBoardManagers.put(playerId, new BoardManager(plugin));
                    })
                    .exceptionally(error -> {
                        plugin.getLogger().warning("Failed to load board for player " + player.getName() + ": " + error.getMessage());
                        return null;
                    });

                loadFutures.add(future);

                // 플레이어에게 보스바 표시
                bossBar.addPlayer(player);

                // 스코어보드 초기화
                setupScoreboard(player, gamePlayer);
            }
        }

        // 게임 시작 전 모든 보드판이 로딩될 때까지 대기
        java.util.concurrent.CompletableFuture.allOf(loadFutures.toArray(new java.util.concurrent.CompletableFuture[0]))
            .thenRun(() -> {
                plugin.getLogger().info("All player boards loaded for game " + gameId);
                // 보드 로딩이 완료된 후에만 게임 시작
                Bukkit.getScheduler().runTask(plugin, this::startGame);
            });
    }

    /**
     * Start the game
     */
    private void startGame() {
        currentPhase = GamePhase.STARTING;
        currentRound = 1;

        // Teleport all players to lobby spawn
        teleportPlayersToLobby();
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

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby world not found: " + worldName);
            return;
        }

        org.bukkit.Location lobbyLoc = new org.bukkit.Location(world, x, y, z);

        for (GamePlayer gp : players.values()) {
            Player player = gp.getPlayer();
            if (player != null && player.isOnline()) {
                // Adventure 모드로 설정
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.teleport(lobbyLoc);
                player.sendMessage("§a게임 로비로 이동하였습니다!");
            }
        }
    }

    /**
     * 이자 계산 메소드
     * 10골드당 1골드, 최대 50골드(5골드 이자)
     */
    private int calculateInterest(int currentGold) {
        if (currentGold < 10) {
            return 0;
        }
        // 10골드당 1골드 이자, 최대 50골드까지 계산
        int interestGold = Math.min(currentGold / 10, 5);
        return interestGold;
    }

    /**
     * 준비단계 시작
     */
    private void startPreparationPhase() {
        if (currentPhase == GamePhase.GAME_END) {
            return;
        }

        if (getAlivePlayerCount() <= 1) {
            endGame();
            return;
        }

        currentPhase = GamePhase.PREPARATION;

        // 보스바 업데이트
        updateBossBar();

        // 리소스 제공 및 상점 오픈
        for (GamePlayer gp : players.values()) {
            if (gp.isAlive()) {
                Player player = gp.getPlayer();

                // 어드벤처 모드로 설정
                player.setGameMode(GameMode.ADVENTURE);
                player.setInvulnerable(true);

                // 플레이어를 자신의 보드판으로 텔레포트
                com.matochess.board.BoardInstance boardInstance = playerBoards.get(player.getUniqueId());
                if (boardInstance != null) {
                    teleportToBoardView(player, boardInstance);
                }

                // 큰 글자로 안내 메시지 표시
                player.sendTitle("§6§l준비 단계", "§e[ Shift + F ] 키를 눌러 병력을 준비하세요!", 10, 60, 20);

                // 이자 계산 및 지급 (골드 지급 전에)
                int interest = calculateInterest(gp.getGold());
                if (interest > 0) {
                    gp.addGold(interest);
                }

                // Give gold (기본 + 연승 보너스 + 연패 보너스)
                int baseGold = goldPerRound;
                int winStreakGold = gp.getWinStreakBonus();
                int loseStreakGold = gp.getLoseStreakBonus();
                int totalGold = baseGold + winStreakGold + loseStreakGold;
                gp.addGold(totalGold);

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

                // 골드 획득 상세 표시
                StringBuilder goldMsg = new StringBuilder("§6골드: §f+" + baseGold);
                if (interest > 0) {
                    goldMsg.append(" §b+").append(interest).append("(이자)");
                }
                if (winStreakGold > 0) {
                    goldMsg.append(" §e+").append(winStreakGold).append("(연승)");
                }
                if (loseStreakGold > 0) {
                    goldMsg.append(" §c+").append(loseStreakGold).append("(연패)");
                }
                goldMsg.append(" §7(총: §f").append(gp.getGold()).append("§7)");
                player.sendMessage(goldMsg.toString());
                player.sendMessage("");
                player.sendMessage("§a§l[ Shift + F ] 키를 눌러 배치/상점을 사용하세요!");

                // 새 라운드 시작 시 상점 리셋 (새로운 유닛 풀 생성)
                plugin.getInventoryGUIManager().resetPlayerShop(player.getUniqueId());

                // Setup inventory-based GUI system
                plugin.getInventoryGUIManager().setupGameInventory(player, gp);
            }
        }

        broadcast("&e라운드 " + currentRound + " - 준비 단계 (" + preparationTime + "초)");

        // 모든 플레이어 스코어보드 업데이트
        updateGameDisplays();

        // 배치판의 모든 유닛을 미리보기로 표시
        // 주의: 첫 라운드에는 유닛이 없으므로 호출하지 않음
        // 이후 라운드에서는 이전 라운드의 배치가 유지되므로 프리뷰를 표시
        if (currentRound > 1) {
            refreshAllPlayersPreviewUnits();
        }

        // 남은 시간 초기화 및 타이머 시작
        phaseTimeRemaining = preparationTime;
        startBossBarTimer();

        // Schedule combat phase
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin,
            this::startCombatPhase,
            preparationTime * 20L);
    }

    /**
     * 플레이어를 보드판으로 텔레포트
     * 보드판 위에 평범하게 스폰 (걸어다닐 수 있음)
     */
    private void teleportToBoardView(Player player, com.matochess.board.BoardInstance boardInstance) {
        Location spawnPos = boardInstance.getPlayerSpawnLocation();

        player.teleport(spawnPos);
        plugin.getLogger().info("Player " + player.getName() + " teleported to board at " +
                               String.format("%.1f, %.1f, %.1f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));
    }

    /**
     * 전투 단계 시작
     */
    private void startCombatPhase() {
        // 프리뷰 유닛 제거는 각 전투가 시작될 때 개별적으로 처리됨
        // (전투 보드에 유닛을 스폰한 후에 프리뷰를 제거해야 함)

        // Determine if PVE or PVP
        boolean isPVE = (currentRound == 1 || currentRound % 4 == 0);
        activeCombats = 0;

        if (isPVE) {
            currentPhase = GamePhase.COMBAT_PVE;
            broadcast("&cPVE 전투 단계 - 몬스터와 싸우세요!");
            activeCombats = startPVECombats();
        } else {
            currentPhase = GamePhase.COMBAT_PVP;
            clearExpiredGroundDropsForPVP();
            broadcast("&cPVP 전투 단계 - 다른 플레이어와 싸우세요!");
            activeCombats = matchPlayers();
        }
        updateGameDisplays(); // updateGameDisplays는 updateScoreboard와 updatePlayerList를 모두 호출합니다.

        if (activeCombats == 0) {
            concludeCombatPhaseEarly();
            return;
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
     * 모든 살아있는 플레이어 PVE 매칭
     */
    private int startPVECombats() {
        // PVE에서는 매칭 정보 초기화 (상대가 몬스터)
        currentMatchups.clear();
        int started = 0;

        for (GamePlayer gp : players.values()) {
            if (gp.isAlive()) {
                plugin.getCombatManager().startPVECombat(gp, currentRound, this::handleCombatFinished);
                // PVE는 상대가 없음 (몬스터)
                currentMatchups.put(gp.getPlayerId(), null);
                started++;
            }
        }
        return started;
    }

    /**
     * PVP 전투에서 플레이어 매칭
     */
    private int matchPlayers() {
        // 이전 매칭 정보 초기화
        currentMatchups.clear();
        int started = 0;

        List<GamePlayer> alivePlayers = players.values().stream()
            .filter(GamePlayer::isAlive)
            .collect(Collectors.toList());

        // 랜덤으로 매칭되도록 셔플링
        Collections.shuffle(alivePlayers);

        // for 문 순환 매칭
        for (int i = 0; i < alivePlayers.size() - 1; i += 2) {
            GamePlayer p1 = alivePlayers.get(i);
            GamePlayer p2 = alivePlayers.get(i + 1);

            // 매칭 정보 저장
            currentMatchups.put(p1.getPlayerId(), p2.getPlayerId());
            currentMatchups.put(p2.getPlayerId(), p1.getPlayerId());

            p1.getPlayer().sendMessage("§c상대: §e" + p2.getPlayer().getName());
            p2.getPlayer().sendMessage("§c상대: §e" + p1.getPlayer().getName());

            // 전투 시작 전 보드 상태 로그
            plugin.getLogger().info("Starting PVP combat: " + p1.getPlayer().getName() + " (" + p1.getBoard().size() + " units) vs " + p2.getPlayer().getName() + " (" + p2.getBoard().size() + " units)");

            // 전투 시작
            plugin.getCombatManager().startPVPCombat(p1, p2, this::handleCombatFinished);
            started++;
        }

        // 홀수 플레이어인 경우, 한 명은 부전승
        if (alivePlayers.size() % 2 == 1) {
            GamePlayer lastPlayer = alivePlayers.get(alivePlayers.size() - 1);
            lastPlayer.getPlayer().sendMessage("§e이번 라운드는 부전승입니다!");
            lastPlayer.recordWin(); // 부전승 처리
            // 부전승 플레이어는 상대가 없음
            currentMatchups.put(lastPlayer.getPlayerId(), null);
        }

        return started;
    }

    /**
     * 전투 종료 콜백 처리
     */
    private void handleCombatFinished() {
        if (activeCombats > 0) {
            activeCombats--;
        }

        if (currentPhase == GamePhase.GAME_END) {
            return;
        }

        if (getAlivePlayerCount() <= 1) {
            activeCombats = 0;
            endGame();
            return;
        }

        if (activeCombats == 0 && currentPhase.isCombat()) {
            concludeCombatPhaseEarly();
        }
    }

    private long getAlivePlayerCount() {
        return players.values().stream().filter(GamePlayer::isAlive).count();
    }

    // 항복한 플레이어
    public void surrenderPlayer(UUID playerId) {
        GamePlayer surrendering = players.get(playerId);
        if (surrendering == null || !surrendering.isAlive()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§c이미 게임에서 탈락한 상태입니다.");
            }
            return;
        }

        Player player = surrendering.getPlayer();
        String playerName;
        if (player != null) {
            playerName = player.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            playerName = (offline != null && offline.getName() != null) ? offline.getName() : playerId.toString();
        }

        eliminatePlayer(
            surrendering,
            "§c항복하여 게임에서 탈락했습니다.",
            "§c" + playerName + " §7님이 항복하여 게임에서 탈락했습니다.",
            false,
            true
        );
    }

    /**
     * 지정된 플레이어를 게임에서 탈락 처리합니다.
     * @param target 대상 플레이어
     * @param personalMessage 대상 플레이어에게 표시할 메시지 (null 가능)
     * @param broadcastMessage 전체 방송 메시지 (null 허용, {player} 플레이스홀더 지원)
     * @param fromCombat 전투 흐름에서 호출되었는지 여부 (true면 이미 CombatInstance가 종료 처리 중)
     * @param recordLoss 패배 기록을 갱신할지 여부
     */
    public void eliminatePlayer(GamePlayer target,
                                String personalMessage,
                                String broadcastMessage,
                                boolean fromCombat,
                                boolean recordLoss) {
        if (target == null || !target.isAlive()) {
            return;
        }

        if (recordLoss) {
            target.recordLoss();
        }

        UUID playerId = target.getPlayerId();
        Player player = target.getPlayer();
        String playerName;
        if (player != null && player.isOnline()) {
            playerName = player.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            playerName = (offline != null && offline.getName() != null) ? offline.getName() : playerId.toString();
        }

        target.setHealth(0);
        target.setAlive(false);
        target.clearAllUnits();
        clearAllPreviewUnits(playerId);

        if (!fromCombat) {
            CombatInstance combat = plugin.getCombatManager().getCombatByPlayer(playerId);
            if (combat != null) {
                combat.forfeitPlayer(playerId);
            }
        }

        plugin.getBoardInstanceManager().scheduleUnload(playerId);
        plugin.getInventoryGUIManager().resetPlayerShop(playerId, true);

        currentMatchups.remove(playerId);
        currentMatchups.values().removeIf(opponent -> opponent != null && opponent.equals(playerId));

        if (player != null && player.isOnline()) {
            if (personalMessage != null && !personalMessage.isEmpty()) {
                player.sendMessage(personalMessage);
            }
            player.setGameMode(GameMode.SPECTATOR);
            // 탈락 시 감정 표현을 위해 일관된 사운드를 재생한다.
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.7f, 0.8f);
        }

        if (broadcastMessage != null && !broadcastMessage.isEmpty()) {
            broadcast(broadcastMessage.replace("{player}", playerName));
        } else {
            broadcast("§c" + playerName + " §7님이 탈락했습니다.");
        }

        updateGameDisplays();

        long aliveCount = players.values().stream().filter(GamePlayer::isAlive).count();
        if (aliveCount <= 1 && currentPhase != GamePhase.GAME_END) {
            activeCombats = 0;
            endGame();
            return;
        }
    }

    /**
     * 모든 전투가 끝났을 때 즉시 라운드를 마무리
     */
    private void concludeCombatPhaseEarly() {
        if (!currentPhase.isCombat()) {
            return;
        }

        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
        }
        bossBarTask = null;

        phaseTimeRemaining = 0;
        updateBossBar();

        endRound();
    }

    /**
     * 라운드 종료
     */
    private void endRound() {
        phaseTask = null;
        currentPhase = GamePhase.ROUND_END;

        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
        }
        bossBarTask = null;

        // Check for game end
        long aliveCount = players.values().stream().filter(GamePlayer::isAlive).count();

        if (aliveCount <= 1) {
            endGame();
            return;
        }

        // 다음 라운드 진행
        currentRound++;
        Bukkit.getScheduler().runTaskLater(plugin, this::startPreparationPhase, 60L);
    }

    /**
     * 게임 종료
     */
    public void endGame() {
        currentPhase = GamePhase.GAME_END;
        clearExpiredGroundDropsForPVP();

        if (phaseTask != null) {
            phaseTask.cancel();
        }

        // 최종 순위 계산
        List<GamePlayer> rankedPlayers = players.values().stream()
            .sorted(Comparator.comparingInt(GamePlayer::getHealth).reversed())
            .collect(Collectors.toList());

        for (int i = 0; i < rankedPlayers.size(); i++) {
            rankedPlayers.get(i).setPlacement(i + 1);
        }

        // 실제 게임 인원 수
        int totalPlayers = rankedPlayers.size();

        // 레이팅 업데이트 및 알림
        for (GamePlayer gp : rankedPlayers) {
            int placement = gp.getPlacement();
            Player player = gp.getPlayer();

            String fallbackName = null;
            if (player != null) {
                fallbackName = player.getName();
            }
            if (fallbackName == null || fallbackName.isEmpty()) {
                fallbackName = Optional.ofNullable(Bukkit.getOfflinePlayer(gp.getPlayerId()).getName())
                    .orElse(gp.getPlayerId().toString());
            }

            PlayerProfile profile = plugin.getDataManager().getCachedProfile(gp.getPlayerId());
            if (profile == null) {
                try {
                    profile = plugin.getDataManager()
                        .loadProfile(gp.getPlayerId(), fallbackName)
                        .join();
                } catch (CompletionException ex) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Failed to load profile for player " + gp.getPlayerId(), ex);
                }
            }

            if (profile != null) {
                String configPath = "ranking.placement-points-" + totalPlayers + "." + placement;
                int ratingChange = plugin.getConfig().getInt(configPath, 0);

                profile.recordGame(placement);
                boolean rankChanged = profile.addRating(ratingChange);
                plugin.getDataManager().saveProfile(profile);

                if (player != null && player.isOnline()) {
                    player.sendMessage("§6=== 게임 종료 ===");
                    player.sendMessage("§e순위: §a#" + placement + " §7/ " + totalPlayers + "명");
                    if (rankChanged) {
                        player.sendMessage("§a등급 변경! " + profile.getRankString());
                    }
                    player.sendMessage("§6점수: " + (ratingChange >= 0 ? "+" : "") + ratingChange +
                        " §7(" + profile.getRankString() + " - " + profile.getRatingPoints() + "/100)");
                }
            } else {
                plugin.getLogger().warning("Unable to update rating for player " + gp.getPlayerId() + " - profile unavailable");
            }
        }

        broadcast("&a게임 종료! 우승자: " + rankedPlayers.get(0).getPlayer().getName());

        // 보스바 제거
        bossBar.removeAll();

        // 보스바 타이머 정지
        if (bossBarTask != null && !bossBarTask.isCancelled()) {
            bossBarTask.cancel();
        }

        // 스코어보드 초기화 및 제거
        for (GamePlayer gp : rankedPlayers) {
            Player player = gp.getPlayer();
            if (player != null && player.isOnline()) {
                // 기존 스코어보드 복구 (없으면 메인 스코어보드)
                Scoreboard originalBoard = originalScoreboards.get(player.getUniqueId());
                if (originalBoard != null) {
                    player.setScoreboard(originalBoard);
                } else {
                    ScoreboardManager manager = Bukkit.getScoreboardManager();
                    Scoreboard mainBoard = manager != null ? manager.getMainScoreboard() : null;
                    if (mainBoard != null) {
                        player.setScoreboard(mainBoard);
                    }
                }

                // 탭 리스트 이름 복구 (없으면 플레이어 이름)
                String originalListName = originalPlayerListNames.get(player.getUniqueId());
                if (originalListName != null && !originalListName.isEmpty()) {
                    player.setPlayerListName(originalListName);
                } else {
                    player.setPlayerListName(player.getName());
                }
            }
        }
        playerScoreboards.clear();
        originalScoreboards.clear();
        originalPlayerListNames.clear();

        // 프리뷰 유닛 정리
        clearAllPlayersPreviewUnits();
        previewBoardManagers.clear();

        // 보드 인스턴스 언로드 스케줄
        for (UUID playerId : playerBoards.keySet()) {
            plugin.getBoardInstanceManager().scheduleUnload(playerId);
        }
        playerBoards.clear();
        pendingBoardPreparations.clear();

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
     * 해당 게임에 있는 모든 플레이어에게 알림
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

    public com.matochess.board.BoardInstance getPlayerBoard(UUID playerId) {
        return playerBoards.get(playerId);
    }

    /**
     * 모든 플레이어의 Tab List 이름 (순위, HP, 상태 표시)을 업데이트합니다.
     * 이 업데이트는 모든 게임 참가자에게 전송됩니다.
     */
    private void updatePlayerList() {
        // 1. HP 내림차순으로 정렬하여 순위 확정
        List<GamePlayer> sortedPlayers = players.values().stream()
                .filter(gp -> gp.getPlayer() != null && gp.getPlayer().isOnline())
                .sorted(Comparator.comparingInt(GamePlayer::getHealth).reversed())
                .collect(Collectors.toList());

        // 2. 각 플레이어의 Tab List 이름 업데이트
        for (int i = 0; i < sortedPlayers.size(); i++) {
            GamePlayer gp = sortedPlayers.get(i);
            Player p = gp.getPlayer();
            if (p == null || !p.isOnline()) continue;

            int rank = i + 1;

            // 생존/탈락 표시
            String statusSymbol = gp.isAlive() ? "§a" : "§c✖";

            // 연승/연패 표시
            String streak = "";
            if (gp.getWinStreak() > 0) {
                streak = " §e▲" + gp.getWinStreak();
            } else if (gp.getLoseStreak() > 0) {
                streak = " §c▼" + gp.getLoseStreak();
            }

            // Tab List에 표시할 이름 형식: [순위]. [HP] [연승/연패] [상태] - [이름]
            String tabName = String.format("§7#%d. %s%dHP%s §r§7- %s",
                    rank,
                    statusSymbol,
                    gp.getHealth(),
                    streak,
                    p.getName());

            // 모든 플레이어의 Tab List 이름을 업데이트합니다.
            p.setPlayerListName(tabName);
        }
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
     * 스코어보드 업데이트 (아레나 정보 + 시너지)
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

        int playerGold = gamePlayer.getGold();
        int playerLevel = gamePlayer.getLevel();
        List<Unit> boardUnits = new ArrayList<>(gamePlayer.getBoard().values());
        int unitsOnBoard = boardUnits.size();
        int maxUnits = gamePlayer.getMaxBoardUnits();
        int availableSlots = Math.max(0, maxUnits - unitsOnBoard);
        int currentXp = gamePlayer.getExperience();
        boolean isMaxLevel = playerLevel >= xpRequired.length - 1;
        int xpTarget = isMaxLevel ? currentXp : xpRequired[Math.min(playerLevel, xpRequired.length - 1)];
        String xpDisplay = isMaxLevel ? "MAX" : currentXp + "/" + xpTarget;

        String economyLine = String.format("§6골드 §e%d §7| §b레벨 §f%d §7| §d경험치 §f%s",
            playerGold, playerLevel, xpDisplay);
        String availablePlaceLine = String.format("§6배치 §e%d / §f%d | 잔여 배치 §e%d", unitsOnBoard, maxUnits, availableSlots);

        objective.getScore(economyLine).setScore(line--);
        objective.getScore(availablePlaceLine).setScore(line--);

        objective.getScore("§r§0").setScore(line--); // 구분선 (고유 코드)

        // === 아레나 정보 (전투 중일 때만 표시) ===
        if (currentPhase.isCombat()) {
            objective.getScore("§e§l━━ 아레나 정보 ━━").setScore(line--);

            // 1. 현재 플레이어의 전투 정보 확인 및 팀 색상 결정
            boolean isPlayerTeam1 = true; // 기본값: Team 1 (파란색)
            CombatInstance combat = null;

            // PVP 라운드일 때만 CombatInstance를 확인하여 팀 배정을 동적으로 결정
            if (currentPhase.isPVPCombat()) {
                // 🚨 CombatManager에서 현재 플레이어가 참여하고 있는 전투를 가져옵니다.
                combat = plugin.getCombatManager().getCombatByPlayer(player.getUniqueId());

                if (combat != null && combat.getPlayer2() != null) {
                    // 스코어보드를 보고 있는 플레이어가 combatInstance의 Player2라면,
                    // 이 플레이어는 빨간 팀으로 간주됩니다.
                    if (combat.getPlayer2().getPlayerId().equals(player.getUniqueId())) {
                        isPlayerTeam1 = false; // 나는 Team 2 (빨간색)
                    } else {
                        isPlayerTeam1 = true;  // 나는 Team 1 (파란색)
                    }
                }
            }

            // 동적 색상 정의: 자신의 실제 팀 배정에 따라 색상이 결정됨
            // Team 1 (Player 1) = 파랑(§b), Team 2 (Player 2/몬스터) = 빨강(§c)
            String myColor = isPlayerTeam1 ? "§b" : "§c";
            String opponentColor = isPlayerTeam1 ? "§c" : "§b";

            String myStreak = "";
            if (gamePlayer.getWinStreak() > 0) {
                myStreak = " §e⬆" + gamePlayer.getWinStreak();
            } else if (gamePlayer.getLoseStreak() > 0) {
                myStreak = " §c⬇" + gamePlayer.getLoseStreak();
            }
            String myInfo = String.format("%s%s §f%dHP%s",
                myColor,
                player.getName().length() > 10 ? player.getName().substring(0, 10) : player.getName(),
                gamePlayer.getHealth(),
                myStreak);
            objective.getScore(myInfo).setScore(line--);

            objective.getScore("§7vs").setScore(line--);

            // 3. 상대 정보 (동적 색상 적용)
            UUID opponentId = currentMatchups.get(player.getUniqueId());
            if (opponentId != null) {
                GamePlayer opponent = players.get(opponentId);
                if (opponent != null) {
                    String opponentStreak = "";
                    if (opponent.getWinStreak() > 0) {
                        opponentStreak = " §e⬆" + opponent.getWinStreak();
                    } else if (opponent.getLoseStreak() > 0) {
                        opponentStreak = " §c⬇" + opponent.getLoseStreak();
                    }
                    String opponentInfo = String.format("%s%s §f%dHP%s",
                            opponentColor,
                            opponent.getPlayer().getName().length() > 10 ?
                                    opponent.getPlayer().getName().substring(0, 10) : opponent.getPlayer().getName(),
                            opponent.getHealth(),
                            opponentStreak);
                    objective.getScore(opponentInfo).setScore(line--);
                } else {
                    // 오류 시 상대 색상을 임시로 빨간색으로 표시
                    objective.getScore("§c상대 로드 중...").setScore(line--);
                }
            } else {
                // PVE 또는 부전승
                if (currentPhase == GamePhase.COMBAT_PVE) {
                    // PVE 몬스터는 항상 Team 2(빨간색 영역)에 스폰되므로 §c로 고정
                    objective.getScore("§c몬스터 라운드").setScore(line--);
                } else {
                    objective.getScore("§e부전승").setScore(line--);
                }
            }

            objective.getScore("§r  ").setScore(line--); // 빈 줄
        }

        // 시너지 계산 (activeSynergies 정의)
        final Map<UnitTrait, SynergyManager.ActiveSynergy> activeSynergies =
                plugin.getSynergyManager().calculateSynergies(boardUnits);

        // 특성 카운트 계산 (traitCounts 정의)
        final Map<UnitTrait, Integer> traitCounts = new HashMap<>();
        for (Unit unit : boardUnits) {
            for (UnitTrait trait : unit.getTraits()) {
                traitCounts.put(trait, traitCounts.getOrDefault(trait, 0) + 1);
            }
        }

        // === 시너지 정보 ===
        objective.getScore("§e§l━━━━━ 시너지 ━━━━━").setScore(line--);

        // 시너지 정보를 표시할 리스트
        List<String> synergyLines = new ArrayList<>();

        // 활성화된 시너지 (우선순위 높음)
        for (Map.Entry<UnitTrait, SynergyManager.ActiveSynergy> entry : activeSynergies.entrySet()) {
            UnitTrait trait = entry.getKey();
            SynergyManager.ActiveSynergy activeSynergy = entry.getValue();
            int count = traitCounts.getOrDefault(trait, 0);

            TraitBonus activeBonus = activeSynergy.getActiveBonus();
            String display = String.format("§6%s §f(%d/%d) §a✓",
                    trait.getDisplayName(),
                    count,
                    activeBonus.getRequiredCount());
            synergyLines.add(display);
        }

        // 비활성화된 시너지 중 다음 목표가 있는 시너지 (최대 3개만 표시)
        int maxNextSynergies = 3;
        int addedNext = 0;

        for (UnitTrait trait : UnitTrait.values()) {
            if (synergyLines.size() >= 6) break; // 시너지 표시 최대 6줄 제한

            int count = traitCounts.getOrDefault(trait, 0);
            if (count == 0 || activeSynergies.containsKey(trait)) continue;

            List<TraitBonus> bonuses = plugin.getSynergyManager().getTraitBonuses(trait);

            // 다음 티어 목표 찾기
            TraitBonus nextTier = bonuses.stream()
                    .filter(b -> count < b.getRequiredCount())
                    .min(Comparator.comparingInt(TraitBonus::getRequiredCount))
                    .orElse(null);

            if (nextTier != null) {
                String display = String.format("§7%s §f(%d/%d)",
                        trait.getDisplayName(),
                        count,
                        nextTier.getRequiredCount());
                synergyLines.add(display);
                if (++addedNext >= maxNextSynergies) break;
            }
        }

        // 최종 시너지 라인 출력
        if (synergyLines.isEmpty()) {
            objective.getScore("§7유닛을 배치하세요").setScore(line--);
        } else {
            for (String lineText : synergyLines) {
                objective.getScore(lineText).setScore(line--);
            }
        }

        objective.getScore("§r§r").setScore(line--); // 빈 줄
        objective.getScore("§7라운드: §f" + currentRound).setScore(line--);

        String actionBarMessage;
        if (isMaxLevel) {
            actionBarMessage = String.format("§6골드 §e%d §7| §b레벨 §f%d §7(MAX) §7| §a배치 %d/%d (잔여 %d)",
                playerGold, playerLevel, unitsOnBoard, maxUnits, availableSlots);
        } else {
            actionBarMessage = String.format("§6골드 §e%d §7| §b레벨 §f%d §7(%s) §7| §a배치 %d/%d (잔여 %d)",
                playerGold, playerLevel, xpDisplay, unitsOnBoard, maxUnits, availableSlots);
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage));
    }

    /**
     * 모든 게임 디스플레이 (스코어보드, Tab List)를 업데이트
     */
    public void updateGameDisplays() {
        // 1. 스코어보드 업데이트 (시너지, 라운드, 전투 매칭)
        for (GamePlayer gp : players.values()) {
            Player player = gp.getPlayer();
            if (player != null && player.isOnline()) {
                updateScoreboard(player, gp);
            }
        }
        // 2. Tab List 업데이트 (플레이어 순위 및 상태)
        updatePlayerList();
    }

    /**
     * 드랍된 장비 아이템 추적 구조
     */
    private static class PendingDrop {
        final Item item;
        final UUID equipmentInstanceId;

        PendingDrop(Item item, UUID equipmentInstanceId) {
            this.item = item;
            this.equipmentInstanceId = equipmentInstanceId;
        }

        boolean matches(Item other) {
            return item != null && other != null && item.getUniqueId().equals(other.getUniqueId());
        }

        boolean isAlive() {
            return item != null && item.isValid();
        }
    }

    /**
     * 특정 플레이어의 바닥 드랍 등록
     */
    public void registerPendingGroundDrop(UUID playerId, Item item, UUID equipmentInstanceId) {
        if (playerId == null || item == null || equipmentInstanceId == null) {
            return;
        }
        pendingGroundDrops
            .computeIfAbsent(playerId, id -> new ArrayList<>())
            .add(new PendingDrop(item, equipmentInstanceId));
    }

    /**
     * 플레이어가 드랍 아이템을 회수했을 때 호출하여 추적 목록에서 제거
     */
    public void markGroundDropCollected(UUID playerId, Item item) {
        if (playerId == null || item == null) {
            return;
        }
        List<PendingDrop> drops = pendingGroundDrops.get(playerId);
        if (drops == null) {
            return;
        }

        drops.removeIf(drop -> drop == null || !drop.isAlive() || drop.matches(item));
        if (drops.isEmpty()) {
            pendingGroundDrops.remove(playerId);
        }
    }

    /**
     * 추적 중인 드랍인지 여부 확인
     */
    public boolean isTrackedGroundDrop(UUID playerId, Item item) {
        if (playerId == null || item == null) {
            return false;
        }
        List<PendingDrop> drops = pendingGroundDrops.get(playerId);
        if (drops == null) {
            return false;
        }
        for (PendingDrop drop : drops) {
            if (drop != null && drop.matches(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 다음 PVP 전투가 시작될 때까지 회수되지 않은 장비 드랍을 소멸 처리
     */
    public void clearExpiredGroundDropsForPVP() {
        if (pendingGroundDrops.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, List<PendingDrop>> entry : new HashMap<>(pendingGroundDrops).entrySet()) {
            UUID playerId = entry.getKey();
            GamePlayer gamePlayer = players.get(playerId);
            Player player = gamePlayer != null ? gamePlayer.getPlayer() : null;

            List<PendingDrop> drops = entry.getValue();
            if (drops == null) {
                continue;
            }

            for (PendingDrop drop : drops) {
                if (drop != null && drop.item != null && drop.item.isValid()) {
                    drop.item.remove();
                }
                if (gamePlayer != null && drop != null) {
                    gamePlayer.removeEquipmentByInstanceId(drop.equipmentInstanceId);
                }
            }

            if (gamePlayer != null && player != null && player.isOnline()) {
                player.sendMessage("§c[알림] 준비 단계 동안 회수하지 않은 장비가 소멸되었습니다.");
                plugin.getInventoryGUIManager().refreshGameInventory(player, gamePlayer);
                updateScoreboard(player, gamePlayer);
            }
            pendingGroundDrops.remove(playerId);
        }

        updatePlayerList();
    }

    /**
     * Ensure board instance and preview manager are ready for the given player.
     */
    private java.util.concurrent.CompletableFuture<Void> ensureBoardReady(UUID playerId) {
        BoardManager boardManager = previewBoardManagers.get(playerId);
        com.matochess.board.BoardInstance boardInstance = playerBoards.get(playerId);

        if (boardManager != null && boardInstance != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        return pendingBoardPreparations.computeIfAbsent(playerId, id -> {
            java.util.concurrent.CompletableFuture<Void> readinessFuture = plugin.getBoardInstanceManager()
                .loadPlayerBoard(id)
                .thenCompose(instance -> {
                    java.util.concurrent.CompletableFuture<Void> readyFuture = new java.util.concurrent.CompletableFuture<>();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        playerBoards.put(id, instance);
                        previewBoardManagers.computeIfAbsent(id, key -> new BoardManager(plugin));
                        readyFuture.complete(null);
                    });
                    return readyFuture;
                });

            return readinessFuture.whenComplete((unused, error) -> {
                pendingBoardPreparations.remove(id);
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to prepare board resources for player " + id, error);
                }
            });
        });
    }

    /**
     * 준비 단계에서 유닛을 아레나에 실시간으로 배치 (프리뷰)
     */
    public void spawnPreviewUnit(UUID playerId, Unit unit, Position position) {
        if (!currentPhase.equals(GamePhase.PREPARATION)) {
            return; // 준비 단계가 아니면 무시
        }

        ensureBoardReady(playerId)
            .thenRun(() -> {
                if (!currentPhase.equals(GamePhase.PREPARATION)) {
                    return;
                }

                BoardManager boardManager = previewBoardManagers.get(playerId);
                com.matochess.board.BoardInstance boardInstance = playerBoards.get(playerId);

                if (boardManager == null || boardInstance == null) {
                    plugin.getLogger().warning("Board resources still unavailable for player " + playerId + " - retry later");
                    return;
                }

                LivingEntity entity = boardManager.spawnUnit(unit, position, true, boardInstance);
                if (entity != null) {
                    entity.setAI(false);
                    plugin.getLogger().info("Preview unit spawned: " + unit.getId() + " at " + position + " for player " + playerId);
                } else {
                    plugin.getLogger().warning("Failed to spawn preview unit: " + unit.getId() + " for player " + playerId);
                }
            })
            .exceptionally(error -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to spawn preview unit for player " + playerId + ": " + error.getMessage(), error);
                return null;
            });
    }

    /**
     * 준비 단계에서 유닛을 아레나에서 제거 (프리뷰)
     */
    public void removePreviewUnit(UUID playerId, Unit unit) {
        if (!currentPhase.equals(GamePhase.PREPARATION)) {
            return; // 준비 단계가 아니면 무시
        }

        ensureBoardReady(playerId)
            .thenRun(() -> {
                if (!currentPhase.equals(GamePhase.PREPARATION)) {
                    return;
                }

                BoardManager boardManager = previewBoardManagers.get(playerId);
                if (boardManager == null) {
                    plugin.getLogger().warning("Board resources still unavailable for player " + playerId + " when removing preview unit");
                    return;
                }

                plugin.getLogger().info("Removing preview unit: " + unit.getId() + " (instance: " + unit.getInstanceId() + ") for player " + playerId);
                boardManager.removeEntity(unit);
            })
            .exceptionally(error -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to remove preview unit for player " + playerId + ": " + error.getMessage(), error);
                return null;
            });
    }

    /**
     * 준비 단계에서 모든 프리뷰 유닛 제거 (전투 시작 전)
     */
    public void clearAllPreviewUnits(UUID playerId) {
        BoardManager boardManager = previewBoardManagers.get(playerId);
        if (boardManager != null) {
            boardManager.clearAllUnits();
        }
    }

    /**
     * 준비 단계에서 모든 플레이어의 프리뷰 유닛 제거
     */
    public void clearAllPlayersPreviewUnits() {
        for (UUID playerId : previewBoardManagers.keySet()) {
            clearAllPreviewUnits(playerId);
        }
    }

    /**
     * 배치판의 모든 유닛을 미리보기로 갱신
     */
    public void refreshAllPreviewUnits(UUID playerId) {
        if (!currentPhase.equals(GamePhase.PREPARATION)) {
            return;
        }

        GamePlayer gp = players.get(playerId);
        if (gp == null) {
            return;
        }

        ensureBoardReady(playerId)
            .thenRun(() -> {
                if (!currentPhase.equals(GamePhase.PREPARATION)) {
                    return;
                }

                clearAllPreviewUnits(playerId);

                BoardManager boardManager = previewBoardManagers.get(playerId);
                com.matochess.board.BoardInstance boardInstance = playerBoards.get(playerId);

                if (boardManager != null && boardInstance != null) {
                    gp.getBoard().forEach((position, unit) -> {
                        LivingEntity entity = boardManager.spawnUnit(unit, position, true, boardInstance);
                        if (entity != null) {
                            entity.setAI(false);
                            entity.setInvulnerable(true);
                        }
                    });
                } else {
                    plugin.getLogger().warning("Board resources still unavailable for player " + playerId + " during preview refresh");
                }
            })
            .exceptionally(error -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to refresh preview units for player " + playerId + ": " + error.getMessage(), error);
                return null;
            });
    }

    /**
     * 모든 플레이어의 미리보기 유닛 갱신
     */
    public void refreshAllPlayersPreviewUnits() {
        for (UUID playerId : players.keySet()) {
            refreshAllPreviewUnits(playerId);
        }
    }
}
