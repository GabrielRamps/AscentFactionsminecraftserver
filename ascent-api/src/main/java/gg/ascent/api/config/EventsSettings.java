package gg.ascent.api.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** {@code events.yml}: zones, KOTH and Envoys (PRD Epic 8). */
public record EventsSettings(Zones zones, Koth koth, Envoys envoys) {

  /**
   * Radii from spawn, in blocks. Safezone is inside {@code safezoneRadius}; warzone is the ring
   * from there out to {@code warzoneRadius}.
   */
  /**
   * @param world the world the zones live in; every other world is wilderness
   * @param safezoneRadius blocks from spawn that are safe
   * @param warzoneRadius blocks from spawn where the warzone ends
   * @param worldguard whether to mirror the zones as WorldGuard regions when it is installed
   */
  public record Zones(String world, int safezoneRadius, int warzoneRadius, boolean worldguard) {
    public Zones {
      if (warzoneRadius <= safezoneRadius) {
        throw new IllegalArgumentException("warzone radius must exceed safezone radius");
      }
      if (world == null || world.isBlank()) {
        throw new IllegalArgumentException("world must be named");
      }
    }
  }

  /**
   * @param interval time between KOTHs
   * @param warning how far ahead the start is broadcast
   * @param captureTime continuous time alone in the zone to win
   * @param winnerTagSeconds combat tag applied to the winner
   * @param lootbag weighted table the winner's Lootbag rolls from
   */
  public record Koth(
      Duration interval,
      Duration warning,
      Duration captureTime,
      int winnerTagSeconds,
      List<WeightedEntry> lootbag) {
    public Koth {
      lootbag = List.copyOf(lootbag);
    }
  }

  /**
   * @param interval time between envoy drops
   * @param countdown warning broadcast before the drop
   * @param despawn how long an unclaimed crate stays
   * @param crates crate count per tier name
   * @param loot weighted loot table per tier name; populated by E8-S3
   */
  public record Envoys(
      Duration interval,
      Duration countdown,
      Duration despawn,
      Map<String, Integer> crates,
      Map<String, List<WeightedEntry>> loot) {
    public Envoys {
      crates = Map.copyOf(crates);
      loot = Map.copyOf(loot);
    }
  }

  /** One row of a weighted loot table. Weights are relative; they need not sum to 100. */
  public record WeightedEntry(String item, int weight) {
    public WeightedEntry {
      if (weight <= 0) {
        throw new IllegalArgumentException("weight must be positive for " + item);
      }
    }
  }
}
