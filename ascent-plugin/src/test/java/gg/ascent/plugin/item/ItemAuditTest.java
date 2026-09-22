package gg.ascent.plugin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryItemRepository;
import gg.ascent.plugin.testing.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** PRD E1-S5: a row and a CREATED event per tag; every event logged; DESTROYED closes the item. */
class ItemAuditTest {

  private InMemoryItemRepository repo;
  private FakeDbExecutor db;
  private MutableClock clock;
  private ItemAudit audit;

  @BeforeEach
  void setUp() {
    repo = new InMemoryItemRepository();
    db = new FakeDbExecutor();
    clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
    audit = new ItemAudit(db, repo, clock, LoggerFactory.getLogger(ItemAuditTest.class));
  }

  @Test
  void createdWritesTheRowAndACreatedEventTogether() {
    UUID steve = UUID.randomUUID();
    UUID id = UuidV7.next();

    ItemRecord record =
        audit.created(id, ItemKind.MAGIC_DUST, Map.of("percent", 5), 64, steve, "alchemist");
    db.tick();

    assertEquals(1, db.transactions);
    assertEquals(record, repo.items.get(id));
    assertEquals("{\"percent\":5}", record.data());
    assertEquals(64, record.quantityCreated());
    assertEquals(1, repo.events.size());
    ItemEventRecord created = repo.events.get(0);
    assertEquals(ItemEvent.CREATED, created.event());
    assertEquals(steve, created.actor());
    assertTrue(created.context().contains("alchemist"), created.context());
    assertEquals(1, audit.taggedCount());
  }

  @Test
  void recordAppendsEventsAndDestroyedClosesTheItem() {
    UUID steve = UUID.randomUUID();
    UUID alex = UUID.randomUUID();
    UUID id = UuidV7.next();
    audit.created(id, ItemKind.GEAR, Map.of(), 1, steve, "kit:pvp");
    db.tick();

    audit.record(id, ItemEvent.DROPPED, steve, null, Map.of("world", "world"));
    audit.record(id, ItemEvent.PICKED_UP, alex, steve, null);
    clock.advance(java.time.Duration.ofMinutes(1));
    audit.record(id, ItemEvent.DESTROYED, null, null, Map.of("cause", "LAVA"));
    db.tick();

    CompletableFuture<List<ItemEventRecord>> future = audit.history(id);
    db.tick();
    List<ItemEventRecord> history = future.join();
    assertEquals(
        List.of(ItemEvent.CREATED, ItemEvent.DROPPED, ItemEvent.PICKED_UP, ItemEvent.DESTROYED),
        history.stream().map(ItemEventRecord::event).toList());
    assertEquals(steve, history.get(2).counterparty());
    assertNull(history.get(1).counterparty());
    assertNull(history.get(2).context(), "an empty context is stored as NULL");
    assertNotNull(repo.items.get(id).destroyedAt());
    assertEquals(clock.instant(), repo.items.get(id).destroyedAt());
    assertEquals(3, audit.recordedCount());
  }

  @Test
  void lookupOfUnknownIdIsEmpty() {
    CompletableFuture<Optional<ItemRecord>> future = audit.lookup(UUID.randomUUID());
    db.tick();
    assertTrue(future.join().isEmpty());
  }
}
