package gg.ascent.api.zone;

import org.bukkit.Location;

/**
 * The spawn safezone and the warzone ring (PRD E8-S1): radii from the main world's spawn, read from
 * {@code events.yml}. Every zone rule in the plugin (enchant effects, combat, building) asks here.
 * Main thread.
 */
public interface ZoneService {

  Zone zoneAt(Location location);

  default boolean isSafezone(Location location) {
    return zoneAt(location) == Zone.SAFEZONE;
  }

  default boolean isWarzone(Location location) {
    return zoneAt(location) == Zone.WARZONE;
  }
}
