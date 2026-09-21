package gg.ascent.plugin.economy;

import gg.ascent.api.db.DbExecutor;
import gg.ascent.plugin.player.PlayerRepository;
import gg.ascent.plugin.player.PlayerRepository.BalanceEntry;
import gg.ascent.plugin.redis.RedisConnector;
import gg.ascent.plugin.util.Futures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.resps.Tuple;

/**
 * The {@code /baltop} leaderboard: a Redis sorted set rebuilt from the database every autosave (PRD
 * E1-S4), with the last database answer kept in memory for when Redis is away.
 *
 * <p>{@link #refresh} runs right after the autosave so the query sees the balances just written.
 * {@link #read} is for an async thread: it asks Redis first and falls back to the snapshot.
 */
public final class BaltopCache {

  /** Sorted set of {@code uuid|name} scored by balance. */
  public static final String KEY = "ascent:baltop";

  public static final int SIZE = 10;

  private final DbExecutor db;
  private final PlayerRepository players;
  private final RedisConnector redis;
  private final Logger log;
  private volatile List<BalanceEntry> snapshot = List.of();

  public BaltopCache(DbExecutor db, PlayerRepository players, RedisConnector redis, Logger log) {
    this.db = db;
    this.players = players;
    this.redis = redis;
    this.log = log;
  }

  /** Queries the top balances and rewrites the sorted set. Completes on the main thread. */
  public CompletableFuture<Void> refresh() {
    return Futures.logFailure(
        db.supply(
                c -> {
                  List<BalanceEntry> top = players.topBalances(c, SIZE);
                  // Still on the worker thread: the Redis round trip belongs here, not on main.
                  write(top);
                  return top;
                })
            .thenAccept(top -> snapshot = top),
        log,
        "refreshing baltop");
  }

  private void write(List<BalanceEntry> top) {
    Map<String, Double> scores = new LinkedHashMap<>();
    for (BalanceEntry entry : top) {
      scores.put(entry.uuid() + "|" + entry.name(), (double) entry.balance());
    }
    redis.with(
        jedis -> {
          Transaction tx = jedis.multi();
          tx.del(KEY);
          if (!scores.isEmpty()) {
            tx.zadd(KEY, scores);
          }
          tx.exec();
          return true;
        });
  }

  /** The leaderboard, highest first. Blocking: call from an async task, never the main thread. */
  public List<BalanceEntry> read() {
    return redis
        .with(jedis -> jedis.zrevrangeWithScores(KEY, 0, SIZE - 1))
        .map(BaltopCache::decode)
        .filter(list -> !list.isEmpty())
        .orElse(snapshot);
  }

  /** The last database answer, without touching Redis. Safe on any thread. */
  public List<BalanceEntry> snapshot() {
    return snapshot;
  }

  static List<BalanceEntry> decode(List<Tuple> tuples) {
    List<BalanceEntry> out = new ArrayList<>(tuples.size());
    for (Tuple t : tuples) {
      String member = t.getElement();
      int bar = member.indexOf('|');
      if (bar != 36) {
        continue;
      }
      try {
        out.add(
            new BalanceEntry(
                UUID.fromString(member.substring(0, bar)),
                member.substring(bar + 1),
                (long) t.getScore()));
      } catch (IllegalArgumentException ignored) {
        // A foreign entry in our key; skip it rather than fail the whole board.
      }
    }
    return out;
  }
}
