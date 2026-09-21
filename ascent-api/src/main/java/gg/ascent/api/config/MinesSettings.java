package gg.ascent.api.config;

import java.time.Duration;
import java.util.Map;

/**
 * {@code mines.yml}: personal mines (PRD E4-S1).
 *
 * @param worldName the dedicated void world that holds plots
 * @param plotSize plot edge length in blocks
 * @param plotInactivity how long an unused plot is kept before release
 * @param resetInterval time between resets
 * @param resetMinedFraction fraction of blocks mined that triggers an early reset
 * @param tiers tier definitions keyed by tier number, from 1
 */
public record MinesSettings(
    String worldName,
    int plotSize,
    Duration plotInactivity,
    Duration resetInterval,
    double resetMinedFraction,
    Map<Integer, MineTier> tiers) {

  public MinesSettings {
    tiers = Map.copyOf(tiers);
  }

  /**
   * @param schematic FAWE schematic file, relative to the plugin's data folder
   * @param composition block type name to percentage; must sum to 100
   */
  public record MineTier(String schematic, Map<String, Integer> composition) {
    public MineTier {
      composition = Map.copyOf(composition);
      int total = composition.values().stream().mapToInt(Integer::intValue).sum();
      if (total != 100) {
        throw new IllegalArgumentException("block composition sums to " + total + ", expected 100");
      }
    }
  }
}
