package gg.ascent.plugin.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.enchant.ApplyResult;
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
class SqlEnchantRollRepositoryIntegrationTest {

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
  void everyOutcomeIsAcceptedByTheEnumColumn() {
    SqlEnchantRollRepository repo = new SqlEnchantRollRepository();
    UUID player = UUID.randomUUID();
    database
        .executor()
        .runNow(
            c -> {
              for (ApplyResult outcome : ApplyResult.values()) {
                if (outcome == ApplyResult.INCOMPATIBLE) {
                  continue; // never rolled, never recorded
                }
                repo.record(
                    c,
                    player,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "lifesteal",
                    3,
                    55,
                    40,
                    outcome,
                    Instant.now());
              }
            });

    int rows =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                          c.prepareStatement(
                              "SELECT COUNT(*) FROM enchant_rolls WHERE player_uuid = ?");
                      ResultSet rs = bind(ps, player)) {
                    rs.next();
                    return rs.getInt(1);
                  }
                });
    assertEquals(4, rows);
  }

  private static ResultSet bind(PreparedStatement ps, UUID player) throws SQLException {
    ps.setString(1, player.toString());
    return ps.executeQuery();
  }
}
