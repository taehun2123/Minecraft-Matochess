package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * MatoChess 관리자 명령어
 */
public class AdminCommand implements CommandExecutor {

    private final MatoChessPlugin plugin;

    public AdminCommand(MatoChessPlugin plugin) {
        this.plugin = plugin;
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

            case "setworld":
                setArenaWorld(sender);
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
                displayArenaInfo(sender);
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
        sender.sendMessage("§e/mcadmin setboard §7- 체스판 템플릿 설정 (우클릭 2회)");
        sender.sendMessage("§e/mcadmin cancelboard §7- 체스판 설정 취소");
        sender.sendMessage("§e/mcadmin setworld <월드명> §7- 40개 체스판 생성");
        sender.sendMessage("");
        sender.sendMessage("§a정보 확인:");
        sender.sendMessage("§e/mcadmin debug §7- 디버그 정보");
        sender.sendMessage("§e/mcadmin games §7- 활성 게임 수");
        sender.sendMessage("§e/mcadmin queue §7- 큐 대기 인원");
        sender.sendMessage("§e/mcadmin arenas §7- 아레나 상태");
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
     * 커스텀 보드판으로 아레나 월드 재생성
     */
    private void setArenaWorld(CommandSender sender) {
        // 템플릿 설정 확인
        if (!plugin.getArenaManager().isBoardTemplateConfigured()) {
            sender.sendMessage("§c커스텀 보드판이 설정되지 않았습니다!");
            sender.sendMessage("§e기본 잔디 보드판이 이미 생성되어 있습니다.");
            sender.sendMessage("§e커스텀 보드판을 원하시면 먼저 §6/mcadmin setboard §e명령어를 사용하세요");
            return;
        }

        sender.sendMessage("§a커스텀 보드판으로 아레나 월드를 재생성합니다...");
        sender.sendMessage("§c경고: 기존 월드가 삭제됩니다!");
        sender.sendMessage("§e체스판 생성을 시작합니다... (시간이 걸릴 수 있습니다)");

        // 동기로 월드 재생성 및 커스텀 체스판 생성 (블록 설정은 반드시 동기여야 함)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = plugin.getArenaManager().createWorldAndGenerateArenas();

            if (success) {
                sender.sendMessage("§a커스텀 체스판 생성 완료!");
                sender.sendMessage("§e총 §640개 §e커스텀 체스판이 생성되었습니다");
            } else {
                sender.sendMessage("§c체스판 생성 실패! 로그를 확인하세요");
            }
        });
    }

    /**
     * 아레나 상태 정보
     */
    private void displayArenaInfo(CommandSender sender) {
        sender.sendMessage("§6§l=== 아레나 정보 ===");
        sender.sendMessage("§e전체 아레나: §f" + plugin.getArenaManager().getTotalArenaCount());
        sender.sendMessage("§e사용 가능: §a" + plugin.getArenaManager().getAvailableArenaCount());
        sender.sendMessage("§e사용 중: §c" +
            (plugin.getArenaManager().getTotalArenaCount() - plugin.getArenaManager().getAvailableArenaCount()));
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

        // 아레나 설정
        sender.sendMessage("");
        sender.sendMessage("§6아레나 설정:");
        sender.sendMessage("§e로비 월드: §f" + plugin.getConfig().getString("arena.lobby-spawn.world"));
        sender.sendMessage("§e아레나 월드: §f" + plugin.getConfig().getString("arena.arena-world"));
        sender.sendMessage("§e체스판 템플릿 설정: §f" +
            (plugin.getArenaManager().isBoardTemplateConfigured() ? "완료" : "미완료"));
        sender.sendMessage("§e체스판 개수: §f" + plugin.getArenaManager().getTotalArenaCount());
    }
}
