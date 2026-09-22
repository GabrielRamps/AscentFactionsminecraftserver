package gg.ascent.api.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code kits.yml}: rank-gated kits (PRD E2-S2, E2-S3).
 *
 * @param kits kits in file order, keyed by id
 */
public record KitsSettings(Map<String, Kit> kits) {

  public KitsSettings {
    kits = Map.copyOf(new LinkedHashMap<>(kits));
    if (kits.isEmpty()) {
      throw new IllegalArgumentException("at least one kit is required");
    }
  }

  /** Kits in ascending rank order, then by id. */
  public List<Kit> byRank() {
    return kits.values().stream()
        .sorted(java.util.Comparator.comparingInt(Kit::minRank).thenComparing(Kit::id))
        .toList();
  }

  /**
   * @param id the kit id used in {@code /kit <id>}
   * @param display MiniMessage display name
   * @param minRank the rank that unlocks it
   * @param cooldown time between claims
   * @param items what it gives
   */
  public record Kit(
      String id, String display, int minRank, Duration cooldown, List<KitItem> items) {
    public Kit {
      items = List.copyOf(items);
      if (minRank < 1) {
        throw new IllegalArgumentException("min-rank must be at least 1");
      }
      if (cooldown == null || cooldown.isNegative()) {
        throw new IllegalArgumentException("cooldown cannot be negative");
      }
    }
  }

  /**
   * @param material a Bukkit material name; resolved when the kit is given
   * @param amount stack size
   * @param enchants custom enchant id to level, pre-applied; may be empty
   */
  public record KitItem(String material, int amount, Map<String, Integer> enchants) {
    public KitItem {
      enchants = Map.copyOf(enchants);
      if (amount < 1) {
        throw new IllegalArgumentException("amount must be at least 1");
      }
    }
  }
}
