package gg.ascent.api.config;

import java.util.Map;

/**
 * {@code spawners.yml}: virtual-yield spawners (PRD E5-S1, E5-S2).
 *
 * @param stackCap maximum spawners stacked in one block
 * @param activeRadiusChunks a spawner accumulates only with a player within this many chunks
 * @param pendingCapPerCount pending kills cap, multiplied by the stack count
 * @param types per-mob settings keyed by mob type name
 */
public record SpawnersSettings(
    int stackCap, int activeRadiusChunks, int pendingCapPerCount, Map<String, SpawnerType> types) {

  public SpawnersSettings {
    types = Map.copyOf(types);
  }

  /**
   * @param ratePerSecond pending kills accumulated per second per spawner in the stack
   */
  public record SpawnerType(double ratePerSecond) {}
}
