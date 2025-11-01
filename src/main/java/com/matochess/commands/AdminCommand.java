package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import com.matochess.data.PlayerProfile;
import com.matochess.gui.AdminShopGUI;
import com.matochess.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * MatoChess 관리자 명령어
 */
public class AdminCommand implements CommandExecutor {

    private final MatoChessPlugin plugin;
    private final AdminShopGUI adminShopGUI;

    public AdminCommand(MatoChessPlugin plugin, AdminShopGUI adminShopGUI) {
        this.plugin = plugin;
        this.adminShopGUI = adminShopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("matochess.admin")) {
            sender.sendMessage("§c권한이 없습니다!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage("§a설정 파일이 리로드되었습니다!");
                break;

            case "setlobby":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
                    return true;
                }
                setLobbySpawn((Player) sender);
                break;

            case "setboard":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
                    return true;
                }
                startBoardSetup((Player) sender);
                break;

            case "cancelboard":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
                    return true;
                }
                cancelBoardSetup((Player) sender);
                break;


            case "debug":
                displayDebugInfo(sender);
                break;

            case "games":
                sender.sendMessage("§6활성 게임 수: §e" + plugin.getGameManager().getActiveGames().size());
                break;

            case "queue":
                sender.sendMessage("§6§l=== 큐 대기 현황 ===");
                for (int i = 0; i < 5; i++) {
                    int size = plugin.getMatchmakingManager().getRoomQueueSize(i);
                    sender.sendMessage("§e방 " + (i + 1) + ": §f" + size + "명");
                }
                break;

            case "arenas":
            case "boards":
                displayBoardInfo(sender);
                break;

            case "bt":
            case "boardpoints":
                handleBTCommand(sender, args);
                break;

            case "shop":
                handleShopCommand(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== MatoChess Admin ===");
        sender.sendMessage("§a게임 설정:");
        sender.sendMessage("§e/mcadmin reload §7- 설정 리로드");
        sender.sendMessage("§e/mcadmin setlobby §7- 로비 스폰 설정 (현재 위치)");
        sender.sendMessage("");
        sender.sendMessage("§aBT 관리:");
        sender.sendMessage("§e/mcadmin bt give <플레이어> <양> §7- BT 지급");
        sender.sendMessage("§e/mcadmin bt take <플레이어> <양> §7- BT 차감");
        sender.sendMessage("§e/mcadmin bt set <플레이어> <양> §7- BT 설정");
        sender.sendMessage("§e/mcadmin bt check <플레이어> §7- BT 확인");
        sender.sendMessage("§e/mcadmin bt itemset <양> §7- 손에 든 아이템을 BT 아이템으로 설정");
        sender.sendMessage("");
        sender.sendMessage("§a상점 관리:");
        sender.sendMessage("§e/mcadmin shop §7- 상점 관리 GUI 열기");
        sender.sendMessage("§e/mcadmin shop cancel §7- 진행 중인 설정 취소");
        sender.sendMessage("");
        sender.sendMessage("§a정보 확인:");
        sender.sendMessage("§e/mcadmin debug §7- 디버그 정보");
        sender.sendMessage("§e/mcadmin games §7- 활성 게임 수");
        sender.sendMessage("§e/mcadmin queue §7- 큐 대기 인원");
        sender.sendMessage("§e/mcadmin boards §7- 보드 시스템 정보");
    }

    /**
     * 로비 스폰 위치 설정
     */
    private void setLobbySpawn(Player player) {
        Location loc = player.getLocation();

        plugin.getConfig().set("arena.lobby-spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("arena.lobby-spawn.x", loc.getX());
        plugin.getConfig().set("arena.lobby-spawn.y", loc.getY());
        plugin.getConfig().set("arena.lobby-spawn.z", loc.getZ());
        plugin.getConfig().set("arena.lobby-spawn.yaw", loc.getYaw());
        plugin.getConfig().set("arena.lobby-spawn.pitch", loc.getPitch());

        plugin.saveConfig();

        player.sendMessage("§a로비 스폰이 현재 위치로 설정되었습니다!");
        player.sendMessage("§7월드: §e" + loc.getWorld().getName());
        player.sendMessage(String.format("§7좌표: §e%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
    }

    /**
     * 체스판 템플릿 설정 모드 시작
     */
    private void startBoardSetup(Player player) {
        plugin.getBoardSetupListener().enableSetupMode(player);
    }

    /**
     * 체스판 설정 취소
     */
    private void cancelBoardSetup(Player player) {
        if (plugin.getBoardSetupListener().isInSetupMode(player.getUniqueId())) {
            plugin.getBoardSetupListener().disableSetupMode(player);
            player.sendMessage("§c체스판 설정이 취소되었습니다.");
        } else {
            player.sendMessage("§c현재 체스판 설정 모드가 아닙니다!");
        }
    }


    /**
     * 아레나 상태 정보
     */
    private void displayBoardInfo(CommandSender sender) {
        sender.sendMessage("§6§l=== 보드 시스템 정보 ===");
        sender.sendMessage("§e현재 로드된 보드: §f" + plugin.getBoardInstanceManager().getLoadedBoardCount());
        sender.sendMessage("§e총 할당된 보드: §f" + plugin.getDataManager().getTotalAllocatedBoards());
        sender.sendMessage("§e등록된 템플릿: §f" + plugin.getTemplateManager().getAllTemplates().size());
        sender.sendMessage("§e보드 간격: §f" + plugin.getConfig().getInt("board.spacing", 300) + " 블록");

        // Memory info
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        sender.sendMessage("§e메모리 사용량: §f" + usedMemory + "MB");
    }

    /**
     * 디버그 정보 표시
     */
    private void displayDebugInfo(CommandSender sender) {
        sender.sendMessage("§6§l=== 디버그 정보 ===");
        sender.sendMessage("§e등록된 유닛 수: §f" + plugin.getUnitRegistry().getAllUnitIds().size());
        sender.sendMessage("§e활성 게임: §f" + plugin.getGameManager().getActiveGames().size());
        sender.sendMessage("§e큐 방 수: §f" + plugin.getMatchmakingManager().getRoomCount());
        sender.sendMessage("§eDB 연결: §f" + (plugin.getDataManager() != null ? "활성 (SQLite)" : "비활성"));

        // 보드 시스템 설정
        sender.sendMessage("");
        sender.sendMessage("§6보드 시스템:");
        sender.sendMessage("§e로비 월드: §f" + plugin.getConfig().getString("arena.lobby-spawn.world"));
        sender.sendMessage("§e보드 월드: §f" + plugin.getConfig().getString("board.world-name", "matochessWorld"));
        sender.sendMessage("§e로드된 보드: §f" + plugin.getBoardInstanceManager().getLoadedBoardCount());
        sender.sendMessage("§e총 할당: §f" + plugin.getDataManager().getTotalAllocatedBoards());
        sender.sendMessage("§e템플릿 수: §f" + plugin.getTemplateManager().getAllTemplates().size());
    }

    /**
     * BT 관리 명령어 처리
     */
    private void handleBTCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /mcadmin bt <give|take|set|check> <플레이어> [양]");
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "give":
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /mcadmin bt give <플레이어> <양>");
                    return;
                }
                giveBT(sender, args[2], args[3]);
                break;

            case "take":
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /mcadmin bt take <플레이어> <양>");
                    return;
                }
                takeBT(sender, args[2], args[3]);
                break;

            case "set":
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /mcadmin bt set <플레이어> <양>");
                    return;
                }
                setBT(sender, args[2], args[3]);
                break;

            case "check":
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /mcadmin bt check <플레이어>");
                    return;
                }
                checkBT(sender, args[2]);
                break;

            case "itemset":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /mcadmin bt itemset <BT량>");
                    return;
                }
                setBTItem((Player) sender, args[2]);
                break;

            default:
                sender.sendMessage("§c알 수 없는 명령어입니다. give, take, set, check, itemset 중 하나를 사용하세요.");
                break;
        }
    }

    /**
     * BT 지급
     */
    private void giveBT(CommandSender sender, String playerName, String amountStr) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + playerName);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount <= 0) {
                sender.sendMessage("§c양은 0보다 커야 합니다.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return;
        }

        plugin.getDataManager().loadProfile(target.getUniqueId(), target.getName()).thenAccept(profile -> {
            if (profile == null) {
                sender.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            profile.addBoardPoints(amount);
            plugin.getDataManager().saveProfile(profile);

            sender.sendMessage("§a" + target.getName() + "에게 " + amount + " BT를 지급했습니다. (총: " + profile.getBoardPoints() + " BT)");
            target.sendMessage("§a관리자로부터 " + amount + " BT를 받았습니다! §7(총: §e" + profile.getBoardPoints() + " BT§7)");
        });
    }

    /**
     * BT 차감
     */
    private void takeBT(CommandSender sender, String playerName, String amountStr) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + playerName);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount <= 0) {
                sender.sendMessage("§c양은 0보다 커야 합니다.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return;
        }

        plugin.getDataManager().loadProfile(target.getUniqueId(), target.getName()).thenAccept(profile -> {
            if (profile == null) {
                sender.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            if (profile.subtractBoardPoints(amount)) {
                plugin.getDataManager().saveProfile(profile);
                sender.sendMessage("§a" + target.getName() + "으로부터 " + amount + " BT를 차감했습니다. (남은: " + profile.getBoardPoints() + " BT)");
                target.sendMessage("§c" + amount + " BT가 차감되었습니다. §7(남은: §e" + profile.getBoardPoints() + " BT§7)");
            } else {
                sender.sendMessage("§c" + target.getName() + "의 BT가 부족합니다. (보유: " + profile.getBoardPoints() + " BT)");
            }
        });
    }

    /**
     * BT 설정
     */
    private void setBT(CommandSender sender, String playerName, String amountStr) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + playerName);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount < 0) {
                sender.sendMessage("§c양은 0 이상이어야 합니다.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return;
        }

        plugin.getDataManager().loadProfile(target.getUniqueId(), target.getName()).thenAccept(profile -> {
            if (profile == null) {
                sender.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            profile.setBoardPoints(amount);
            plugin.getDataManager().saveProfile(profile);

            sender.sendMessage("§a" + target.getName() + "의 BT를 " + amount + "로 설정했습니다.");
            target.sendMessage("§aBT가 " + amount + "로 설정되었습니다.");
        });
    }

    /**
     * BT 확인
     */
    private void checkBT(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + playerName);
            return;
        }

        plugin.getDataManager().loadProfile(target.getUniqueId(), target.getName()).thenAccept(profile -> {
            if (profile == null) {
                sender.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            sender.sendMessage("§e" + target.getName() + "의 BT: §f" + profile.getBoardPoints() + " BT");
        });
    }

    /**
     * 손에 든 아이템을 BT 아이템으로 설정
     */
    private void setBTItem(Player sender, String amountStr) {
        ItemStack item = sender.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            sender.sendMessage("§c손에 아이템을 들어주세요!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount <= 0) {
                sender.sendMessage("§cBT량은 0보다 커야 합니다.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return;
        }

        // ItemStack을 Base64로 인코딩
        String base64 = ItemSerializer.itemToBase64(item);

        if (base64 == null) {
            sender.sendMessage("§c아이템 저장에 실패했습니다.");
            return;
        }

        // config에 저장
        plugin.getConfig().set("board-points.item.data", base64);
        plugin.getConfig().set("board-points.item.amount", amount);
        plugin.saveConfig();

        sender.sendMessage("§a손에 든 아이템을 BT 아이템으로 설정했습니다!");
        sender.sendMessage("§7- 아이템: §f" + item.getType().name());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            sender.sendMessage("§7- 이름: §f" + item.getItemMeta().getDisplayName());
        }
        sender.sendMessage("§7- 지급량: §e" + amount + " BT");
    }

    // ========== Shop 관리 명령어 (GUI 기반) ==========

    /**
     * Shop 관리 명령어 처리
     */
    private void handleShopCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
            return;
        }

        Player admin = (Player) sender;

        if (args.length < 2) {
            // 인자가 없으면 상점 GUI 열기
            adminShopGUI.openAdminShop(admin);
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "open":
                adminShopGUI.openAdminShop(admin);
                break;

            case "cancel":
                adminShopGUI.resetSession(admin);
                admin.sendMessage("§a진행 중인 상점 설정이 취소되었습니다.");
                break;

            default:
                admin.sendMessage("§c알 수 없는 명령어입니다.");
                admin.sendMessage("§7/mcadmin shop §7- 상점 관리 GUI 열기");
                admin.sendMessage("§7/mcadmin shop cancel §7- 진행 중인 설정 취소");
                break;
        }
    }
}
