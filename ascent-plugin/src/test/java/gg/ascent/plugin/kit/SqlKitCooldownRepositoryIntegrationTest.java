package gg.ascent.plugin.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlKitCooldownRepositoryIntegrationTest {

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
  void setReplacesAndLoadReturnsAllKits() {
    SqlKitCooldownRepository repo = new SqlKitCooldownRepository();
    UUID player = UUID.randomUUID();
    Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant t2 = t1.plusSeconds(3600);
    database
        .executor()
        .runNow(
            c -> {
              repo.set(c, player, "starter", t1);
              repo.set(c, player, "rank10", t1);
              repo.set(c, player, "starter", t2);
            });

    Map<String, Instant> loaded = database.executor().supplyNow(c -> repo.load(c, player));
    assertEquals(Map.of("starter", t2, "rank10", t1), loaded);
    assertEquals(Map.of(), database.executor().supplyNow(c -> repo.load(c, UUID.randomUUID())));
  }
}
