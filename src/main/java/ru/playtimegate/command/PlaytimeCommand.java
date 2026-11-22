package ru.playtimegate.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import ru.playtimegate.PlaytimeGatePlugin;
import ru.playtimegate.config.PluginConfig;
import ru.playtimegate.playtime.PlaytimeService;
import ru.playtimegate.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


//playtime [ник|reload]

public final class PlaytimeCommand implements TabExecutor {

    private final PlaytimeGatePlugin plugin;
    private final PluginConfig config;
    private final PlaytimeService playtime;

    public PlaytimeCommand(PlaytimeGatePlugin plugin, PluginConfig config, PlaytimeService playtime) {
        this.plugin = plugin;
        this.config = config;
        this.playtime = playtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("playtimegate.reload")) {
                config.send(sender, "no-permission");
                return true;
            }
            plugin.reload();
            config.send(sender, "reloaded");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                config.send(sender, "players-only");
                return true;
            }
            if (!player.hasPermission("playtimegate.use")) {
                config.send(sender, "no-permission");
                return true;
            }
            showSelf(player);
            return true;
        }

        if (!sender.hasPermission("playtimegate.others")) {
            config.send(sender, "no-permission");
            return true;
        }

        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", args[0]);
            config.send(sender, "player-not-found", placeholders);
            return true;
        }

        showOther(sender, target);
        return true;
    }

    private void showSelf(Player player) {
        long played = playtime.seconds(player);
        long required = config.requiredSeconds();
        boolean restricted = required > 0L && played < required;
        long left = Math.max(0L, required - played);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("played", TimeUtil.format(played));
        placeholders.put("required", TimeUtil.format(required));
        placeholders.put("status", config.status(!restricted));
        placeholders.put("hint", restricted
                ? config.text("hint-closed", Map.of("left", TimeUtil.format(left)))
                : config.text("hint-open"));

        config.send(player, "self", placeholders);
    }

    private void showOther(CommandSender sender, OfflinePlayer target) {
        long played = playtime.seconds(target);
        long required = config.requiredSeconds();
        boolean restricted = required > 0L && played < required;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", displayName(target));
        placeholders.put("played", TimeUtil.format(played));
        placeholders.put("required", TimeUtil.format(required));
        placeholders.put("status", config.status(!restricted));

        config.send(sender, "other", placeholders);
    }

    private static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        if ("reload".startsWith(prefix) && sender.hasPermission("playtimegate.reload")) {
            result.add("reload");
        }
        if (sender.hasPermission("playtimegate.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
        }
        return result;
    }
}
