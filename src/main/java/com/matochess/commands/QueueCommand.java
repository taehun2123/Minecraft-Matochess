package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 매치메이킹 큐 참가/나가기 명령어
 */
public class QueueCommand implements CommandExecutor {

    private final MatoChessPlugin plugin;

    public QueueCommand(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다!");
            return true;
        }

        Player player = (Player) sender;

        // 플레이어가 큐에서 나가길 원하는지 확인
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            plugin.getMatchmakingManager().leaveQueue(player);
            return true;
        }

        // 큐 선택 GUI 열기
        plugin.getQueueGUI().openQueueGUI(player);
        return true;
    }
}
