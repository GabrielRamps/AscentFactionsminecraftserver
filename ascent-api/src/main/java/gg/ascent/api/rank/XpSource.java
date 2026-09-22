package gg.ascent.api.rank;

/** Where rank XP came from. Stored in {@code xp_log.source}. */
public enum XpSource {
  MINE_BLOCK,
  SPAWNER_KILL,
  PVP_KILL,
  KOTH_WIN,
  ENVOY_CRATE,
  CONTRACT,
  ADMIN
}
