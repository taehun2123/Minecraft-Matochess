package com.matochess;

import com.matochess.combat.CombatManager;
import com.matochess.commands.AdminCommand;
import com.matochess.commands.BoardCommand;
import com.matochess.commands.QueueCommand;
import com.matochess.commands.ShopCommand;
import com.matochess.commands.StatsCommand;
import com.matochess.core.EquipmentRegistry;
import com.matochess.core.MonsterRegistry;
import com.matochess.core.SynergyManager;
import com.matochess.core.UnitRegistry;
import com.matochess.data.SQLiteDataManager;
import com.matochess.game.GameManager;
import com.matochess.gui.GUIManager;
import com.matochess.gui.InventoryGUIManager;
import com.matochess.gui.QueueGUI;
import com.matochess.listeners.BoardSetupListener;
import com.matochess.listeners.GUIListener;
import com.matochess.listeners.InventoryGUIListener;
import com.matochess.listeners.ItemDropListener;
import com.matochess.listeners.QueueGUIListener;
import com.matochess.listeners.SpectatorMovementListener;
import com.matochess.managers.ArenaManager;
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
    private GUIManager guiManager;
    private InventoryGUIManager inventoryGUIManager;
    private CombatManager combatManager;
    private MatchmakingManager matchmakingManager;

    // New managers
    private PlayerDataManager playerDataManager;
    private ArenaManager arenaManager;
    private QueueGUI queueGUI;
    private BoardSetupListener boardSetupListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        if (!initializeManagers()) {
            getLogger().severe("Failed to initialize managers. Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        registerCommands();

        // Register events
        registerEvents();

        // Initialize arena world and boards
        initializeArenaWorld();

        getLogger().info("MatoChess plugin has been enabled successfully!");
        getLogger().info("Version: " + getDescription().getVersion());
    }

    /**
     * 아레나 월드 및 기본 보드판 초기화
     */
    private void initializeArenaWorld() {
        getLogger().info("Initializing arena world...");

        // 동기로 월드 및 보드판 생성 (블록 설정은 반드시 동기여야 함)
        getServer().getScheduler().runTask(this, () -> {
            boolean success = arenaManager.initializeDefaultWorld();

            if (success) {
                getLogger().info("Arena world initialized successfully with 40 default boards!");
            } else {
                getLogger().warning("Failed to initialize arena world. Use /mcadmin setworld to create manually.");
            }
        });
    }

    @Override
    public void onDisable() {
        // Save all game data
        if (gameManager != null) {
            gameManager.shutdown();
        }

        // Close database connections
        if (dataManager != null) {
            dataManager.close();
        }

        getLogger().info("MatoChess plugin has been disabled.");
    }

    /**
     * Initialize all plugin managers
     * @return true if successful, false otherwise
     */
    private boolean initializeManagers() {
        try {
            // Unit registry
            this.unitRegistry = new UnitRegistry();
            unitRegistry.registerDefaults();
            getLogger().info("Registered " + unitRegistry.getAllUnitIds().size() + " units");

            // Equipment registry
            this.equipmentRegistry = new EquipmentRegistry();
            equipmentRegistry.registerDefaults();
            getLogger().info("Registered " + equipmentRegistry.getAllEquipmentIds().size() + " equipment");

            // Monster registry
            this.monsterRegistry = new MonsterRegistry();
            getLogger().info("Registered PVE monster waves");

            // Synergy manager
            this.synergyManager = new SynergyManager();
            getLogger().info("SynergyManager initialized");

            // SQLite data manager
            this.dataManager = new SQLiteDataManager(this);
            if (!dataManager.initialize()) {
                getLogger().severe("Failed to initialize SQLite database!");
                return false;
            }

            // GUI manager
            this.guiManager = new GUIManager(this);

            // Inventory GUI manager
            this.inventoryGUIManager = new InventoryGUIManager(this);
            getLogger().info("InventoryGUIManager initialized");

            // Combat manager
            this.combatManager = new CombatManager(this);

            // Game manager
            this.gameManager = new GameManager(this);

            // Matchmaking manager
            this.matchmakingManager = new MatchmakingManager(this);

            // Player data manager
            this.playerDataManager = new PlayerDataManager();
            getLogger().info("PlayerDataManager initialized");

            // Arena manager
            this.arenaManager = new ArenaManager(this);
            getLogger().info("ArenaManager initialized");

            // Queue GUI
            this.queueGUI = new QueueGUI(this);
            getLogger().info("QueueGUI initialized");

            // Board setup listener
            this.boardSetupListener = new BoardSetupListener(this);
            getLogger().info("BoardSetupListener initialized");

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

        getCommand("mcqueue").setExecutor(new QueueCommand(this));
        getCommand("mcstats").setExecutor(new StatsCommand(this));
        getCommand("mcadmin").setExecutor(new AdminCommand(this));

        getLogger().info("Commands registered successfully.");
    }

    /**
     * Register event listeners
     */
    private void registerEvents() {
        getLogger().info("Registering events...");

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // New listeners
        getServer().getPluginManager().registerEvents(boardSetupListener, this);
        getServer().getPluginManager().registerEvents(new QueueGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryGUIListener(this, inventoryGUIManager), this);
        getServer().getPluginManager().registerEvents(new SpectatorMovementListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemDropListener(this), this);

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

    public GUIManager getGUIManager() {
        return guiManager;
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

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public QueueGUI getQueueGUI() {
        return queueGUI;
    }

    public BoardSetupListener getBoardSetupListener() {
        return boardSetupListener;
    }

    /**
     * Get the plugin's NamespacedKey for NBT data
     */
    public NamespacedKey getKey() {
        return new NamespacedKey(this, "matochess");
    }
}
