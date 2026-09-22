package gg.ascent.plugin.zone;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import gg.ascent.api.config.EventsSettings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.slf4j.Logger;

/**
 * Mirrors the zones as WorldGuard regions (PRD E8-S1) so other plugins and WorldGuard's own
 * protection see them: {@code spawn} (no PvP, invincible, no build) and {@code warzone} (PvP on, no
 * build). Circles become 32-sided polygons. Ascent enforces its rules itself; this is a courtesy to
 * the rest of the server.
 *
 * <p>Only loaded when the WorldGuard plugin is present.
 */
public final class WorldGuardZones {

  public static final String SPAWN_REGION = "spawn";
  public static final String WARZONE_REGION = "warzone";
  private static final int POINTS = 32;

  private WorldGuardZones() {}

  /** Replaces both regions in {@code world}. Failures are logged, never thrown. */
  public static void sync(World world, EventsSettings.Zones zones, Logger log) {
    RegionManager manager =
        WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    if (manager == null) {
      log.warn(
          "WorldGuard has no region manager for world {}; zones not mirrored.", world.getName());
      return;
    }
    Location spawn = world.getSpawnLocation();
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight();
    ProtectedPolygonalRegion safe =
        new ProtectedPolygonalRegion(
            SPAWN_REGION,
            circle(spawn.getBlockX(), spawn.getBlockZ(), zones.safezoneRadius()),
            minY,
            maxY);
    safe.setPriority(20);
    safe.setFlag(Flags.PVP, StateFlag.State.DENY);
    safe.setFlag(Flags.INVINCIBILITY, StateFlag.State.ALLOW);
    safe.setFlag(Flags.BUILD, StateFlag.State.DENY);
    ProtectedPolygonalRegion war =
        new ProtectedPolygonalRegion(
            WARZONE_REGION,
            circle(spawn.getBlockX(), spawn.getBlockZ(), zones.warzoneRadius()),
            minY,
            maxY);
    war.setPriority(10);
    war.setFlag(Flags.PVP, StateFlag.State.ALLOW);
    war.setFlag(Flags.BUILD, StateFlag.State.DENY);
    manager.removeRegion(SPAWN_REGION);
    manager.removeRegion(WARZONE_REGION);
    manager.addRegion(safe);
    manager.addRegion(war);
    try {
      manager.save();
    } catch (Exception e) {
      log.warn("Could not save the WorldGuard zone regions: {}", e.toString());
    }
    log.info(
        "WorldGuard regions {} (r={}) and {} (r={}) mirrored in {}.",
        SPAWN_REGION,
        zones.safezoneRadius(),
        WARZONE_REGION,
        zones.warzoneRadius(),
        world.getName());
  }

  static List<BlockVector2> circle(int cx, int cz, int radius) {
    List<BlockVector2> points = new ArrayList<>(POINTS);
    for (int i = 0; i < POINTS; i++) {
      double angle = 2 * Math.PI * i / POINTS;
      points.add(
          BlockVector2.at(
              cx + (int) Math.round(radius * Math.cos(angle)),
              cz + (int) Math.round(radius * Math.sin(angle))));
    }
    return points;
  }
}
