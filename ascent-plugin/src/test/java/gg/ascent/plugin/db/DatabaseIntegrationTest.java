package gg.ascent.plugin.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.db.DbException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Against the MariaDB CI provides; skipped when {@code ASCENT_TEST_DB_URL} is unset. */
class DatabaseIntegrationTest {

  private static Database database;

  @BeforeAll
  static void connect() throws SQLException {
    TestDatabase.wipe();
    database = TestDatabase.open();
  }

  @AfterAll
  static void disconnect() {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void v1CreatesEveryTableInTheSpec() {
    Set<String> tables =
        database
            .executor()
            .supplyNow(
                c -> {
                  Set<String> out = new HashSet<>();
                  try (Statement s = c.createStatement();
                      ResultSet rs = s.executeQuery("SHOW TABLES")) {
                    while (rs.next()) {
                      out.add(rs.getString(1));
                    }
                  }
                  return out;
                });
    for (String table : MigrationScriptTest.SPEC_TABLES) {
      assertTrue(tables.contains(table), "missing table " + table);
    }
    assertTrue(tables.contains("flyway_schema_history"));
  }

  @Test
  void migratingTwiceIsANoOp() {
    // A second connect against the same schema validates and applies nothing.
    try (Database again = TestDatabase.open()) {
      int applied =
          again
              .executor()
              .supplyNow(
                  c -> {
                    try (Statement s = c.createStatement();
                        ResultSet rs =
                            s.executeQuery(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
                      rs.next();
                      return rs.getInt(1);
                    }
                  });
      assertEquals(1, applied);
    }
  }

  @Test
  void roundTripsUtcTimestampsAndUuids() {
    UUID uuid = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    database
        .executor()
        .runNow(
            c -> {
              try (PreparedStatement ps =
                  c.prepareStatement(
                      "INSERT INTO players (uuid, name, power_updated, first_seen, last_seen,"
                          + " updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                Sql.setUuid(ps, 1, uuid);
                ps.setString(2, "Tester");
                Sql.setInstant(ps, 3, now);
                Sql.setInstant(ps, 4, now);
                Sql.setInstant(ps, 5, now);
                Sql.setInstant(ps, 6, now);
                ps.executeUpdate();
              }
            });

    Instant back =
        database
            .executor()
            .supply(
                c -> {
                  try (PreparedStatement ps =
                      c.prepareStatement("SELECT first_seen, `rank` FROM players WHERE uuid = ?")) {
                    Sql.setUuid(ps, 1, uuid);
                    try (ResultSet rs = ps.executeQuery()) {
                      assertTrue(rs.next());
                      assertEquals(1, rs.getInt("rank"));
                      return Sql.getInstant(rs, "first_seen");
                    }
                  }
                })
            .join();

    assertEquals(now, back);
  }

  @Test
  void transactionRollsBackEveryStatement() {
    UUID uuid = UUID.randomUUID();
    CompletionException e =
        assertThrows(
            CompletionException.class,
            () ->
                database
                    .executor()
                    .transaction(
                        c -> {
                          insertPlayer(c, uuid);
                          throw new SQLException("abort");
                        })
                    .join());
    assertTrue(e.getCause() instanceof DbException);

    int count =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                      c.prepareStatement("SELECT COUNT(*) FROM players WHERE uuid = ?")) {
                    Sql.setUuid(ps, 1, uuid);
                    try (ResultSet rs = ps.executeQuery()) {
                      rs.next();
                      return rs.getInt(1);
                    }
                  }
                });
    assertEquals(0, count);
  }

  @Test
  void pingAnswers() {
    assertTrue(database.pingMillis() >= 0);
    assertTrue(database.executor().stats().poolTotal() >= 1);
  }

  @Test
  void wrongPasswordFailsFastWithAClearMessage() {
    CoreSettings.Database good = TestDatabase.settings();
    CoreSettings.Database bad =
        new CoreSettings.Database(
            good.host(),
            good.port(),
            good.name(),
            good.user(),
            "definitely-wrong",
            1,
            Duration.ofSeconds(3));

    DbException e =
        assertThrows(
            DbException.class,
            () ->
                Database.connect(
                    bad,
                    getClass().getClassLoader(),
                    new TestDatabase.InlineMainThread(),
                    LoggerFactory.getLogger(DatabaseIntegrationTest.class)));
    assertTrue(e.getMessage().contains("cannot connect to " + good.jdbcUrl()), e.getMessage());
  }

  @Test
  void missingCredentialsAreRefusedBeforeConnecting() {
    CoreSettings.Database good = TestDatabase.settings();
    CoreSettings.Database none =
        new CoreSettings.Database(
            good.host(), good.port(), good.name(), "", "", 1, Duration.ofSeconds(3));

    DbException e =
        assertThrows(
            DbException.class,
            () ->
                Database.connect(
                    none,
                    getClass().getClassLoader(),
                    new TestDatabase.InlineMainThread(),
                    LoggerFactory.getLogger(DatabaseIntegrationTest.class)));
    assertTrue(e.getMessage().contains("MARIADB_PASSWORD"), e.getMessage());
  }

  static void insertPlayer(java.sql.Connection c, UUID uuid) throws SQLException {
    Instant now = Instant.now();
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO players (uuid, name, power_updated, first_seen, last_seen, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, uuid);
      ps.setString(2, "Tx");
      Sql.setInstant(ps, 3, now);
      Sql.setInstant(ps, 4, now);
      Sql.setInstant(ps, 5, now);
      Sql.setInstant(ps, 6, now);
      ps.executeUpdate();
    }
  }
}
