package ru.playtimegate.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.playtimegate.config.PluginConfig;

import java.util.Locale;
import java.util.Set;

/**
 * Проверка "находится ли точка в привате, где игроку нельзя действовать".
 */
public final class RegionGuard {

    private final PluginConfig config;

    public RegionGuard(PluginConfig config) {
        this.config = config;
    }

    /**
     * @return ID первого региона, который блокирует действие, иначе null
     */
    public String blockedRegion(Player player, Location location) {
        if (player == null || location == null) {
            return null;
        }

        World world = location.getWorld();
        if (world == null || isExemptWorld(world)) {
            return null;
        }

        RegionManager manager = manager(world);
        if (manager == null) {
            return null;
        }

        ApplicableRegionSet regions = manager.getApplicableRegions(
                BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        if (regions.isVirtual() || regions.size() == 0) {
            return null;
        }

        LocalPlayer local = null;
        for (ProtectedRegion region : regions) {
            String id = region.getId();

            // __global__ и прочие служебные регионы не считаем приватами.
            if (id.startsWith("__")) {
                continue;
            }
            if (config.exemptRegions().contains(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (config.exemptOwnRegions()) {
                if (local == null) {
                    local = WorldGuardPlugin.inst().wrapPlayer(player);
                }
                // Свои регионы и регионы, где игрок участник (в т.ч. через группу), не трогаем.
                if (region.isMember(local)) {
                    continue;
                }
            }

            return id;
        }

        return null;
    }

    /**
     * ID первого "приватного" региона, содержащего точку, без проверки участия игрока.
     *
     * <p>Нужен для разблокировки игроков, наигравших порог: им открыт любой приват,
     * включая собственный, поэтому членство здесь не проверяется.</p>
     */
    public String privateRegionAt(Location location) {
        if (location == null) {
            return null;
        }

        World world = location.getWorld();
        if (world == null || isExemptWorld(world)) {
            return null;
        }

        RegionManager manager = manager(world);
        if (manager == null) {
            return null;
        }

        ApplicableRegionSet regions = manager.getApplicableRegions(
                BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        if (regions.isVirtual() || regions.size() == 0) {
            return null;
        }

        for (ProtectedRegion region : regions) {
            String id = region.getId();
            if (id.startsWith("__")) {
                continue;
            }
            if (config.exemptRegions().contains(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            return id;
        }

        return null;
    }

    private boolean isExemptWorld(World world) {
        Set<String> worlds = config.exemptWorlds();
        if (worlds.isEmpty()) {
            return false;
        }
        return worlds.contains(world.getName().toLowerCase(Locale.ROOT))
                || worlds.contains(world.getKey().toString().toLowerCase(Locale.ROOT));
    }

    private RegionManager manager(World world) {
        var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }
}
