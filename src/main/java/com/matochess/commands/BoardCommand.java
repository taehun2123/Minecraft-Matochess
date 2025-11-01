package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import com.matochess.data.BoardData;
import com.matochess.board.BoardInstance;
import com.matochess.gui.BoardShopGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /mcboard command - Manage player boards (GUI 기반)
 */
public class BoardCommand implements CommandExecutor, TabCompleter {

    private final MatoChessPlugin plugin;
    private final BoardShopGUI shopGUI;

    public BoardCommand(MatoChessPlugin plugin, BoardShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        // 인자가 없으면 상점 GUI 열기
        if (args.length == 0) {
            shopGUI.openShop(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showInfo(player);
                break;

            case "shop":
                shopGUI.openShop(player);
                break;

            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showInfo(Player player) {
        BoardData data = plugin.getDataManager().getPlayerBoardSync(player.getUniqueId());

        plugin.getDataManager().loadProfile(player.getUniqueId(), player.getName()).thenAccept(profile -> {
            if (profile == null) {
                player.sendMessage("§c프로필을 불러올 수 없습니다.");
                return;
            }

            player.sendMessage("§6===== 내 보드 정보 =====");
            player.sendMessage("§e보유 BT: §a" + profile.getBoardPoints() + " BT");
            player.sendMessage("");

            if (data == null) {
                player.sendMessage("§7보드 정보가 없습니다. 첫 게임 시작 시 자동으로 생성됩니다.");
                return;
            }

            boolean isLoaded = plugin.getBoardInstanceManager().isBoardLoaded(player.getUniqueId());
            BoardInstance instance = plugin.getBoardInstanceManager().getLoadedBoard(player.getUniqueId());

            player.sendMessage("§e보드 번호: §f#" + data.getPositionIndex());
            player.sendMessage("§e위치: §f" + data.getPositionX() + ", " + data.getPositionY() + ", " + data.getPositionZ());
            player.sendMessage("§e현재 템플릿: §f" + data.getActiveTemplateId());
            player.sendMessage("§e로드 상태: " + (isLoaded ? "§a로드됨" : "§7언로드됨"));

            if (instance != null) {
                player.sendMessage("§e템플릿 이름: §f" + instance.getTemplate().getName());
                player.sendMessage("§e블록 수: §f" + instance.getTemplate().getBlockCount());
            }

            player.sendMessage("§e소유 템플릿: §f" + String.join(", ", data.getOwnedTemplates()));
        });
    }

    private void showHelp(Player player) {
        String baseLabel = plugin.getCommand("mtchess") != null ? plugin.getCommand("mtchess").getName() : "mtchess";
        player.sendMessage("§6===== /" + baseLabel + " board =====");
        player.sendMessage("§e/" + baseLabel + " board §7- 템플릿 상점 열기");
        player.sendMessage("§e/" + baseLabel + " board shop §7- 템플릿 상점 열기");
        player.sendMessage("§e/" + baseLabel + " board info §7- 내 보드 정보 확인");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "shop").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
