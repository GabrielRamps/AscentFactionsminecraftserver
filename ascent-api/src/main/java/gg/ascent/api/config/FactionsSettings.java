package gg.ascent.api.config;

import java.time.Duration;

/** {@code factions.yml}: lifecycle, power, claims, upkeep, relations, F-Top (PRD Epic 6). */
public record FactionsSettings(
    Naming naming,
    long createCost,
    int memberCap,
    int maxAllies,
    Power power,
    Claims claims,
    Upkeep upkeep,
    Home home,
    Fly fly,
    FTop ftop) {

  /** Faction name rules: alphanumeric, unique, within the length bounds. */
  public record Naming(int minLength, int maxLength) {}

  /**
   * Per-player power. Faction power is the sum over members, and allowed claims are faction power
   * divided by {@code claims.powerPerClaim}, rounded down.
   *
   * @param max cap per player
   * @param regenPerMinute gained per minute, online or offline
   * @param deathLoss lost on death outside a safezone
   * @param floor lowest a player can fall to
   */
  public record Power(int max, int regenPerMinute, int deathLoss, int floor) {}

  /**
   * @param powerPerClaim faction power needed per allowed claim
   * @param minDistanceFromSpawn blocks a core claim must be from spawn
   */
  public record Claims(int powerPerClaim, int minDistanceFromSpawn) {}

  /**
   * @param costPerChunkPerDay deducted from the faction vault at daily reset
   * @param graceDays consecutive unpaid days before all claims release
   */
  public record Upkeep(long costPerChunkPerDay, int graceDays) {}

  /** @param warmupSeconds delay before {@code /f home} teleports; cancelled by damage or movement */
  public record Home(int warmupSeconds) {}

  /**
   * @param enemyRadius flight drops when an enemy comes within this many blocks
   * @param fallProtectionSeconds no fall damage for this long after flight drops
   */
  public record Fly(int enemyRadius, int fallProtectionSeconds) {}

  /**
   * @param vaultWeight fraction of vault balance counted toward faction value
   * @param recalcInterval how often F-Top is recomputed
   */
  public record FTop(double vaultWeight, Duration recalcInterval) {}
}
