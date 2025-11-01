package com.matochess;

import com.matochess.combat.CombatManager;
import com.matochess.commands.AdminCommand;
import com.matochess.commands.BoardCommand;
import com.matochess.commands.MtChessCommand;
import com.matochess.commands.QueueCommand;
import com.matochess.commands.StatsCommand;
import com.matochess.core.EquipmentRegistry;
import com.matochess.core.MonsterRegistry;
import com.matochess.core.SynergyManager;
import com.matochess.core.UnitRegistry;
import com.matochess.data.SQLiteDataManager;
import com.matochess.game.GameManager;
import com.matochess.gui.InventoryGUIManager;
import com.matochess.gui.QueueGUI;
import com.matochess.listeners.*;
import com.matochess.board.management.BoardInstanceManager;
import com.matochess.board.management.BoardTemplateManager;
import com.matochess.managers.PlayerDataManager;
import com.matochess.matchmaking.MatchmakingManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * MatoChess - Auto Battler Plugin for Minecraft
 * Inspired by League of Legends: Teamfight Tactics and Auto Chess
 */
public class MatoChessPlugin extends JavaPlugin {

    private static MatoChessPlugin instance;

    // Core managers
    private UnitRegistry unitRegistry;
    private EquipmentRegistry equipmentRegistry;
    private MonsterRegistry monsterRegistry;
    private SynergyManager synergyManager;
    private SQLiteDataManager dataManager;
    private GameManager gameManager;
    private InventoryGUIManager inventoryGUIManager;
    private CombatManager combatManager;
    private MatchmakingManager matchmakingManager;

    // New managers
    private PlayerDataManager playerDataManager;
    private QueueGUI queueGUI;
    private BoardSetupListener boardSetupListener;

    // Board system
    private BoardTemplateManager boardTemplateManager;
    private BoardInstanceManager boardInstanceManager;

    // Shop GUI system
    private com.matochess.gui.BoardShopGUI boardShopGUI;
    private com.matochess.gui.AdminShopGUI adminShopGUI;

