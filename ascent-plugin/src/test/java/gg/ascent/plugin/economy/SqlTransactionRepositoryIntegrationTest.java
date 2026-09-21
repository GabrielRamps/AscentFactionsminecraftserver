package gg.ascent.plugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.economy.TxReason;
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
class SqlTransactionRepositoryIntegrationTest {

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
  void everyReasonIsAcceptedByTheEnumColumn() {
    SqlTransactionRepository repo = new SqlTransactionRepository();
    UUID player = UUID.randomUUID();
    database
        .executor()
        .runNow(
            c -> {
              for (TxReason reason : TxReason.values()) {
                repo.record(c, player, null, 5, 105, reason, "ref-" + reason, Instant.now());
              }
              repo.record(c, null, 7, -50, 950, TxReason.UPKEEP, null, Instant.now());
            });

    int rows =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                      c.prepareStatement("SELECT COUNT(*) FROM money_transactions")) {
                    try (ResultSet rs = ps.executeQuery()) {
                      rs.next();
                      return rs.getInt(1);
                    }
                  }
                });
    assertEquals(TxReason.values().length + 1, rows);
  }
}
