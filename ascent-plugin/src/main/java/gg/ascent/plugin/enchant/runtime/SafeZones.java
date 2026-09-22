package gg.ascent.plugin.enchant.runtime;

import org.bukkit.Location;

/** Where enchants never run (PRD E3-S4). Epic 9's combat service replaces the default. */
public interface SafeZones {

  boolean isSafezone(Location location);

  /** Until Epic 9 lands: nowhere is safe. */
  SafeZones NONE = location -> false;
}
