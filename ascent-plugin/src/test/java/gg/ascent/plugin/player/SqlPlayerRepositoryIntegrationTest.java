package gg.ascent.plugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The players/sessions SQL against CI's MariaDB; skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlPlayerRepositoryIntegrationTest {

  private static Database database;
  private final SqlPlayerRepository repo = new SqlPlayerRepository();

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
  void insertFindUpdateRoundTrip() {
    UUID uuid = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    PlayerSnapshot fresh = PlayerSnapshot.fresh(uuid, "Steve", 1000, now);

    database.executor().runNow(c -> repo.insert(c, fresh));
    PlayerSnapshot loaded = database.executor().supplyNow(c -> repo.find(c, uuid)).orElseThrow();
    assertEquals(fresh, loaded);

    PlayerSnapshot changed =
        new PlayerSnapshot(uuid, "Steve2", 5, 10, 20, 0, 1500, 12.5, now, null, true, now, now, 60);
    database.executor().runNow(c -> repo.update(c, changed));
    assertEquals(changed, database.executor().supplyNow(c -> repo.find(c, uuid)).orElseThrow());
    assertEquals(
        Optional.of(changed), database.executor().supplyNow(c -> repo.findByName(c, "steve2")));
  }

  @Test
  void adjustBalanceRefusesOverdraft() {
    UUID uuid = UUID.randomUUID();
    database
        .executor()
        .runNow(c -> repo.insert(c, PlayerSnapshot.fresh(uuid, "Poor", 10, Instant.now())));

    long after = database.executor().supplyNow(c -> repo.adjustBalance(c, uuid, 15));
    assertEquals(25L, after);
    assertThrows(
        RuntimeException.class,
        () -> database.executor().supplyNow(c -> repo.adjustBalance(c, uuid, -26)));
    assertEquals(
        25, database.executor().supplyNow(c -> repo.find(c, uuid)).orElseThrow().balance());
  }

  @Test
  void sessionsOpenAndClose() {
    UUID uuid = UUID.randomUUID();
    Instant joined = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    long id = database.executor().supplyNow(c -> repo.openSession(c, uuid, joined));
    assertTrue(id > 0);
    database.executor().runNow(c -> repo.closeSession(c, id, joined.plusSeconds(30)));
  }

  @Test
  void topBalancesOrdersHighestFirst() {
    UUID rich = UUID.randomUUID();
    UUID richer = UUID.randomUUID();
    database
        .executor()
        .runNow(
            c -> {
              repo.insert(c, PlayerSnapshot.fresh(rich, "Rich", 5_000_000, Instant.now()));
              repo.insert(c, PlayerSnapshot.fresh(richer, "Richer", 9_000_000, Instant.now()));
            });

    List<PlayerRepository.BalanceEntry> top =
        database.executor().supplyNow(c -> repo.topBalances(c, 2));

    assertEquals(List.of("Richer", "Rich"), top.stream().map(e -> e.name()).toList());
  }
}
