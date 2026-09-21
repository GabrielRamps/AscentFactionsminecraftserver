package gg.ascent.plugin.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlAdminActionRepositoryIntegrationTest {

  private static Database database;

  @BeforeAll
  static void connect() throws SQLException {
    database = TestDatabaseAccess.freshDatabase();
  }

  @AfterAll
  static void disconnect() {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void writesJsonArgsAndTheConsoleActor() {
    SqlAdminActionRepository repo = new SqlAdminActionRepository();
    database
        .executor()
        .runNow(
            c -> {
              repo.record(
                  c,
                  AdminActionLog.CONSOLE,
                  "give.money",
                  "Steve",
                  "{\"amount\":5}",
                  Instant.now());
              repo.record(c, UUID.randomUUID(), "reload", null, null, Instant.now());
            });

    String args =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                          c.prepareStatement(
                              "SELECT JSON_EXTRACT(args, '$.amount') FROM admin_actions"
                                  + " WHERE actor_uuid = ?");
                      ResultSet rs = bind(ps)) {
                    rs.next();
                    return rs.getString(1);
                  }
                });
    assertEquals("5", args);
  }

  private static ResultSet bind(PreparedStatement ps) throws SQLException {
    ps.setString(1, AdminActionLog.CONSOLE.toString());
    return ps.executeQuery();
  }
}
