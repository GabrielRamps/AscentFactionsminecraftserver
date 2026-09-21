package gg.ascent.plugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.player.PlayerRepository.BalanceEntry;
import gg.ascent.plugin.redis.RedisConnector;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.resps.Tuple;

/** The leaderboard with Redis disabled: the database snapshot must carry it. */
class BaltopCacheTest {

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
            LoggerFactory.getLogger(BaltopCacheTest.class));
    BaltopCache cache =
        new BaltopCache(db, repo, redis, LoggerFactory.getLogger(BaltopCacheTest.class));

    assertTrue(cache.read().isEmpty());
    cache.refresh();
    db.tick();

    List<BalanceEntry> top = cache.read();
    assertEquals(2, top.size());
    assertEquals("Rich", top.get(0).name());
    assertEquals(999, top.get(0).balance());
    assertEquals(top, cache.snapshot());
  }

  @Test
  void decodeSkipsForeignMembers() {
    UUID a = UUID.randomUUID();
    List<BalanceEntry> out =
        BaltopCache.decode(
            List.of(
                new Tuple(a + "|Steve", 1500.0),
                new Tuple("garbage", 3.0),
                new Tuple("not-a-uuid-but-36-chars-long-xxxxxxxx|x", 1.0)));

    assertEquals(List.of(new BalanceEntry(a, "Steve", 1500)), out);
  }
}
