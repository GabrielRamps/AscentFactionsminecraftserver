package gg.ascent.plugin.player;

import gg.ascent.api.player.PlayerSnapshot;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code players} and {@code sessions} tables. Every method runs on the caller's connection and
 * thread; the player service decides which thread that is.
 */
public interface PlayerRepository {

  Optional<PlayerSnapshot> find(Connection c, UUID uuid) throws SQLException;

  /** The most recently seen player with this name, ignoring case. */
  Optional<PlayerSnapshot> findByName(Connection c, String name) throws SQLException;

  /** Inserts a row. The caller has checked none exists. */
  void insert(Connection c, PlayerSnapshot row) throws SQLException;

  /** Updates every mutable column of an existing row. */
  void update(Connection c, PlayerSnapshot row) throws SQLException;

  /** Adds {@code delta} to the stored balance and returns the new balance. */
  long adjustBalance(Connection c, UUID uuid, long delta) throws SQLException;

  /** Opens a {@code sessions} row and returns its id. */
  long openSession(Connection c, UUID uuid, Instant joinedAt) throws SQLException;

  void closeSession(Connection c, long sessionId, Instant quitAt) throws SQLException;

  /** Highest balances first. */
  List<BalanceEntry> topBalances(Connection c, int limit) throws SQLException;

  /** One leaderboard row. */
  record BalanceEntry(UUID uuid, String name, long balance) {}
}
