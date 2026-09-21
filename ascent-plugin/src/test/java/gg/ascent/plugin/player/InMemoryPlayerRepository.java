package gg.ascent.plugin.player;

import gg.ascent.api.player.PlayerSnapshot;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** A map standing in for the tables, so the manager's rules can be tested without a database. */
final class InMemoryPlayerRepository implements PlayerRepository {

  final Map<UUID, PlayerSnapshot> rows = new LinkedHashMap<>();
  final Map<Long, Instant[]> sessions = new LinkedHashMap<>();
  final List<String> calls = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong();
  boolean failNext;

  private void maybeFail() throws SQLException {
    if (failNext) {
      failNext = false;
      throw new SQLException("simulated failure");
    }
  }

  @Override
  public synchronized Optional<PlayerSnapshot> find(Connection c, UUID uuid) throws SQLException {
    calls.add("find");
    maybeFail();
    return Optional.ofNullable(rows.get(uuid));
  }

  @Override
  public synchronized Optional<PlayerSnapshot> findByName(Connection c, String name) {
    calls.add("findByName");
    return rows.values().stream()
        .filter(r -> r.name().equalsIgnoreCase(name))
        .max(Comparator.comparing(PlayerSnapshot::lastSeen));
  }

  @Override
  public synchronized void insert(Connection c, PlayerSnapshot row) throws SQLException {
    calls.add("insert");
    maybeFail();
    rows.put(row.uuid(), row);
  }

  @Override
  public synchronized void update(Connection c, PlayerSnapshot row) throws SQLException {
    calls.add("update");
    maybeFail();
    if (!rows.containsKey(row.uuid())) {
      throw new SQLException("no row");
    }
    rows.put(row.uuid(), row);
  }

  @Override
  public synchronized long adjustBalance(Connection c, UUID uuid, long delta) throws SQLException {
    calls.add("adjustBalance");
    maybeFail();
    PlayerSnapshot row = rows.get(uuid);
    if (row == null || row.balance() + delta < 0) {
      throw new SQLException("refused");
    }
    PlayerSnapshot updated =
        new PlayerSnapshot(
            row.uuid(),
            row.name(),
            row.rank(),
            row.xp(),
            row.xpTotal(),
            row.xpBanked(),
            row.balance() + delta,
            row.power(),
            row.powerUpdated(),
            row.factionId(),
            row.starterKitClaimed(),
            row.firstSeen(),
            row.lastSeen(),
            row.playSeconds());
    rows.put(uuid, updated);
    return updated.balance();
  }

  @Override
  public synchronized long openSession(Connection c, UUID uuid, Instant joinedAt) {
    calls.add("openSession");
    long id = ids.incrementAndGet();
    sessions.put(id, new Instant[] {joinedAt, null});
    return id;
  }

  @Override
  public synchronized void closeSession(Connection c, long sessionId, Instant quitAt) {
    calls.add("closeSession");
    sessions.get(sessionId)[1] = quitAt;
  }

  @Override
  public synchronized List<BalanceEntry> topBalances(Connection c, int limit) {
    calls.add("topBalances");
    return rows.values().stream()
        .sorted(Comparator.comparingLong(PlayerSnapshot::balance).reversed())
        .limit(limit)
        .map(r -> new BalanceEntry(r.uuid(), r.name(), r.balance()))
        .toList();
  }
}
