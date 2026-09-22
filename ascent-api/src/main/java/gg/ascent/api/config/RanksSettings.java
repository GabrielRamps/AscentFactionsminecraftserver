package gg.ascent.api.config;

import java.util.Map;

/** {@code ranks.yml}: the personal rank ladder (PRD E2-S1, §6.4.4). */
public record RanksSettings(XpCurve xpCurve, XpSources sources, Unlocks unlocks, RankUp rankUp) {

  /**
   * Rank gates (PRD E2-S2). Book tiers come from {@code enchants.yml} and kits from {@code
   * kits.yml}.
   *
   * @param enchantSlots slot cap curve: {@code base + rank / perRanks}, at most {@code max}
   * @param mineTiers mine tier number to the rank that opens it
   */
  public record Unlocks(EnchantSlots enchantSlots, Map<Integer, Integer> mineTiers) {
    public Unlocks {
      mineTiers = Map.copyOf(mineTiers);
      if (!mineTiers.containsKey(1) || mineTiers.get(1) != 1) {
        throw new IllegalArgumentException("mine tier 1 must open at rank 1");
      }
    }
  }

  /**
   * @param base slots at rank 1 (before the first step)
   * @param perRanks one more slot every this many ranks
   * @param max the cap
   */
  public record EnchantSlots(int base, int perRanks, int max) {
    public EnchantSlots {
      if (base < 1 || perRanks < 1 || max < base) {
        throw new IllegalArgumentException(
            "enchant-slots must have base >= 1, per-ranks >= 1, max >= base");
      }
    }
  }

  /**
   * @param sound the Minecraft sound key played on rank-up, like {@code entity.player.levelup}
   */
  public record RankUp(String sound) {}

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
