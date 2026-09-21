package gg.ascent.api.config;

import gg.ascent.api.contract.Archetype;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.progress.ObjectiveType;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * {@code contracts.yml}: the daily contract pool (PRD E7-S1).
 *
 * @param perDay contracts assigned to each player at daily reset
 * @param pool every contract that can be assigned
 */
public record ContractsSettings(int perDay, List<Contract> pool) {

  public ContractsSettings {
    pool = List.copyOf(pool);
    long distinct = pool.stream().map(Contract::id).distinct().count();
    if (distinct != pool.size()) {
      throw new IllegalArgumentException("contract ids must be unique");
    }
  }

  /**
   * @param id stable identifier; "never the same id two days running" keys on this
   * @param archetype which lane the contract serves
   * @param objective what is counted
   * @param target amount required
   * @param minRank lowest personal rank that can receive it
   * @param rewards paid on completion
   */
  public record Contract(
      String id,
      Archetype archetype,
      ObjectiveType objective,
      long target,
      int minRank,
      Rewards rewards) {}

  /**
   * @param rankXp personal rank XP
   * @param money whole dollars
   * @param bookTier an unopened book of this tier, or null for none
   */
  public record Rewards(long rankXp, long money, @Nullable Tier bookTier) {}
}
