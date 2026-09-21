package gg.ascent.api.config;

import java.util.Map;

/** {@code ranks.yml}: the personal rank ladder (PRD E2-S1, §6.4.4). */
public record RanksSettings(XpCurve xpCurve, XpSources sources) {

  /**
   * The rank-up curve: {@code xpToNext(rank) = round(base * rank^exponent)}.
   *
   * @param base XP required to leave rank 1
   * @param exponent curve steepness; the PRD's soft knee comes from 1.4
   * @param maxRank the top of the ladder
   */
  public record XpCurve(long base, double exponent, int maxRank) {

    /** XP needed to advance from {@code rank} to {@code rank + 1}. */
    public long xpToNext(int rank) {
      if (rank < 1) {
        throw new IllegalArgumentException("rank must be >= 1, got " + rank);
      }
      return Math.round(base * Math.pow(rank, exponent));
    }
  }

  /**
   * XP granted per activity.
   *
   * @param mineBlockByTier XP per block mined, keyed by mine tier
   * @param spawnerKillByMob XP per melee spawner kill, keyed by mob type name
   * @param pvpKill PvP kill XP and its diminishing-returns rule
   * @param kothWin XP for winning a KOTH
   * @param envoyCrate XP for claiming an envoy crate
   */
  public record XpSources(
      Map<Integer, Long> mineBlockByTier,
      Map<String, Long> spawnerKillByMob,
      PvpKill pvpKill,
      long kothWin,
      long envoyCrate) {

    public XpSources {
      mineBlockByTier = Map.copyOf(mineBlockByTier);
      spawnerKillByMob = Map.copyOf(spawnerKillByMob);
    }
  }

  /**
   * @param base XP for a kill
   * @param repeatVictimWindowMinutes window in which killing the same victim again is "repeat"
   * @param repeatMultiplier fraction of base XP a repeat kill grants
   */
  public record PvpKill(long base, int repeatVictimWindowMinutes, double repeatMultiplier) {}
}
