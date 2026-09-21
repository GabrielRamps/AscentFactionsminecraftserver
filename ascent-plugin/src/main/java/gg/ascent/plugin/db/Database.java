package gg.ascent.plugin.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.db.DbException;
import gg.ascent.api.db.DbExecutor;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;

/**
 * The connection pool, the schema migrations and the executor, in the order they must come up.
 *
 * <p>{@link #connect} either returns a database that is reachable and migrated or throws a {@link
 * DbException} whose message tells the operator what to fix. Nothing in the plugin runs with a
 * half-working database (PRD E1-S2).
 */
public final class Database implements AutoCloseable {

  /** Where the versioned SQL lives inside the jar. */
  public static final String MIGRATIONS = "classpath:db/migration";

  private final HikariDataSource dataSource;
  private final HikariDbExecutor executor;
  private final Logger log;

  private Database(HikariDataSource dataSource, HikariDbExecutor executor, Logger log) {
    this.dataSource = dataSource;
    this.executor = executor;
    this.log = log;
  }

  /**
   * Opens the pool, checks a connection, applies pending migrations and starts the executor.
   *
   * @param settings connection settings; credentials must be present
   * @param migrations the class loader that can see {@link #MIGRATIONS}, normally the plugin's
   * @param mainThread how asynchronous results reach the main thread
   * @param log where progress and problems are reported
   * @throws DbException when the database cannot be reached or migrated
   */
  public static Database connect(
      CoreSettings.Database settings, ClassLoader migrations, MainThread mainThread, Logger log) {
    if (!settings.hasCredentials()) {
      throw new DbException(
          "MARIADB_USER and MARIADB_PASSWORD are not set. scripts/start.sh exports them from the"
              + " repo's .env; if you start the server another way, export them yourself.");
    }
    HikariDataSource dataSource = openPool(settings);
    try {
      migrate(dataSource, migrations, log);
    } catch (RuntimeException e) {
      dataSource.close();
      throw e;
    }
    HikariDbExecutor executor =
        new HikariDbExecutor(dataSource, settings.maxPoolSize(), mainThread, log);
    log.info(
        "Connected to {} as {} (pool of {}).",
        settings.jdbcUrl(),
        settings.user(),
        settings.maxPoolSize());
    return new Database(dataSource, executor, log);
  }

  private static HikariDataSource openPool(CoreSettings.Database settings) {
    HikariConfig config = new HikariConfig();
    config.setPoolName("Ascent-Hikari");
    config.setJdbcUrl(settings.jdbcUrl());
    config.setUsername(settings.user());
    config.setPassword(settings.password());
    config.setMaximumPoolSize(settings.maxPoolSize());
    config.setMinimumIdle(Math.min(2, settings.maxPoolSize()));
    config.setConnectionTimeout(settings.connectTimeout().toMillis());
    // Fail fast: a pool that cannot get one connection at startup throws instead of retrying
    // in the background while the server pretends to be fine.
    config.setInitializationFailTimeout(1);
    config.setAutoCommit(true);
    // Timestamps are stored and read as UTC wall-clock values (see Sql); pin the session so a
    // server whose system zone drifts never shifts a row.
    config.addDataSourceProperty("timezone", "UTC");
    config.addDataSourceProperty("useServerPrepStmts", "true");
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    try {
      return new HikariDataSource(config);
    } catch (RuntimeException e) {
      throw new DbException(
          "cannot connect to "
              + settings.jdbcUrl()
              + " as "
              + settings.user()
              + ": "
              + rootMessage(e)
              + ". Is MariaDB running (docker compose ps) and do the credentials in .env match?",
          e);
    }
  }

  private static void migrate(HikariDataSource dataSource, ClassLoader loader, Logger log) {
    try {
      Flyway flyway =
          Flyway.configure(loader)
              .dataSource(dataSource)
              .locations(MIGRATIONS)
              .table("flyway_schema_history")
              .validateOnMigrate(true)
              .baselineOnMigrate(false)
              .load();
      MigrateResult result = flyway.migrate();
      if (result.migrationsExecuted > 0) {
        log.info(
            "Applied {} database migration(s); schema is now at version {}.",
            result.migrationsExecuted,
            result.targetSchemaVersion);
      } else {
        log.info("Database schema is up to date (version {}).", result.targetSchemaVersion);
      }
    } catch (FlywayException e) {
      throw new DbException("database migration failed: " + rootMessage(e), e);
    }
  }

  /** The executor every repository uses. */
  public DbExecutor executor() {
    return executor;
  }

  /** The pool itself, for migrations and tests; production code uses {@link #executor()}. */
  public HikariDataSource dataSource() {
    return dataSource;
  }

  /** A round trip to the server, in milliseconds, on the calling thread. */
  public long pingMillis() {
    long start = System.nanoTime();
    try (Connection c = dataSource.getConnection()) {
      if (!c.isValid(2)) {
        throw new DbException("connection is not valid");
      }
    } catch (SQLException e) {
      throw new DbException("ping failed: " + e.getMessage(), e);
    }
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** Signals that shutdown has begun, so the shutdown flush may block on the main thread. */
  public void beginShutdown() {
    executor.beginShutdown();
  }

  /** Drains queued work for up to {@code grace}, then closes the pool. */
  public void close(Duration grace) {
    executor.close(grace);
    dataSource.close();
    log.info("Database connection closed.");
  }

  @Override
  public void close() {
    close(Duration.ofSeconds(10));
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
