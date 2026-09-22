package gg.ascent.plugin.leaderboard;

import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.db.SqlFunction;
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
 * A top-N board kept in a Redis sorted set and rebuilt from the database after every autosave, with
 * the last database answer kept in memory for when Redis is away. Used for {@code /baltop} (PRD
 * E1-S4) and {@code /rank top} (E2-S1).
 *
 * <p>{@link #refresh} runs on the main thread and completes there; {@link #read} is for an async
 * thread, since Redis is a network hop.
 */
public final class Leaderboard {

  public static final int SIZE = 10;

  /** One row: who, their last known name, and the sort score. */
  public record Entry(UUID uuid, String name, long score) {}

  private final String key;
  private final DbExecutor db;
  private final SqlFunction<List<Entry>> query;
  private final RedisConnector redis;
  private final Logger log;
  private volatile List<Entry> snapshot = List.of();

  /**
   * @param key the Redis key, like {@code ascent:baltop}
   * @param query the database query for the top {@link #SIZE}, highest score first
   */
  public Leaderboard(
      String key, DbExecutor db, SqlFunction<List<Entry>> query, RedisConnector redis, Logger log) {
    this.key = key;
    this.db = db;
    this.query = query;
    this.redis = redis;
    this.log = log;
  }

  /** Queries the database and rewrites the sorted set. Completes on the main thread. */
  public CompletableFuture<Void> refresh() {
    return Futures.logFailure(
        db.supply(
                c -> {
                  List<Entry> top = query.apply(c);
                  // Still on the worker thread: the Redis round trip belongs here, not on main.
                  write(top);
                  return top;
                })
            .thenAccept(top -> snapshot = top),
        log,
        "refreshing " + key);
  }

  private void write(List<Entry> top) {
    Map<String, Double> scores = new LinkedHashMap<>();
    for (Entry entry : top) {
      scores.put(entry.uuid() + "|" + entry.name(), (double) entry.score());
    }
    redis.with(
        jedis -> {
          Transaction tx = jedis.multi();
          tx.del(key);
          if (!scores.isEmpty()) {
            tx.zadd(key, scores);
          }
          tx.exec();
          return true;
        });
  }

  /** The board, highest first. Blocking: call from an async task, never the main thread. */
  public List<Entry> read() {
    return redis
        .with(jedis -> jedis.zrevrangeWithScores(key, 0, SIZE - 1))
        .map(Leaderboard::decode)
        .filter(list -> !list.isEmpty())
        .orElse(snapshot);
  }

  /** The last database answer, without touching Redis. Safe on any thread. */
  public List<Entry> snapshot() {
    return snapshot;
  }

  static List<Entry> decode(List<Tuple> tuples) {
    List<Entry> out = new ArrayList<>(tuples.size());
    for (Tuple t : tuples) {
      String member = t.getElement();
      int bar = member.indexOf('|');
      if (bar != 36) {
        continue;
      }
      try {
        out.add(
            new Entry(
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
