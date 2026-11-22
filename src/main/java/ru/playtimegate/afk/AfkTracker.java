package ru.playtimegate.afk;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.playtimegate.config.PluginConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Детектор AFK: игрок считается афком, если за {@code afk.threshold-seconds}
 * не было «активной» активности.
 *
 * <p>Активная активность: любой ввод с клавиатуры (PlayerInputEvent),
 * правый клик (воздух или блок), работа с инвентарем, чат, команды,
 * взаимодействие с сущностями, скольжение на элитре и самостоятельное
 * передвижение (на земле, без скорости, вне жидкости, вне транспорта).
 *
 * <p>Не считаются активностью: левый клик (автокликер) и принудительное
 * перемещение (поток воды, отброс, жемчужина, поршень, транспорт) —
 * позиция при этом всё равно сбрасывает «отправную точку» сэмплера,
 * поэтому принудительно унесённый игрок потом не получает чужого
 * «самостоятельного» хода.
 *
 * <p>Все методы и поля используются только на основном потоке сервера.
 */
public final class AfkTracker implements Listener {

    /** Колбэк при смене состояния AFK/не-AFK (для мгновенного обновления TAB). */
    @FunctionalInterface
    public interface AfkChangeListener {
        void onAfkChange(Player player, boolean afk);
    }

    /** Минимальное смещение за интервал сэмпла, считающееся «ходил» (блок², 0.5). */
    private static final double MOVEMENT_EPSILON_SQ = 0.25D;
    /** Скорость (lengthSquared) выше этого — принудительное перемещение. */
    private static final double VELOCITY_EPSILON_SQ = 0.01D;
    /** Интервал сэмплера: раз в секунду. */
    private static final long SAMPLE_TICKS = 20L;

    private final JavaPlugin plugin;
    private final PluginConfig config;
    // ConcurrentHashMap: чат-событие Paper приходит с асинхронного потока.
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afkState = new HashMap<>();
    private final Map<UUID, Location> lastSample = new HashMap<>();

    private AfkChangeListener changeListener;
    private org.bukkit.scheduler.BukkitTask sampler;

    public AfkTracker(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setOnAfkChange(AfkChangeListener listener) {
        this.changeListener = listener;
    }

    public void start() {
        sampler = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    public void stop() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
    }

    public boolean isAfk(Player player) {
        if (!config.afkEnabled()) {
            return false;
        }
        return Boolean.TRUE.equals(afkState.get(player.getUniqueId()));
    }

    /**
     * Значение плейсхолдера TAB: префикс мёртвого игрока (экран смерти),
     * префикс афка или пустая строка.
     */
    public String afkValue(Player player) {
        if (player.isDead()) {
            return config.afkDeadPrefix();
        }
        return isAfk(player) ? config.afkTabPrefix() : "";
    }

    public String afkValue(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player != null ? afkValue(player) : "";
    }

    /** Зафиксировать активную активность (обнуляет таймер AFK). */
    public void recordActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void tick() {
        if (!config.afkEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isGliding()) {
                // Полёт на элитре — управляемое действие, считаем активным.
                recordActivity(player);
            }
            sampleMovement(player);
            evaluate(player, now);
        }
    }

    /**
     * Самодвижение: за секунду игрок сместился, стоит на земле, скорости
     * нет (нет отброса/жемчужины), вне жидкости (нет потока) и не едет.
     */
    private void sampleMovement(Player player) {
        Location current = player.getLocation();
        Location last = lastSample.get(player.getUniqueId());
        lastSample.put(player.getUniqueId(), current.clone());
        if (last == null || last.getWorld() == null || !last.getWorld().equals(current.getWorld())) {
            return;
        }
        if (current.distanceSquared(last) < MOVEMENT_EPSILON_SQ) {
            return;
        }
        if (player.getVehicle() != null) {
            return;
        }
        if (player.getVelocity().lengthSquared() > VELOCITY_EPSILON_SQ) {
            return;
        }
        if (!player.isOnGround()) {
            return;
        }
        if (player.isInWater() || player.isInLava()) {
            return;
        }
        recordActivity(player);
    }

    private void evaluate(Player player, long now) {
        Long last = lastActivity.get(player.getUniqueId());
        if (last == null) {
            return;
        }
        boolean afk = now - last >= config.afkThresholdMillis();
        Boolean previous = afkState.get(player.getUniqueId());
        if (previous != null && previous == afk) {
            return;
        }
        afkState.put(player.getUniqueId(), afk);
        if (changeListener != null) {
            changeListener.onAfkChange(player, afk);
        }
    }

    // ------------------------------------------------------------
    //  Сигналы активности
    // ------------------------------------------------------------

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        // Только правый клик: левый — это автокликер, активностью не считаем.
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            recordActivity(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            recordActivity(player);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        recordActivity(event.getPlayer());
    }

    // ------------------------------------------------------------
    //  Жизненный цикл
    // ------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        lastActivity.put(id, System.currentTimeMillis());
        afkState.put(id, false);
        lastSample.put(id, player.getLocation().clone());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastActivity.remove(id);
        afkState.remove(id);
        lastSample.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Смерть — событие: после респавна таймер AFK считается заново.
        UUID id = event.getEntity().getUniqueId();
        lastActivity.put(id, System.currentTimeMillis());
        afkState.put(id, false);
        forceRefresh(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Игрок вернулся с экрана смерти — снимаем "мёртвый" префикс.
        forceRefresh(event.getPlayer().getUniqueId());
    }

    /** Принудительно перегнать значение TAB'а для игрока (смерть/респавн). */
    private void forceRefresh(UUID id) {
        if (changeListener == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            changeListener.onAfkChange(player, isAfk(player));
        }
    }
}
