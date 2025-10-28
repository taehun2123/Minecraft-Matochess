package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import com.matochess.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 플레이어 통계 조회 명령어
 */
public class StatsCommand implements CommandExecutor {

    private final MatoChessPlugin plugin;

    public StatsCommand(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;

        // 다른 플레이어의 통계를 보는지 확인
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[0]);
                return true;
            }
        } else {
            // 자신의 통계 보기
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c플레이어 이름을 지정해주세요!");
                return true;
            }
            target = (Player) sender;
        }

        // 비동기로 프로필 로드
        plugin.getDataManager().loadProfile(target.getUniqueId(), target.getName())
            .thenAccept(profile -> {
                // 메인 스레드에서 통계 표시
                Bukkit.getScheduler().runTask(plugin, () -> displayStats(sender, profile));
            });

        return true;
    }

    /**
     * 플레이어 통계 표시
     */
    private void displayStats(CommandSender sender, PlayerProfile profile) {
        sender.sendMessage("§6§l=== " + profile.getPlayerName() + " 통계 ===");
        sender.sendMessage("§e랭크: " + profile.getRankString() + " §7(" + profile.getRatingPoints() + "/100)");
        sender.sendMessage("§e총 게임 수: §f" + profile.getGamesPlayed());
        sender.sendMessage("§e1등: §a" + profile.getWins() + " §7(" + String.format("%.1f", profile.getWinRate()) + "%)");
        sender.sendMessage("§eTop 4: §b" + profile.getTop4() + " §7(" + String.format("%.1f", profile.getTop4Rate()) + "%)");
        sender.sendMessage("§e평균 순위: §f" + String.format("%.2f", profile.getAveragePlacement()));
    }
}
