package gg.ascent.api.zone;

/** Where a location sits relative to spawn (PRD E8-S1). */
public enum Zone {
  /** Inside the spawn radius: no PvP, no damage, no building. */
  SAFEZONE,
  /** The ring around spawn: PvP on, no claiming, no building outside event areas. */
  WARZONE,
  /** Everywhere else, including other worlds. */
  WILDERNESS
}
