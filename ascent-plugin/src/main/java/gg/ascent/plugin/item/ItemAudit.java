package gg.ascent.plugin.item;

import com.google.gson.Gson;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The database side of the item registry, kept free of Bukkit so it can be unit tested: rows for
 * new items, event rows, and lookups. {@link ItemRegistryImpl} adds the item-stack tagging.
 */
public final class ItemAudit {

  private static final Gson GSON = new Gson();

  private final DbExecutor db;
  private final ItemRepository repo;
  private final Clock clock;
  private final Logger log;
  private final AtomicLong tagged = new AtomicLong();
  private final AtomicLong recorded = new AtomicLong();

  public ItemAudit(DbExecutor db, ItemRepository repo, Clock clock, Logger log) {
    this.db = db;
    this.repo = repo;
    this.clock = clock;
    this.log = log;
  }

  /** Writes the {@code items} row and its CREATED event in one transaction. Never blocks. */
  public ItemRecord created(
      UUID itemId,
      ItemKind kind,
      Map<String, Object> data,
      int quantity,
      @Nullable UUID creator,
      String reason) {
    Instant now = clock.instant();
    ItemRecord record =
        new ItemRecord(itemId, kind, GSON.toJson(data), quantity, creator, reason, now, null);
    tagged.incrementAndGet();
    Futures.logFailure(
        db.transaction(
            c -> {
              repo.insertItem(c, record);
              repo.insertEvent(
                  c,
                  itemId,
                  ItemEvent.CREATED,
                  creator,
                  null,
                  GSON.toJson(Map.of("reason", reason, "quantity", quantity)),
                  now);
              return null;
            }),
        log,
        "registering item " + itemId);
    return record;
  }

  /** Writes one event row; a DESTROYED event also closes the item. Never blocks. */
  public void record(
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable Map<String, Object> context) {
    Instant now = clock.instant();
    String json = context == null || context.isEmpty() ? null : GSON.toJson(context);
    recorded.incrementAndGet();
    Futures.logFailure(
        db.run(
            c -> {
              repo.insertEvent(c, itemId, event, actor, counterparty, json, now);
              if (event == ItemEvent.DESTROYED) {
                repo.markDestroyed(c, itemId, now);
              }
            }),
        log,
        "recording " + event + " on item " + itemId);
  }

  public CompletableFuture<Optional<ItemRecord>> lookup(UUID itemId) {
    return db.supply(c -> repo.findItem(c, itemId));
  }

  public CompletableFuture<List<ItemEventRecord>> history(UUID itemId) {
    return db.supply(c -> repo.events(c, itemId));
  }

  public CompletableFuture<Integer> openAlertCount() {
    return db.supply(repo::openAlertCount);
  }

  /** Items tagged since startup. */
  public long taggedCount() {
    return tagged.get();
  }

  /** Events recorded since startup. */
  public long recordedCount() {
    return recorded.get();
  }
}
