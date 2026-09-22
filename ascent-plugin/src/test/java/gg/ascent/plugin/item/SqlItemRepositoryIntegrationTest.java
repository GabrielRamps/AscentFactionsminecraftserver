package gg.ascent.plugin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.db.TestDatabaseAccess;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The items, item_events and dupe_alerts SQL; skipped without {@code ASCENT_TEST_DB_URL}. */
class SqlItemRepositoryIntegrationTest {

  private static Database database;
  private final SqlItemRepository repo = new SqlItemRepository();

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

  private static ItemRecord item(UUID id, ItemKind kind, int quantity, Instant now) {
    return new ItemRecord(
        id, kind, "{\"tier\":\"ELITE\"}", quantity, UUID.randomUUID(), "enchanter", now, null);
  }

  @Test
  void itemRoundTripWithEventsAndDestroy() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    UUID id = UuidV7.next();
    ItemRecord row = item(id, ItemKind.BOOK_UNOPENED, 1, now);
    UUID actor = UUID.randomUUID();

    database
        .executor()
        .runNow(
            c -> {
              repo.insertItem(c, row);
              repo.insertEvent(c, id, ItemEvent.CREATED, actor, null, "{\"reason\":\"x\"}", now);
              repo.insertEvent(c, id, ItemEvent.DROPPED, actor, null, null, now.plusSeconds(1));
              repo.markDestroyed(c, id, now.plusSeconds(2));
            });

    ItemRecord back = database.executor().supplyNow(c -> repo.findItem(c, id)).orElseThrow();
    assertEquals(row.kind(), back.kind());
    assertEquals(row.data(), back.data());
    assertEquals(now, back.createdAt());
    assertEquals(now.plusSeconds(2), back.destroyedAt());

    List<ItemEventRecord> events = database.executor().supplyNow(c -> repo.events(c, id));
    assertEquals(
        List.of(ItemEvent.CREATED, ItemEvent.DROPPED),
        events.stream().map(ItemEventRecord::event).toList());
    assertEquals("{\"reason\":\"x\"}", events.get(0).context());
  }

  @Test
  void eventOnUnknownItemIsRefusedByTheForeignKey() {
    assertThrows(
        RuntimeException.class,
        () ->
            database
                .executor()
                .runNow(
                    c ->
                        repo.insertEvent(
                            c,
                            UUID.randomUUID(),
                            ItemEvent.DROPPED,
                            null,
                            null,
                            null,
                            Instant.now())));
  }

  @Test
  void quantitiesOpenAlertsAndInsertAlert() {
    Instant now = Instant.now();
    UUID a = UuidV7.next();
    UUID b = UuidV7.next();
    UUID unknown = UUID.randomUUID();
    database
        .executor()
        .runNow(
            c -> {
              repo.insertItem(c, item(a, ItemKind.MAGIC_DUST, 64, now));
              repo.insertItem(c, item(b, ItemKind.SPAWNER, 4, now));
              repo.insertAlert(c, a, 128, "[{\"name\":\"Steve\"}]", now);
            });

    Map<UUID, Integer> quantities =
        database.executor().supplyNow(c -> repo.quantities(c, List.of(a, b, unknown)));
    assertEquals(Map.of(a, 64, b, 4), quantities);

    Set<UUID> open = database.executor().supplyNow(c -> repo.openAlerts(c, List.of(a, b)));
    assertEquals(Set.of(a), open);
    assertTrue(database.executor().supplyNow(c -> repo.openAlertCount(c)) >= 1);
    assertNotNull(database.executor().supplyNow(c -> repo.quantities(c, List.of())));
  }
}
