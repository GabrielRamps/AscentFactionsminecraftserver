package gg.ascent.plugin.db;

import gg.ascent.api.config.CoreSettings;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.LoggerFactory;

/**
 * A live MariaDB for integration tests, taken from the environment CI provides:
 *
 * <pre>
 * ASCENT_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3306/ascent_test
 * ASCENT_TEST_DB_USER=ascent
 * ASCENT_TEST_DB_PASSWORD=ascent
 * </pre>
 *
 * Tests that call {@link #settings()} are skipped when the URL is unset, so the unit suite runs
 * anywhere. To run them locally, start docker compose and export the three variables against the
 * dev database (it is wiped).
 */
final class TestDatabase {

  private TestDatabase() {}

  static CoreSettings.Database settings() {
    String url = System.getenv("ASCENT_TEST_DB_URL");
    Assumptions.assumeTrue(
        url != null && !url.isBlank(), "ASCENT_TEST_DB_URL not set; skipping database test");
    URI uri = URI.create(url.substring("jdbc:".length()));
    String name = uri.getPath().replaceFirst("^/", "");
    return new CoreSettings.Database(
        uri.getHost(),
        uri.getPort() == -1 ? 3306 : uri.getPort(),
        name,
        envOr("ASCENT_TEST_DB_USER", "ascent"),
        envOr("ASCENT_TEST_DB_PASSWORD", "ascent"),
        4,
        Duration.ofSeconds(10));
  }

  /** Opens a migrated database with continuations running inline. Caller closes it. */
  static Database open() {
    return Database.connect(
        settings(),
        TestDatabase.class.getClassLoader(),
        new InlineMainThread(),
        LoggerFactory.getLogger(TestDatabase.class));
  }

  /** Drops every table in the schema so each test class starts from nothing. */
  static void wipe(DataSource dataSource) throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      List<String> tables = new ArrayList<>();
      try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
        while (rs.next()) {
          tables.add(rs.getString(1));
        }
      }
      s.execute("SET FOREIGN_KEY_CHECKS = 0");
      for (String table : tables) {
        s.execute("DROP TABLE IF EXISTS `" + table + "`");
      }
      s.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
  }

  /** Wipes through a throwaway pool, before the real one runs migrations. */
  static void wipe() throws SQLException {
    CoreSettings.Database settings = settings();
    com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
    config.setJdbcUrl(settings.jdbcUrl());
    config.setUsername(settings.user());
    config.setPassword(settings.password());
    config.setMaximumPoolSize(1);
    try (com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(config)) {
      wipe(ds);
    }
  }

  private static String envOr(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value;
  }

  /** Continuations run where the work finished; fine for tests that block on the future. */
  static final class InlineMainThread implements MainThread {
    @Override
    public void execute(Runnable task) {
      task.run();
    }

    @Override
    public boolean isCurrent() {
      return false;
    }
  }
}