    @Override
    public void onEnable() {
        instance = this;

        // config 기본 세팅 저장
        saveDefaultConfig();

        // 매니저 초기 설정
        if (!initializeManagers()) {
            getLogger().severe("Failed to initialize managers. Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 커맨드 등록
        registerCommands();

        // 이벤트 등록
        registerEvents();

        getLogger().info("마토체스 플러그인이 성공적으로 로딩되었습니다!");
        getLogger().info("버전: " + getDescription().getVersion());
        getLogger().info("Board system: Dynamic (unlimited boards with " +
            getConfig().getInt("board.spacing", 300) + " block spacing)");
    }

    @Override
    public void onDisable() {
        // Save all game data
        if (gameManager != null) {
            gameManager.shutdown();
        }

        // Shutdown board instance manager
        if (boardInstanceManager != null) {
            boardInstanceManager.shutdown();
        }

        // Close database connections
        if (dataManager != null) {
            dataManager.close();
        }

        getLogger().info("마토체스 플러그인이 비활성화 되었습니다.");
    }

    /**
     * 모든 플러그인 매너지들 초기화
     * @return true if successful, false otherwise
     */
    private boolean initializeManagers() {
        try {
            // Unit registry
            this.unitRegistry = new UnitRegistry();
            unitRegistry.registerDefaults();
            getLogger().info("유닛 " + unitRegistry.getAllUnitIds().size() + " 개 등록됨");

            // Equipment registry
            this.equipmentRegistry = new EquipmentRegistry();
            equipmentRegistry.registerDefaults();
            getLogger().info("장비 " + equipmentRegistry.getAllEquipmentIds().size() + " 개 등록됨");

            // Monster registry
            this.monsterRegistry = new MonsterRegistry();
            getLogger().info("PVE 몬스터들 등록됨");

            // Synergy manager
            this.synergyManager = new SynergyManager();
            getLogger().info("시너지보너스 초기화 완료");

            // SQLite data manager
            this.dataManager = new SQLiteDataManager(this);
            if (!dataManager.initialize()) {
                getLogger().severe("SQLite 초기 세팅에 실패하였습니다!");
                return false;
            }

            // Inventory GUI manager
            this.inventoryGUIManager = new InventoryGUIManager(this);
            getLogger().info("GUI 세팅됨");

            // Combat manager
            this.combatManager = new CombatManager(this);

            // Game manager
            this.gameManager = new GameManager(this);

            // Matchmaking manager
            this.matchmakingManager = new MatchmakingManager(this);

            // Player data manager
            this.playerDataManager = new PlayerDataManager();
            getLogger().info("PlayerDataManager initialized");

            // Template manager
            this.boardTemplateManager = new BoardTemplateManager(this);
            getLogger().info("TemplateManager initialized");

            // Board instance manager (depends on TemplateManager)
            this.boardInstanceManager = new BoardInstanceManager(this, boardTemplateManager);
            getLogger().info("BoardInstanceManager initialized");

            // Queue GUI
            this.queueGUI = new QueueGUI(this);
            getLogger().info("QueueGUI initialized");

            // Board setup listener
            this.boardSetupListener = new BoardSetupListener(this);
            getLogger().info("BoardSetupListener initialized");

            // Shop GUI system
            this.boardShopGUI = new com.matochess.gui.BoardShopGUI(this);
            getLogger().info("BoardShopGUI initialized");

            this.adminShopGUI = new com.matochess.gui.AdminShopGUI(this);
            getLogger().info("AdminShopGUI initialized");

            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing managers", e);
            return false;
        }
    }

    /**
     * Register plugin commands
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        QueueCommand queueCommand = new QueueCommand(this);
        StatsCommand statsCommand = new StatsCommand(this);
        AdminCommand adminCommand = new AdminCommand(this, adminShopGUI);
        BoardCommand boardCommand = new BoardCommand(this, boardShopGUI);

        MtChessCommand mtChessCommand = new MtChessCommand(this, queueCommand, statsCommand, adminCommand, boardCommand);

        org.bukkit.command.PluginCommand main = getCommand("mtchess");
        if (main != null) {
            main.setExecutor(mtChessCommand);
            main.setTabCompleter(mtChessCommand);
        } else {
            getLogger().severe("/mtchess command not found in plugin.yml - command registration skipped");
        }

        getLogger().info("Commands registered successfully.");
    }

    /**
     * Register event listeners
     */
    private void registerEvents() {
        getLogger().info("Registering events...");

        // Listeners
        getServer().getPluginManager().registerEvents(boardSetupListener, this);
        getServer().getPluginManager().registerEvents(new QueueGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryGUIListener(this, inventoryGUIManager), this);
        getServer().getPluginManager().registerEvents(new ItemDropListener(this), this);
        getServer().getPluginManager().registerEvents(new EquipmentPickupListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorReturnListener(this), this);

        // Shop system listeners
        getServer().getPluginManager().registerEvents(new com.matochess.listeners.BTItemListener(this), this);
        getServer().getPluginManager().registerEvents(boardShopGUI, this);
        getServer().getPluginManager().registerEvents(adminShopGUI, this);
        getServer().getPluginManager().registerEvents(new com.matochess.listeners.ShopSetupListener(this, adminShopGUI), this);

        getLogger().info("Events registered successfully.");
    }

    // Getters for managers
    public static MatoChessPlugin getInstance() {
        return instance;
    }

    public UnitRegistry getUnitRegistry() {
        return unitRegistry;
    }

    public EquipmentRegistry getEquipmentRegistry() {
        return equipmentRegistry;
    }

    public MonsterRegistry getMonsterRegistry() {
        return monsterRegistry;
    }

    public SynergyManager getSynergyManager() {
        return synergyManager;
    }

    public SQLiteDataManager getDataManager() {
        return dataManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public InventoryGUIManager getInventoryGUIManager() {
        return inventoryGUIManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public MatchmakingManager getMatchmakingManager() {
        return matchmakingManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public QueueGUI getQueueGUI() {
        return queueGUI;
    }

    public BoardSetupListener getBoardSetupListener() {
        return boardSetupListener;
    }

    public BoardTemplateManager getTemplateManager() {
        return boardTemplateManager;
    }

    public BoardInstanceManager getBoardInstanceManager() {
        return boardInstanceManager;
    }

    /**
     * Get the plugin's NamespacedKey for NBT data
     */
    public NamespacedKey getKey() {
        return new NamespacedKey(this, "matochess");
    }
}
