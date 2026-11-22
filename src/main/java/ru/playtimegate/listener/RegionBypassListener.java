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

/**
 * Открывает приваты игрокам, наигравшим порог.
 *
 * WorldGuard отменяет событие при LOWEST/NORMAL, а этот слушатель висит на
 * MONITOR (после всех) и снимает отмену, если игрок уже не новичок и точка
 * находится в привате. Так "взрослый" получает доступ ко всем приватам,
 * включая собственный, несмотря на шаблонные флаги.
 */
public final class RegionBypassListener implements Listener {

    private static final String BYPASS_PERMISSION = "playtimegate.bypass";

    private final PluginConfig config;
    private final PlaytimeService playtime;
    private final RegionGuard guard;

    public RegionBypassListener(PluginConfig config, PlaytimeService playtime, RegionGuard guard) {
        this.config = config;
        this.playtime = playtime;
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        restore(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        restore(event.getPlayer(), event.getBlockPlaced().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
        restore(event.getPlayer(), block.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked == null) {
            return;
        }
        restore(event.getPlayer(), clicked.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked == null) {
            return;
        }
        restore(event.getPlayer(), clicked.getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!config.blockEntityDamage() || event.getEntity() instanceof Player) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        restore(player, event.getEntity().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (player == null || entity == null) {
            return;
        }
        restore(player, entity.getLocation(), event);
    }

    private void restore(Player player, Location location, Cancellable event) {
        if (player == null || location == null || !event.isCancelled()) {
            return;
        }
        if (config.requiredSeconds() <= 0L) {
            return;
        }
        if (playtime.isRestricted(player) && !player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        if (guard.privateRegionAt(location) == null) {
            return;
        }
        event.setCancelled(false);
    }
}
