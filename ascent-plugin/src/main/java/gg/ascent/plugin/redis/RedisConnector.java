package gg.ascent.plugin.redis;

import gg.ascent.api.config.CoreSettings;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

/**
 * The Redis client (Jedis), initialised at startup but never required (PRD E1-S2).
 *
 * <p>Redis holds only caches that can be rebuilt from MariaDB, so a Redis that is down, disabled or
 * misconfigured is a warning, not a reason to refuse to start. Callers go through {@link #with},
 * which answers empty when Redis is unavailable, and fall back to the database.
 */
public final class RedisConnector implements AutoCloseable {

  private static final int TIMEOUT_MILLIS = 2000;

  private final Logger log;
  private final @Nullable JedisPool pool;
  private volatile boolean available;

  private RedisConnector(Logger log, @Nullable JedisPool pool, boolean available) {
    this.log = log;
    this.pool = pool;
    this.available = available;
  }

  /** Connects and pings. Never throws; the result says whether Redis answered. */
  public static RedisConnector connect(CoreSettings.Redis settings, Logger log) {
    if (!settings.enabled()) {
      log.info("Redis is disabled in config.yml; leaderboards will read the database.");
      return new RedisConnector(log, null, false);
    }
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(8);
    poolConfig.setMaxIdle(4);
    poolConfig.setMinIdle(0);
    poolConfig.setTestOnBorrow(true);
    DefaultJedisClientConfig.Builder client =
        DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(TIMEOUT_MILLIS)
            .socketTimeoutMillis(TIMEOUT_MILLIS)
            .clientName("ascent");
    if (settings.password() != null) {
      client.password(settings.password());
    }
    JedisPool pool =
        new JedisPool(
            poolConfig, new HostAndPort(settings.host(), settings.port()), client.build());
    try (Jedis jedis = pool.getResource()) {
      String pong = jedis.ping();
      if (!"PONG".equalsIgnoreCase(pong)) {
        throw new JedisException("unexpected ping reply: " + pong);
      }
    } catch (JedisException e) {
      log.warn(
          "Redis at {}:{} is unavailable ({}); leaderboards will read the database until it"
              + " answers. Check docker compose ps and REDIS_PASSWORD in .env.",
          settings.host(),
          settings.port(),
          rootMessage(e));
      return new RedisConnector(log, pool, false);
    }
    log.info("Connected to Redis at {}:{}.", settings.host(), settings.port());
    return new RedisConnector(log, pool, true);
  }

  /** Whether the last operation against Redis succeeded. */
  public boolean isAvailable() {
    return available;
  }

  /**
   * Runs {@code work} with a pooled connection. Answers empty when Redis is disabled or the call
   * fails; the failure is logged once per outage. Must not be called on the main thread: Redis is a
   * network round trip.
   */
  public <T> Optional<T> with(Function<Jedis, T> work) {
    if (pool == null) {
      return Optional.empty();
    }
    try (Jedis jedis = pool.getResource()) {
      T value = work.apply(jedis);
      if (!available) {
        available = true;
        log.info("Redis is answering again.");
      }
      return Optional.ofNullable(value);
    } catch (JedisException e) {
      if (available) {
        available = false;
        log.warn("Redis call failed ({}); falling back to the database.", rootMessage(e));
      }
      return Optional.empty();
    }
  }

  @Override
  public void close() {
    if (pool != null) {
      pool.close();
    }
  }

  private static String rootMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage();
    return message == null ? root.getClass().getSimpleName() : message;
  }
}
