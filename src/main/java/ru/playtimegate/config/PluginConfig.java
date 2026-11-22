package ru.playtimegate.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.playtimegate.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Конфигурация плагина. Объект переиспользуется при /playtime reload,
 * поэтому сервисы и слушатели держат ссылку на него, а не на значения.
 */
public final class PluginConfig {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;

    private FileConfiguration raw;

    private long requiredSeconds = 24L * 3_600L;
    private boolean exemptOwnRegions = true;
    private Set<String> exemptRegions = Set.of();
    private Set<String> exemptWorlds = Set.of();
    private boolean blockPhysical = false;
    private boolean blockEntityDamage = true;
    private boolean useActionBar = true;
    private long messageCooldownMillis = 5_000L;
    private boolean regionTemplateEnabled = true;
    private Map<String, Object> regionTemplateFlags = defaultTemplateFlags();
    private boolean regionCreationEnabled = true;
    private List<String> regionCreationPermissions = defaultCreationPermissions();
    private int topSize = 100;
    private int topPageSize = 10;
    private int topRefreshMinutes = 10;
    private int scanBatchSize = 100;
    private boolean afkEnabled = true;
    private long afkThresholdMillis = 300_000L;
    private String afkTabPrefix = "&8[AFK]&r ";
    private String afkDeadPrefix = "&4[СМЕРТЬ]&r ";

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.raw = plugin.getConfig();

        this.requiredSeconds = TimeUtil.parseDuration(raw.getString("required-playtime", "24h"), 24L * 3_600L);
        this.exemptOwnRegions = raw.getBoolean("exempt-own-regions", true);
        this.exemptRegions = lowerCase(raw.getStringList("exempt-regions"));
        this.exemptWorlds = lowerCase(raw.getStringList("exempt-worlds"));
        this.blockPhysical = raw.getBoolean("block.physical-interactions", false);
        this.blockEntityDamage = raw.getBoolean("block.entity-damage", true);
        this.useActionBar = raw.getBoolean("message.use-action-bar", true);
        this.messageCooldownMillis = Math.max(0L, raw.getLong("message.cooldown-seconds", 5L) * 1_000L);
        this.regionTemplateEnabled = raw.getBoolean("region-template.enabled", true);
        this.regionTemplateFlags = templateFlags();
        this.regionCreationEnabled = raw.getBoolean("region-creation.enabled", true);
        this.regionCreationPermissions = creationPermissions();
        this.topSize = Math.max(10, raw.getInt("top.size", 100));
        this.topPageSize = Math.max(5, raw.getInt("top.page-size", 10));
        this.topRefreshMinutes = Math.max(1, raw.getInt("top.refresh-minutes", 10));
        this.scanBatchSize = Math.max(10, raw.getInt("top.scan-batch-size", 100));
        this.afkEnabled = raw.getBoolean("afk.enabled", true);
        this.afkThresholdMillis = Math.max(10L, raw.getLong("afk.threshold-seconds", 300L)) * 1000L;
        this.afkTabPrefix = raw.getString("afk.tab-prefix", "&8[AFK]&r ");
        this.afkDeadPrefix = raw.getString("afk.dead-prefix", "&4[СМЕРТЬ]&r ");

        if (this.requiredSeconds <= 0L) {
            plugin.getLogger().warning("required-playtime <= 0: ограничение приватов фактически выключено.");
        }
    }

    /**
     * Флаги шаблона по умолчанию: приват закрыт для всех, пока порог не наигран
     * (и для владельца тоже — доступ "взрослым" даёт обход WorldGuard, а не флаги).
     */
    private static Map<String, Object> defaultTemplateFlags() {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("build", false);
        flags.put("use", false);
        flags.put("mob-damage", false);
        return flags;
    }

    private static List<String> defaultCreationPermissions() {
        return List.of("worldguard.region.define", "worldguard.region.wand");
    }

    private Map<String, Object> templateFlags() {
        ConfigurationSection section = raw.getConfigurationSection("region-template.flags");
        if (section == null) {
            return defaultTemplateFlags();
        }
        Map<String, Object> flags = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value != null) {
                flags.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        return flags;
    }

    private List<String> creationPermissions() {
        List<String> result = new ArrayList<>();
        for (String value : raw.getStringList("region-creation.permissions")) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result.isEmpty() ? defaultCreationPermissions() : List.copyOf(result);
    }

    private static Set<String> lowerCase(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public long requiredSeconds() {
        return requiredSeconds;
    }

    public boolean exemptOwnRegions() {
        return exemptOwnRegions;
    }

    public Set<String> exemptRegions() {
        return exemptRegions;
    }

    public Set<String> exemptWorlds() {
        return exemptWorlds;
    }

    public boolean blockPhysical() {
        return blockPhysical;
    }

    public boolean blockEntityDamage() {
        return blockEntityDamage;
    }

    public boolean useActionBar() {
        return useActionBar;
    }

    public long messageCooldownMillis() {
        return messageCooldownMillis;
    }

    public boolean regionTemplateEnabled() {
        return regionTemplateEnabled;
    }

    public Map<String, Object> regionTemplateFlags() {
        return regionTemplateFlags;
    }

    public boolean regionCreationEnabled() {
        return regionCreationEnabled;
    }

    public List<String> regionCreationPermissions() {
        return regionCreationPermissions;
    }

    public int topSize() {
        return topSize;
    }

    public int topPageSize() {
        return topPageSize;
    }

    public int topRefreshMinutes() {
        return topRefreshMinutes;
    }

    public int scanBatchSize() {
        return scanBatchSize;
    }

    public boolean afkEnabled() {
        return afkEnabled;
    }

    public long afkThresholdMillis() {
        return afkThresholdMillis;
    }

    /**
     * Префикс в табе для AFK-игрока, с применёнными цветовыми кодами.
     */
    public String afkTabPrefix() {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', afkTabPrefix);
    }

    /**
     * Префикс в табе для игрока на экране смерти, с цветовыми кодами.
     */
    public String afkDeadPrefix() {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', afkDeadPrefix);
    }

    /**
     * Возвращает строки сообщения с подставленными плейсхолдерами.
     */
    public List<String> lines(String key, Map<String, ?> placeholders) {
        List<String> source = new ArrayList<>();
        Object value = raw == null ? null : raw.get("messages." + key);
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element != null) {
                    source.add(String.valueOf(element));
                }
            }
        } else if (value instanceof String string) {
            source.add(string);
        } else {
            source.add("&cВ конфиге отсутствует сообщение: messages." + key);
        }

        List<String> result = new ArrayList<>(source.size());
        for (String line : source) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    public List<String> lines(String key) {
        return lines(key, Map.of());
    }

    /**
     * Первая строка сообщения как Component (для action bar).
     */
    public Component component(String key, Map<String, ?> placeholders) {
        List<String> lines = lines(key, placeholders);
        return LEGACY.deserialize(lines.isEmpty() ? "" : lines.get(0));
    }

    /**
     * Текст сообщения без отправки (для вложенных плейсхолдеров).
     */
    public String text(String key, Map<String, ?> placeholders) {
        List<String> lines = lines(key, placeholders);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public String status(boolean hasAccess) {
        return text(hasAccess ? "status.open" : "status.closed");
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        for (String line : lines(key, placeholders)) {
            sender.sendMessage(LEGACY.deserialize(line));
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private String apply(String text, Map<String, ?> placeholders) {
        if (text == null) {
            return "";
        }
        // {prefix} доступен всегда — его не нужно передавать в каждом вызове.
        String result = text.replace("{prefix}", raw == null ? "" : raw.getString("messages.prefix", ""));
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
