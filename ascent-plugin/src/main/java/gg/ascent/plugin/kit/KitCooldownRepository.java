package gg.ascent.plugin.kit;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The {@code kit_cooldowns} table: when each kit is next available to a player. */
public interface KitCooldownRepository {

  /** Kit id to the instant it becomes available again. Expired rows may be included. */
  Map<String, Instant> load(Connection c, UUID player) throws SQLException;

  /** Sets (or replaces) when {@code kitId} is next available. */
  void set(Connection c, UUID player, String kitId, Instant nextAt) throws SQLException;
}
