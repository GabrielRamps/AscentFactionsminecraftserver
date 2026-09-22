package gg.ascent.plugin.rank;

import gg.ascent.api.rank.XpSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** The {@code xp_log} table: rank XP aggregated per player, source and minute (PRD §6.3). */
public interface XpLogRepository {

  /** Adds {@code amount} to the row for this player, source and minute, creating it if needed. */
  void add(Connection c, UUID player, XpSource source, long amount, Instant minute)
      throws SQLException;
}
