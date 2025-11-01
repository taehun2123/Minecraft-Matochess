package com.matochess.commands;

import com.matochess.MatoChessPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * /mtchess 메인 명령어. 기존 개별 명령을 하위 인수로 통합하여 처리합니다.
 */
public class MtChessCommand implements CommandExecutor, TabCompleter {

    private final MatoChessPlugin plugin;
    private final QueueCommand queueCommand;
    private final StatsCommand statsCommand;
    private final AdminCommand adminCommand;
    private final BoardCommand boardCommand;

    private static final Map<String, String> LABEL_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("queue", "queue");
        aliases.put("mcqueue", "queue");
        aliases.put("mcq", "queue");
        aliases.put("join", "queue");
        aliases.put("leave", "queue-leave");

        aliases.put("board", "board");
        aliases.put("mcboard", "board");
        aliases.put("mcb", "board");

        aliases.put("stats", "stats");
        aliases.put("mcstats", "stats");

        aliases.put("admin", "admin");
        aliases.put("mcadmin", "admin");
        aliases.put("mca", "admin");

        LABEL_ALIASES = Collections.unmodifiableMap(aliases);
    }

    public MtChessCommand(MatoChessPlugin plugin,
                          QueueCommand queueCommand,
                          StatsCommand statsCommand,
                          AdminCommand adminCommand,
                          BoardCommand boardCommand) {
        this.plugin = plugin;
        this.queueCommand = queueCommand;
        this.statsCommand = statsCommand;
        this.adminCommand = adminCommand;
        this.boardCommand = boardCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String mapped = LABEL_ALIASES.get(normalizedLabel);

        String subCommand;
        String[] subArgs;

        if (mapped != null) {
            if (mapped.equals("queue-leave")) {
                subCommand = "queue";
                subArgs = new String[]{"leave"};
            } else {
                subCommand = mapped;
                subArgs = args;
            }
        } else {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }
            subCommand = args[0].toLowerCase(Locale.ROOT);
            subArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        switch (subCommand) {
            case "queue":
                return queueCommand.onCommand(sender, command, label, subArgs);
            case "board":
                return boardCommand.onCommand(sender, command, label, subArgs);
            case "stats":
                return statsCommand.onCommand(sender, command, label, subArgs);
            case "admin":
                return adminCommand.onCommand(sender, command, label, subArgs);
            case "leave":
                return queueCommand.onCommand(sender, command, label, new String[]{"leave"});
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c알 수 없는 하위 명령입니다: " + subCommand);
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        PluginCommand mainCommand = plugin.getCommand("mtchess");
        String baseLabel = mainCommand != null ? mainCommand.getName() : "mtchess";

        sender.sendMessage("§6§l=== MatoChess 명령어 ===");
        sender.sendMessage("§e/" + baseLabel + " queue §7- 대기열 GUI 열기");
        sender.sendMessage("§e/" + baseLabel + " queue leave §7- 대기열 나가기");
        sender.sendMessage("§e/" + baseLabel + " board [info|shop] §7- 보드 정보 또는 상점 열기");
        sender.sendMessage("§e/" + baseLabel + " stats [플레이어] §7- 통계 확인");
        if (sender.hasPermission("matochess.admin")) {
            sender.sendMessage("§e/" + baseLabel + " admin ... §7- 관리자 명령");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        String mapped = LABEL_ALIASES.get(normalizedAlias);

        if (mapped != null) {
            if (mapped.equals("queue-leave")) {
                return Collections.emptyList();
            }

            if (mapped.equals("board")) {
                return boardCommand.onTabComplete(sender, command, alias, args);
            }

            if (mapped.equals("admin")) {
                return suggestAdmin(args, sender);
            }

            if (mapped.equals("queue")) {
                return Collections.singletonList("leave");
            }
        }

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("queue");
            base.add("board");
            base.add("stats");
            base.add("leave");
            base.add("help");
            if (sender.hasPermission("matochess.admin")) {
                base.add("admin");
            }
            String current = args[0].toLowerCase(Locale.ROOT);
            base.removeIf(option -> !option.startsWith(current));
            return base;
        }

        if (args.length >= 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (sub) {
                case "board":
                    return boardCommand.onTabComplete(sender, command, alias, subArgs);
                case "admin":
                    return suggestAdmin(subArgs, sender);
                case "queue":
                    if (subArgs.length == 1 && "leave".startsWith(subArgs[0].toLowerCase(Locale.ROOT))) {
                        return Collections.singletonList("leave");
                    }
                    return Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    private List<String> suggestAdmin(String[] args, CommandSender sender) {
        if (!sender.hasPermission("matochess.admin")) {
            return Collections.emptyList();
        }

        List<String> base = Arrays.asList("reload", "setlobby", "setboard", "cancelboard", "debug", "games", "queue", "boards", "bt", "shop");
        if (args.length == 0) {
            return base;
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> filtered = new ArrayList<>();
            for (String option : base) {
                if (option.startsWith(current)) {
                    filtered.add(option);
                }
            }
            return filtered;
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("bt")) {
                return Arrays.asList("give", "take", "set", "check", "itemset");
            }
            if (first.equals("shop")) {
                return Collections.singletonList("cancel");
            }
        }

        return Collections.emptyList();
    }
}
