package gg.ascent.plugin.zone;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EventsSettings;
import gg.ascent.api.zone.Zone;
import gg.ascent.api.zone.ZoneService;
import gg.ascent.plugin.enchant.runtime.SafeZones;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Zones as circles around the main world's spawn (PRD E8-S1). The radii are read on every call so
 * {@code /ascent reload} takes effect at once.
 */
public final class RadiusZones implements ZoneService, SafeZones {

  private final ConfigService config;

  public RadiusZones(ConfigService config) {
    this.config = config;
  }

  @Override
  public Zone zoneAt(Location location) {
    World world = location.getWorld();
    EventsSettings.Zones zones = config.events().zones();
    if (world == null || !world.getName().equals(zones.world())) {
      return Zone.WILDERNESS;
    }
    Location spawn = world.getSpawnLocation();
    return classify(
        location.getX() - spawn.getX(),
        location.getZ() - spawn.getZ(),
        zones.safezoneRadius(),
        zones.warzoneRadius());
  }

  @Override
  public boolean isSafezone(Location location) {
    return zoneAt(location) == Zone.SAFEZONE;
  }

  /** The circle test, without Bukkit: horizontal distance from spawn against the two radii. */
  public static Zone classify(double dx, double dz, int safezoneRadius, int warzoneRadius) {
    double distanceSquared = dx * dx + dz * dz;
    if (distanceSquared <= (double) safezoneRadius * safezoneRadius) {
      return Zone.SAFEZONE;
    }
    if (distanceSquared <= (double) warzoneRadius * warzoneRadius) {
      return Zone.WARZONE;
    }
    return Zone.WILDERNESS;
  }
}
