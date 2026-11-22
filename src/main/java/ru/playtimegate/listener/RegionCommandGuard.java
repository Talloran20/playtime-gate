package ru.playtimegate.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.playtimegate.config.PluginConfig;
import ru.playtimegate.playtime.PlaytimeService;
import ru.playtimegate.util.TimeUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Контроль команд создания регионов WorldGuard.
 *
 * <p>Новичкам запрещает создание приватов (/rg create и аналоги) с сообщением
 * о недостающем времени. Для "взрослых" подставляет стандартный шаблон флагов
 * на новый регион — WorldGuard создаёт регион асинхронно, поэтому шаблон
 * применяется по опросу, пока регион не появится в менеджере.</p>
 */
public final class RegionCommandGuard implements Listener {

    private static final Set<String> REGION_COMMANDS = Set.of("rg", "region", "regions");
    private static final Set<String> CREATE_SUBCOMMANDS = Set.of("create", "define", "def", "d", "claim");
    private static final int TEMPLATE_WAIT_TICKS = 40;

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final PlaytimeService playtime;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public RegionCommandGuard(JavaPlugin plugin, PluginConfig config, PlaytimeService playtime) {
        this.plugin = plugin;
        this.config = config;
        this.playtime = playtime;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String[] tokens = parse(event.getMessage());
        if (tokens == null) {
            return;
        }
        Player player = event.getPlayer();
        if (playtime.isRestricted(player)) {
            event.setCancelled(true);
            denyCreation(player);
            return;
        }
        scheduleTemplate(tokens, player);
    }

    /**
     * Распознаёт "/rg|region|regions create|define|def|d|claim ...".
     *
     * @return токены команды, либо null, если это не создание региона
     */
    private static String[] parse(String message) {
        if (message == null || !message.startsWith("/")) {
            return null;
        }
        String body = message.substring(1).trim();
        if (body.isEmpty()) {
            return null;
        }
        String[] tokens = body.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        if (!REGION_COMMANDS.contains(tokens[0].toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (!CREATE_SUBCOMMANDS.contains(tokens[1].toLowerCase(Locale.ROOT))) {
            return null;
        }
        return tokens;
    }

    private void scheduleTemplate(String[] tokens, Player player) {
        if (!config.regionTemplateEnabled()) {
            return;
        }
        String worldName = null;
        String regionId = null;
        for (int i = 2; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("-")) {
                if (token.equals("-w") && i + 1 < tokens.length) {
                    worldName = tokens[i + 1];
                    i++;
                }
                continue;
            }
            if (regionId == null) {
                regionId = token;
                break;
            }
        }
        if (regionId == null) {
            return;
        }
        World world = worldName != null ? Bukkit.getWorld(worldName) : player.getWorld();
        RegionManager manager = world != null ? manager(world) : null;
        if (manager == null || manager.hasRegion(regionId)) {
            return;
        }
        watchRegionCreated(manager, regionId);
    }

    private void watchRegionCreated(RegionManager manager, String regionId) {
        final int[] ticks = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (++ticks[0] > TEMPLATE_WAIT_TICKS) {
                taskHolder[0].cancel();
                return;
            }
            if (manager.hasRegion(regionId)) {
                taskHolder[0].cancel();
                applyTemplate(manager, regionId);
            }
        }, 1L, 1L);
        taskHolder[0] = task;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyTemplate(RegionManager manager, String regionId) {
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : config.regionTemplateFlags().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name == null || value == null) {
                continue;
            }
            Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name);
            if (flag == null) {
                plugin.getLogger().warning("Неизвестный флаг WorldGuard в region-template.flags: " + name);
                continue;
            }
            try {
                region.setFlag((Flag) flag, toFlagValue(flag, value));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Не удалось применить флаг " + name + "=" + value
                        + " к региону " + regionId + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Переводит значение из config.yml в тип, который требует сам флаг.
     *
     * <p>State-флаги (build/use/mob-damage) в WorldGuard хранят enum
     * {@link StateFlag.State}, а не Boolean: сырой {@code false} из YAML
     * работает в рантайме, но роняет CCE при сохранении регионов, и WorldGuard
     * тихо выбрасывает такой флаг из файла.</p>
     */
    private static Object toFlagValue(Flag<?> flag, Object value) {
        if (flag instanceof StateFlag stateFlag) {
            if (value instanceof Boolean b) {
                return b ? StateFlag.State.ALLOW : StateFlag.State.DENY;
            }
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if (text.equals("true") || text.equals("1")) {
                return StateFlag.State.ALLOW;
            }
            if (text.equals("false") || text.equals("0")) {
                return StateFlag.State.DENY;
            }
            // "allow"/"deny" — свой словарь WorldGuard; остальное → null (снять флаг).
            return stateFlag.unmarshal(text);
        }
        return value;
    }

    private void denyCreation(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long last = lastNotice.get(id);
        long cooldown = config.messageCooldownMillis();
        if (cooldown > 0L && last != null && now - last < cooldown) {
            return;
        }
        lastNotice.put(id, now);
        if (lastNotice.size() > 1_000) {
            lastNotice.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        }

        long played = playtime.seconds(player);
        long required = config.requiredSeconds();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("played", TimeUtil.format(played));
        placeholders.put("required", TimeUtil.format(required));
        placeholders.put("left", TimeUtil.format(Math.max(0L, required - played)));
        config.send(player, "creation-denied", placeholders);
    }

    private static RegionManager manager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }
}
