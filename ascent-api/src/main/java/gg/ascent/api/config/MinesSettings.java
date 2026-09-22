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
    Layout layout,
    Map<Integer, MineTier> tiers) {

  public MinesSettings {
    tiers = Map.copyOf(tiers);
    if (!tiers.containsKey(1)) {
      throw new IllegalArgumentException("tier1 is required");
    }
    if (layout.pitInset() * 2 + 2 > plotSize) {
      throw new IllegalArgumentException(
          "pit-inset leaves no room for the pit in a plot of " + plotSize);
    }
  }

  /** The pit is the square {@code [inset, plotSize - inset)} on both axes. */
  public int pitSize() {
    return plotSize - 2 * layout.pitInset();
  }

  /** Blocks in the pit: what the reset fraction is measured against. */
  public int pitVolume() {
    return pitSize() * pitSize() * layout.pitDepth();
  }

  /**
   * How a plot is built when no schematic exists for its tier (PRD E4-S1): a bedrock slab with a
   * square pit of ore in the middle and a spawn point on the rim.
   *
   * @param surfaceY the y of the walkable surface; the pit's top layer is here
   * @param pitDepth layers of ore below and including the surface
   * @param pitInset blocks between the plot edge and the pit on each side
   * @param wallHeight glass wall height above the surface around the plot edge
   */
  public record Layout(int surfaceY, int pitDepth, int pitInset, int wallHeight) {
    public Layout {
      if (pitDepth < 1 || pitInset < 2 || wallHeight < 1 || surfaceY - pitDepth < -60) {
        throw new IllegalArgumentException(
            "layout: pit-depth >= 1, pit-inset >= 2, wall-height >= 1, and the pit must stay above"
                + " y -60");
      }
    }
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
