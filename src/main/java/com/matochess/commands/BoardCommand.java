package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import com.matochess.data.GamePlayer;
import com.matochess.game.GameInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 준비 단계에서 보드 배치 GUI를 여는 명령어
 */
public class BoardCommand implements CommandExecutor {

    private final MatoChessPlugin plugin;

    public BoardCommand(MatoChessPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다!");
            return true;
        }

        Player player = (Player) sender;

        // 플레이어가 게임 중인지 확인
        GameInstance game = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (game == null) {
            player.sendMessage("§c게임에 참가하고 있지 않습니다!");
            return true;
        }

        // 게임 플레이어 가져오기
        GamePlayer gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            player.sendMessage("§c게임 플레이어를 찾을 수 없습니다!");
            return true;
        }

        // 플레이어가 생존해 있는지 확인
        if (!gamePlayer.isAlive()) {
            player.sendMessage("§c탈락한 상태에서는 보드를 편집할 수 없습니다!");
            return true;
        }

        // 보드 GUI 열기
        plugin.getGUIManager().openBoardGUI(player, gamePlayer);
        return true;
    }
}
