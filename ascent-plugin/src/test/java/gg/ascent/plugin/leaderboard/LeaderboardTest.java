package gg.ascent.plugin.leaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.leaderboard.Leaderboard.Entry;
import gg.ascent.plugin.redis.RedisConnector;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.resps.Tuple;

/** The board with Redis disabled: the database snapshot must carry it. */
class LeaderboardTest {

  @Test
  void refreshFillsTheSnapshotAndReadFallsBackToItWithoutRedis() {
    InMemoryPlayerRepository repo = new InMemoryPlayerRepository();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    repo.rows.put(a, PlayerSnapshot.fresh(a, "Poor", 10, Instant.EPOCH));
    repo.rows.put(b, PlayerSnapshot.fresh(b, "Rich", 999, Instant.EPOCH));
    FakeDbExecutor db = new FakeDbExecutor();
    RedisConnector redis =
        RedisConnector.connect(
            new CoreSettings.Redis(false, "localhost", 6379, null),
            LoggerFactory.getLogger(LeaderboardTest.class));
    Leaderboard board =
        new Leaderboard(
            "ascent:test",
            db,
            c ->
                repo.topBalances(c, Leaderboard.SIZE).stream()
                    .map(e -> new Entry(e.uuid(), e.name(), e.balance()))
                    .toList(),
            redis,
            LoggerFactory.getLogger(LeaderboardTest.class));

    assertTrue(board.read().isEmpty());
    board.refresh();
    db.tick();

    List<Entry> top = board.read();
    assertEquals(2, top.size());
    assertEquals("Rich", top.get(0).name());
    assertEquals(999, top.get(0).score());
    assertEquals(top, board.snapshot());
  }

  @Test
  void decodeSkipsForeignMembers() {
    UUID a = UUID.randomUUID();
    List<Entry> out =
        Leaderboard.decode(
            List.of(
                new Tuple(a + "|Steve", 1500.0),
                new Tuple("garbage", 3.0),
                new Tuple("not-a-uuid-but-36-chars-long-xxxxxxxx|x", 1.0)));

    assertEquals(List.of(new Entry(a, "Steve", 1500)), out);
  }
}
