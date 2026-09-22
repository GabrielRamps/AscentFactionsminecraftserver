package gg.ascent.plugin.mine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import gg.ascent.plugin.mine.MinePlotRepository.Row;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlMinePlotRepositoryIntegrationTest {

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
  void saveUpsertsAndAllReadsBack() {
    SqlMinePlotRepository repo = new SqlMinePlotRepository();
    UUID steve = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    database
        .executor()
        .runNow(
            c -> {
              repo.save(c, new Row(0, steve, 1, now, now, 5));
              repo.save(c, new Row(1, null, 1, null, null, 0));
              repo.save(c, new Row(0, steve, 2, now, now, 0));
            });

    List<Row> all = database.executor().supplyNow(repo::all);
    assertEquals(2, all.size());
    assertEquals(new Row(0, steve, 2, now, now, 0), all.get(0));
    assertNull(all.get(1).owner());
  }
}
