package ru.playtimegate;

import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ru.playtimegate.afk.AfkTracker;
import ru.playtimegate.command.PlaytimeCommand;
import ru.playtimegate.command.TopCommand;
import ru.playtimegate.config.PluginConfig;
import ru.playtimegate.listener.RegionActionListener;
import ru.playtimegate.listener.RegionBypassListener;
import ru.playtimegate.listener.RegionCommandGuard;
import ru.playtimegate.playtime.PlaytimeService;
import ru.playtimegate.playtime.TopCache;
import ru.playtimegate.region.RegionGuard;
import ru.playtimegate.tab.TabAfkBridge;
import ru.playtimegate.util.TimeUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * PlaytimeGate — ограничение действий в приватах WorldGuard по наигранному времени.
 */
public final class PlaytimeGatePlugin extends JavaPlugin {

    /** Плейсхолдер, который надо добавить в формат таб-листа TAB. */
    public static final String AFK_PLACEHOLDER = "%playtimegate-afk%";
    /** Авто-обновление значения TAB'ом (мс, делится на 50 — тик планировщика). */
    private static final int AFK_REFRESH_MS = 10_000;

    private PluginConfig config;
    private PlaytimeService playtime;
    private RegionGuard regionGuard;
    private TopCache topCache;
    private AfkTracker afkTracker;
    private TabAfkBridge tabAfk;
    private final Set<String> grantedNodes = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new PluginConfig(this);

        this.playtime = new PlaytimeService(config);
        this.regionGuard = new RegionGuard(config);
        this.topCache = new TopCache(this, config, playtime);
        this.topCache.start();

        syncCreationPermissions();

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new RegionActionListener(config, playtime, regionGuard), this);
        pluginManager.registerEvents(new RegionBypassListener(config, playtime, regionGuard), this);
        pluginManager.registerEvents(new RegionCommandGuard(this, config, playtime), this);

        setupAfkTracking();

        register("playtime", new PlaytimeCommand(this, config, playtime));
        register("ptop", new TopCommand(config, topCache));

        getLogger().info("PlaytimeGate включён. Порог: " + TimeUtil.format(config.requiredSeconds()));
    }

    @Override
    public void onDisable() {
        if (topCache != null) {
            topCache.stop();
        }
        if (afkTracker != null) {
            afkTracker.stop();
        }
    }

    /**
     * Перезагрузка конфигурации без перезапуска сервера.
     */
    public void reload() {
        config.reload();
        syncCreationPermissions();
        topCache.requestRefresh();
        getLogger().info("Конфигурация перезагружена. Порог: " + TimeUtil.format(config.requiredSeconds()));
    }

    /**
     * Выдаёт игрокам пермишны на создание регионов (worldguard.region.define и т.п.)
     * через зарегистрированные Permission с default: true — работает и без
     * пермишн-плагина, и с LuckPerms/Vault (они уважают registered defaults).
     */
    private void syncCreationPermissions() {
        Set<String> wanted = new HashSet<>();
        if (config.regionCreationEnabled()) {
            wanted.addAll(config.regionCreationPermissions());
        }
        PluginManager pluginManager = getServer().getPluginManager();
        for (String node : wanted) {
            Permission permission = pluginManager.getPermission(node);
            if (permission == null) {
                permission = new Permission(node, "PlaytimeGate: выдаёт " + node + " всем игрокам");
                pluginManager.addPermission(permission);
            }
            permission.setDefault(PermissionDefault.TRUE);
            grantedNodes.add(node);
        }
        for (String node : grantedNodes) {
            if (wanted.contains(node)) {
                continue;
            }
            Permission permission = pluginManager.getPermission(node);
            if (permission != null) {
                permission.setDefault(PermissionDefault.FALSE);
            }
        }
        grantedNodes.retainAll(wanted);
    }

    /**
     * AFK-трекинг + (если TAB включён) плейсхолдер {@code %playtimegate-afk%}.
     * Без TAB трекер работает, но префикс нигде не отображается.
     */
    private void setupAfkTracking() {
        this.afkTracker = new AfkTracker(this, config);

        Plugin tab = getServer().getPluginManager().getPlugin("TAB");
        if (tab != null && tab.isEnabled()) {
            this.tabAfk = TabAfkBridge.create(this, AFK_PLACEHOLDER, AFK_REFRESH_MS,
                    id -> afkTracker.afkValue(id));
            if (tabAfk != null) {
                getLogger().info("Зарегистрирован TAB-плейсхолдер " + AFK_PLACEHOLDER
                        + " — добавьте его в формат таб-листа TAB.");
            }
        } else {
            getLogger().info("TAB не найден — AFK-префикс в табе отключён, остальное работает.");
        }

        afkTracker.setOnAfkChange((player, afk) -> refreshTabAfk(player));
        afkTracker.start();
        getServer().getPluginManager().registerEvents(afkTracker, this);
    }

    /** Мгновенное обновление значения TAB'а при смене состояния AFK (и смерти). */
    private void refreshTabAfk(Player player) {
        if (tabAfk != null) {
            tabAfk.update(player, afkTracker.afkValue(player));
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Команда /" + name + " не объявлена в plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
