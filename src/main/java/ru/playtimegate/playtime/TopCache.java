package ru.playtimegate.playtime;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.playtimegate.config.PluginConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Кэш топа игроков по наигранному времени.
 *
 * <p>Статистика всех известных серверу игроков читается порциями по
 * {@code top.scan-batch-size} за тик в главном потоке: так мы не трогаем
 * файлы игроков из асинхронного потока и не подвешиваем сервер.</p>
 */
public final class TopCache {

    public record Entry(String name, UUID uuid, long seconds) {
    }

    private final Plugin plugin;
    private final PluginConfig config;
    private final PlaytimeService playtime;

    private volatile List<Entry> top = List.of();
    private volatile int totalPlayers;
    private volatile long updatedAt;
    private BukkitTask task;

    private boolean scanning;
    private Deque<OfflinePlayer> queue = new ArrayDeque<>();
    private Map<UUID, Entry> collected = new HashMap<>();

    public TopCache(Plugin plugin, PluginConfig config, PlaytimeService playtime) {
        this.plugin = plugin;
        this.config = config;
        this.playtime = playtime;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<Entry> top() {
        return top;
    }

    public int totalPlayers() {
        return totalPlayers;
    }

    public boolean isReady() {
        return !top.isEmpty();
    }

    /**
     * Пометить кэш устаревшим: пересборка начнётся при следующем тике.
     */
    public void requestRefresh() {
        updatedAt = 0L;
    }

    private void tick() {
        if (scanning) {
            processBatch();
            return;
        }

        long interval = config.topRefreshMinutes() * 60_000L;
        if (System.currentTimeMillis() - updatedAt >= interval) {
            begin();
        }
    }

    private void begin() {
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        if (players.length == 0) {
            updatedAt = System.currentTimeMillis();
            return;
        }

        queue = new ArrayDeque<>(Arrays.asList(players));
        collected = new HashMap<>(Math.min(players.length, 4096));
        scanning = true;
    }

    private void processBatch() {
        int budget = config.scanBatchSize();
        while (budget-- > 0 && !queue.isEmpty()) {
            OfflinePlayer player = queue.poll();
            if (player == null) {
                continue;
            }
            long seconds = playtime.seconds(player);
            collected.put(player.getUniqueId(), new Entry(nameOf(player), player.getUniqueId(), seconds));
        }

        if (queue.isEmpty()) {
            finish();
        }
    }

    private void finish() {
        scanning = false;

        List<Entry> all = new ArrayList<>(collected.values());
        all.sort(Comparator.comparingLong(Entry::seconds).reversed());

        totalPlayers = all.size();
        int limit = Math.min(config.topSize(), all.size());
        top = List.copyOf(all.subList(0, limit));

        collected = new HashMap<>();
        queue = new ArrayDeque<>();
        updatedAt = System.currentTimeMillis();
    }

    private static String nameOf(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? "Неизвестный" : name;
    }
}
