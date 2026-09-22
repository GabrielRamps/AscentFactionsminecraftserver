package gg.ascent.plugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlDeathLogRepositoryIntegrationTest {

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
  void aDeathRowKeepsTheKillerAndTheDroppedIds() {
    SqlDeathLogRepository repo = new SqlDeathLogRepository();
    UUID victim = UUID.randomUUID();
    UUID killer = UUID.randomUUID();
    UUID item = UUID.randomUUID();
    database
        .executor()
        .runNow(
            c -> {
              repo.insert(c, victim, killer, "world", 10, 64, -20, List.of(item), Instant.now());
              repo.insert(c, victim, null, "world", 0, 0, 0, List.of(), Instant.now());
            });

    String[] json = new String[2];
    String[] killers = new String[2];
    database
        .executor()
        .runNow(
            c -> {
              try (PreparedStatement s =
                  c.prepareStatement(
                      "SELECT killer_uuid, dropped_item_ids FROM death_log WHERE victim_uuid = ?"
                          + " ORDER BY id")) {
                s.setString(1, victim.toString());
                try (ResultSet r = s.executeQuery()) {
                  for (int i = 0; r.next(); i++) {
                    killers[i] = r.getString("killer_uuid");
                    json[i] = r.getString("dropped_item_ids");
                  }
                }
              }
            });
    assertEquals(killer.toString(), killers[0]);
    assertEquals("[\"" + item + "\"]", json[0]);
    assertNull(killers[1]);
    assertEquals("[]", json[1]);
  }
}
