package gg.ascent.api.mine;

import java.util.Optional;
import java.util.UUID;

/**
 * Personal mines (PRD E4-S1): one instanced, rank-tiered plot per player in a dedicated world,
 * refilled on a timer or when mostly mined. Main thread.
 */
public interface MineService {

  /** The plot a player currently holds, if any. */
  Optional<PlotInfo> plotOf(UUID player);

  /**
   * Allocates (or reuses) the player's plot at the tier their rank allows and teleports them to its
   * spawn point. The plot is built if new or upgraded.
   */
  void visit(UUID player);

  /** Refills the pit of plot {@code index} now, moving anyone inside to the spawn point. */
  void reset(int index);

  /**
   * @param index the plot's position in the grid
   * @param owner who holds it
   * @param tier the tier it is built at
   * @param minedBlocks blocks mined since the last reset
   * @param pitVolume blocks in a full pit
   */
  record PlotInfo(int index, UUID owner, int tier, int minedBlocks, int pitVolume) {
    public double minedFraction() {
      return pitVolume == 0 ? 0 : (double) minedBlocks / pitVolume;
    }
  }
}
