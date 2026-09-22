package gg.ascent.plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.rank.XpSource;
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

/** xp_log aggregates per minute and source; skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlXpLogRepositoryIntegrationTest {

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
  void sameMinuteAndSourceAggregateIntoOneRow() {
    SqlXpLogRepository repo = new SqlXpLogRepository();
    UUID player = UUID.randomUUID();
    Instant t0 = Instant.parse("2026-09-22T12:00:05Z");
    database
        .executor()
        .runNow(
            c -> {
              repo.add(c, player, XpSource.MINE_BLOCK, 2, t0);
              repo.add(c, player, XpSource.MINE_BLOCK, 5, t0.plusSeconds(40));
              repo.add(c, player, XpSource.PVP_KILL, 500, t0.plusSeconds(10));
              repo.add(c, player, XpSource.MINE_BLOCK, 12, t0.plusSeconds(70));
            });

    int rows =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                          c.prepareStatement("SELECT COUNT(*) FROM xp_log WHERE player_uuid = ?");
                      ResultSet rs = query(ps, player)) {
                    rs.next();
                    return rs.getInt(1);
                  }
                });
    int firstMinuteMining =
        database
            .executor()
            .supplyNow(
                c -> {
                  try (PreparedStatement ps =
                          c.prepareStatement(
                              "SELECT amount FROM xp_log WHERE player_uuid = ? AND source ="
                                  + " 'MINE_BLOCK' ORDER BY minute LIMIT 1");
                      ResultSet rs = query(ps, player)) {
                    rs.next();
                    return rs.getInt(1);
                  }
                });
    assertEquals(3, rows);
    assertEquals(7, firstMinuteMining);
  }

  private static ResultSet query(PreparedStatement ps, UUID player) throws SQLException {
    ps.setString(1, player.toString());
    return ps.executeQuery();
  }
}
