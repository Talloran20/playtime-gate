package ru.playtimegate.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.playtimegate.config.PluginConfig;
import ru.playtimegate.playtime.PlaytimeService;
import ru.playtimegate.region.RegionGuard;
import ru.playtimegate.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Блокирует действия новичков в приватах WorldGuard.
 *
 * Слушатели висят на LOWEST и срабатывают раньше WorldGuard, чтобы
 * игрок увидел наше сообщение, а не стандартное "You don't have permission"
 */
public final class RegionActionListener implements Listener {

    private static final String BYPASS_PERMISSION = "playtimegate.bypass";

    private final PluginConfig config;
    private final PlaytimeService playtime;
    private final RegionGuard guard;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public RegionActionListener(PluginConfig config, PlaytimeService playtime, RegionGuard guard) {
        this.config = config;
        this.playtime = playtime;
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        deny(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        deny(event.getPlayer(), event.getBlockPlaced().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_BLOCK;
        boolean physical = action == Action.PHYSICAL && config.blockPhysical();
        if (!rightClick && !physical) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        deny(event.getPlayer(), block.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked == null) {
            return;
        }
        deny(event.getPlayer(), clicked.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked == null) {
            return;
        }
        deny(event.getPlayer(), clicked.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!config.blockEntityDamage() || event.getEntity() instanceof Player) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        deny(player, event.getEntity().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (player == null || entity == null) {
            return;
        }
        deny(player, entity.getLocation(), event);
    }

    private void deny(Player player, Location location, Cancellable event) {
        if (player == null || location == null) {
            return;
        }
        if (!playtime.isRestricted(player)) {
            return;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        String region = guard.blockedRegion(player, location);
        if (region == null) {
            return;
        }

        event.setCancelled(true);
        notifyPlayer(player, region);
    }

    private void notifyPlayer(Player player, String region) {
        long now = System.currentTimeMillis();
        long cooldown = config.messageCooldownMillis();
        UUID id = player.getUniqueId();

        Long last = lastNotice.get(id);
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
        placeholders.put("region", region);
        placeholders.put("played", TimeUtil.format(played));
        placeholders.put("required", TimeUtil.format(required));
        placeholders.put("left", TimeUtil.format(Math.max(0L, required - played)));

        if (config.useActionBar()) {
            player.sendActionBar(config.component("denied", placeholders));
        } else {
            config.send(player, "denied", placeholders);
        }
    }
}
