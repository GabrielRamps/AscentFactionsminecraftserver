package gg.ascent.api.rank;

import java.util.UUID;

/**
 * The personal rank ladder (PRD E2-S1): XP from every activity, rank 1 to the configured maximum,
 * excess banked past the top for prestige. Rank XP is separate from vanilla XP levels.
 *
 * <p>Main thread only; online players only. Offline players are read through {@code
 * PlayerService.lookup}.
 */
public interface RankService {

  /**
   * @throws IllegalStateException if the player is not online
   */
  int getRank(UUID player);

  /**
   * Progress toward the next rank.
   *
   * @throws IllegalStateException if the player is not online
   */
  long getXp(UUID player);

  /** XP needed to go from {@code rank} to {@code rank + 1}: {@code round(base * rank^exponent)}. */
  long xpToNext(int rank);

  /** The top of the ladder. */
  int maxRank();

  /**
   * Grants XP. Rank-ups happen at once, excess carries over, and each fires {@code RankUpEvent}; at
   * the top rank the XP is banked instead. Nothing happens for an offline player or a non-positive
   * amount.
   */
  void addXp(UUID player, XpSource source, long amount);

  /**
   * A snapshot for display.
   *
   * @throws IllegalStateException if the player is not online
   */
  Progress progress(UUID player);

  /**
   * @param rank current rank
   * @param xp progress toward the next rank; 0 at the top
   * @param xpToNext XP the next rank needs; 0 at the top
   * @param banked XP earned past the top
   */
  record Progress(int rank, long xp, long xpToNext, long banked) {
    public boolean atTop() {
      return xpToNext == 0;
    }

    /** 0.0 to 1.0. */
    public double fraction() {
      return xpToNext == 0 ? 1.0 : Math.min(1.0, (double) xp / xpToNext);
    }
  }
}
