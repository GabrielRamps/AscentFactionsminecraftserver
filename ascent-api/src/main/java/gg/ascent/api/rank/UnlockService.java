package gg.ascent.api.rank;

import gg.ascent.api.enchant.Tier;
import java.util.List;

/**
 * Every rank gate in one place (PRD E2-S2), so modules never hard-code thresholds.
 *
 * <p>All numbers come from {@code ranks.yml}, {@code enchants.yml} (book tiers) and {@code
 * kits.yml} (kits).
 */
public interface UnlockService {

  /** Custom-enchant slots on one item: {@code base + rank / perRanks}, capped. */
  int enchantSlotCap(int rank);

  /** The highest book tier a player of {@code rank} may buy. */
  Tier maxBookTier(int rank);

  /** The highest personal-mine tier open at {@code rank}, from 1. */
  int mineTier(int rank);

  /** Kit ids open at {@code rank}, in {@code kits.yml} order. */
  List<String> availableKits(int rank);

  /** Every threshold on the ladder, lowest rank first, for {@code /rank unlocks}. */
  List<Unlock> unlocks();

  /** The next threshold above {@code rank}, or empty at the top. */
  java.util.Optional<Unlock> nextUnlock(int rank);

  /** What kind of thing an unlock opens. */
  enum Kind {
    ENCHANT_SLOT,
    BOOK_TIER,
    MINE_TIER,
    KIT
  }

  /**
   * @param rank the rank at which it opens
   * @param kind what it is
   * @param name the slot count, tier name, mine tier number or kit id
   */
  record Unlock(int rank, Kind kind, String name) {}
}
