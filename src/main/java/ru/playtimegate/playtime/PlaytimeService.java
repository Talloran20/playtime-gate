package ru.playtimegate.playtime;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import ru.playtimegate.config.PluginConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Доступ к ванильной статистике наигранного времени.
 *
 * <p>Statistic.PLAY_ONE_MINUTE хранится в тиках (20 тиков = 1 секунда),
 * поэтому при переводе в секунды делим на 20.</p>
 */
public final class PlaytimeService {

    private static final long TICKS_PER_SECOND = 20L;

    private final PluginConfig config;
    private final AtomicBoolean readFailureLogged = new AtomicBoolean(false);

    public PlaytimeService(PluginConfig config) {
        this.config = config;
    }

    /**
     * Наигранное время в секундах. Для оффлайн-игрока читается с диска.
     */
    public long seconds(OfflinePlayer player) {
        if (player == null) {
            return 0L;
        }
        try {
            int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return Math.max(0, ticks) / TICKS_PER_SECOND;
        } catch (RuntimeException e) {
            if (readFailureLogged.compareAndSet(false, true)) {
                config.plugin().getLogger().warning(
                        "Не удалось прочитать статистику play_time: " + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
            }
            return 0L;
        }
    }

    /**
     * true, если игроку ещё запрещены действия в приватах.
     */
    public boolean isRestricted(Player player) {
        long required = config.requiredSeconds();
        return required > 0L && seconds(player) < required;
    }

    /**
     * Сколько секунд осталось до снятия ограничения.
     */
    public long remaining(Player player) {
        return Math.max(0L, config.requiredSeconds() - seconds(player));
    }
}
