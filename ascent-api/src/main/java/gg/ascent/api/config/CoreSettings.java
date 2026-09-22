package gg.ascent.api.config;

import java.time.Duration;
import java.time.ZoneId;
import org.jetbrains.annotations.Nullable;

/**
 * {@code config.yml}.
 *
 * @param debug whether verbose diagnostic logging is on
 * @param timeZone the zone daily resets and displayed times use; the PRD fixes this at UTC
 * @param database the MariaDB connection and pool
 * @param redis the Redis connection, used only for leaderboard caches in Phase 1
 * @param players player data defaults and persistence timing
 * @param items item registry timing
 * @param alerts where staff alerts go besides the console
 */
public record CoreSettings(
    boolean debug,
    ZoneId timeZone,
    Database database,
    Redis redis,
    Players players,
    Items items,
    Alerts alerts) {

  /**
   * Item registry (PRD E1-S5).
   *
   * @param dupeScanInterval how often online inventories are scanned for duplicated ids
   */
  public record Items(Duration dupeScanInterval) {
    public Items {
      if (dupeScanInterval == null || dupeScanInterval.isZero() || dupeScanInterval.isNegative()) {
        throw new IllegalArgumentException("dupe-scan-interval must be positive");
      }
    }
  }

  /**
   * Alert routing (PRD §6.6).
   *
   * @param discordWebhookUrl an outgoing Discord webhook, or null for none; comes from the {@code
   *     DISCORD_WEBHOOK_URL} environment variable, never from a file
   */
  public record Alerts(@Nullable String discordWebhookUrl) {
    public Alerts {
      if (discordWebhookUrl != null && discordWebhookUrl.isBlank()) {
        discordWebhookUrl = null;
      }
    }

    /** Never prints the URL: it is a secret. */
    @Override
    public String toString() {
      return "Alerts[discord=" + (discordWebhookUrl == null ? "off" : "configured") + "]";
    }
  }

  /**
   * Player data (PRD E1-S3).
   *
   * @param startingBalance the balance a brand-new player starts with
   * @param autosaveInterval how often dirty profiles are written
   */
  public record Players(long startingBalance, Duration autosaveInterval) {
    public Players {
      if (startingBalance < 0) {
        throw new IllegalArgumentException("starting-balance cannot be negative");
      }
      if (autosaveInterval == null || autosaveInterval.isZero() || autosaveInterval.isNegative()) {
        throw new IllegalArgumentException("autosave-interval must be positive");
      }
    }
  }

  /**
   * MariaDB connection settings (PRD E1-S2).
   *
   * <p>Host, port and database name come from {@code config.yml} unless the environment overrides
   * them; the user and password come only from the environment ({@code MARIADB_USER}, {@code
   * MARIADB_PASSWORD}), which {@code scripts/start.sh} exports from {@code .env}. A password is
   * never written to a file the plugin ships or to a log line.
   *
   * @param host database host
   * @param port database port
   * @param name database (schema) name
   * @param user login user; empty when the environment did not provide one
   * @param password login password; empty when the environment did not provide one
   * @param maxPoolSize HikariCP maximum pool size; the PRD fixes it at 10
   * @param connectTimeout how long a caller waits for a connection before failing
   */
  public record Database(
      String host,
      int port,
      String name,
      String user,
      String password,
      int maxPoolSize,
      Duration connectTimeout) {

    public Database {
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("database host must not be blank");
      }
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("database port must be 1..65535, got " + port);
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("database name must not be blank");
      }
      if (maxPoolSize < 1) {
        throw new IllegalArgumentException("max-pool-size must be at least 1");
      }
      if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
        throw new IllegalArgumentException("connect-timeout must be positive");
      }
      user = user == null ? "" : user;
      password = password == null ? "" : password;
    }

    /** Whether the environment supplied both a user and a password. */
    public boolean hasCredentials() {
      return !user.isBlank() && !password.isBlank();
    }

    /** The JDBC URL, without credentials. */
    public String jdbcUrl() {
      return "jdbc:mariadb://" + host + ":" + port + "/" + name;
    }

    /** Never prints the password. */
    @Override
    public String toString() {
      return "Database["
          + jdbcUrl()
          + ", user="
          + (user.isBlank() ? "<unset>" : user)
          + ", password="
          + (password.isBlank() ? "<unset>" : "****")
          + ", maxPoolSize="
          + maxPoolSize
          + ", connectTimeout="
          + connectTimeout
          + "]";
    }
  }

  /**
   * Redis connection settings. Redis is optional: when it is disabled or unreachable, the features
   * that cache in it fall back to the database.
   *
   * @param enabled whether the plugin tries to connect at all
   * @param host Redis host
   * @param port Redis port
   * @param password the {@code requirepass} value, or null for none
   */
  public record Redis(boolean enabled, String host, int port, @Nullable String password) {

    public Redis {
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("redis host must not be blank");
      }
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("redis port must be 1..65535, got " + port);
      }
      if (password != null && password.isBlank()) {
        password = null;
      }
    }

    /** Never prints the password. */
    @Override
    public String toString() {
      return "Redis[enabled="
          + enabled
          + ", "
          + host
          + ":"
          + port
          + ", password="
          + (password == null ? "<none>" : "****")
          + "]";
    }
  }
}
