package gg.ascent.api.progress;

/**
 * Things a player can make progress on. Modules publish these to the {@code ProgressBus}; contracts
 * (and later quests) listen. No module knows about contracts directly (PRD E7-S1).
 */
public enum ObjectiveType {
  MINE_BLOCKS,
  SPAWNER_KILLS,
  APPLY_BOOKS,
  KOTH_PARTICIPATE,
  ENVOY_CRATES,
  PVP_KILLS,
  JOIN_FACTION,
  SELL_VALUE
}
