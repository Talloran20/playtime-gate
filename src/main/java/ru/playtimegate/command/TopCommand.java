package ru.playtimegate.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import ru.playtimegate.config.PluginConfig;
import ru.playtimegate.playtime.TopCache;
import ru.playtimegate.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


//ptop [страница]

public final class TopCommand implements TabExecutor {

    private final PluginConfig config;
    private final TopCache cache;

    public TopCommand(PluginConfig config, TopCache cache) {
        this.config = config;
        this.cache = cache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playtimegate.top")) {
            config.send(sender, "no-permission");
            return true;
        }

        List<TopCache.Entry> entries = cache.top();
        if (entries.isEmpty()) {
            config.send(sender, "top-empty");
            cache.requestRefresh();
            return true;
        }

        int page = parsePage(args.length > 0 ? args[0] : null);
        int pageSize = config.topPageSize();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.min(Math.max(1, page), pages);

        config.send(sender, "top-header");

        int from = (page - 1) * pageSize;
        int to = Math.min(entries.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            TopCache.Entry entry = entries.get(index);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("position", String.valueOf(index + 1));
            placeholders.put("player", entry.name());
            placeholders.put("played", TimeUtil.format(entry.seconds()));

            config.send(sender, "top-row", placeholders);
        }

        Map<String, String> footer = new HashMap<>();
        footer.put("page", String.valueOf(page));
        footer.put("pages", String.valueOf(pages));
        footer.put("total", String.valueOf(cache.totalPlayers()));
        config.send(sender, "top-footer", footer);

        return true;
    }

    private static int parsePage(String value) {
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("playtimegate.top")) {
            return List.of();
        }

        int pageSize = config.topPageSize();
        int size = cache.top().size();
        int pages = Math.max(1, (size + pageSize - 1) / pageSize);

        String prefix = args[0];
        List<String> result = new ArrayList<>();
        for (int page = 1; page <= Math.min(pages, 10); page++) {
            String value = String.valueOf(page);
            if (value.startsWith(prefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
