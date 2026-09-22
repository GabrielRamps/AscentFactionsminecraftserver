package gg.ascent.plugin.testing;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.plugin.item.ItemRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Maps standing in for the items, item_events and dupe_alerts tables. */
public final class InMemoryItemRepository implements ItemRepository {

  public record Alert(UUID itemId, int observed, String holders) {}

  public final Map<UUID, ItemRecord> items = new LinkedHashMap<>();
  public final List<ItemEventRecord> events = new ArrayList<>();
  public final List<Alert> alerts = new ArrayList<>();
  private long nextId = 1;

  @Override
  public synchronized void insertItem(Connection c, ItemRecord item) {
    items.put(item.itemId(), item);
  }

  @Override
  public synchronized Optional<ItemRecord> findItem(Connection c, UUID itemId) {
    return Optional.ofNullable(items.get(itemId));
  }

  @Override
  public synchronized void markDestroyed(Connection c, UUID itemId, Instant at) {
    ItemRecord r = items.get(itemId);
    if (r != null && r.destroyedAt() == null) {
      items.put(
          itemId,
          new ItemRecord(
              r.itemId(),
              r.kind(),
              r.data(),
              r.quantityCreated(),
              r.createdBy(),
              r.createdReason(),
              r.createdAt(),
              at));
    }
  }

  @Override
  public synchronized void insertEvent(
      Connection c,
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable String contextJson,
      Instant at) {
    events.add(new ItemEventRecord(nextId++, itemId, event, actor, counterparty, contextJson, at));
  }

  @Override
  public synchronized List<ItemEventRecord> events(Connection c, UUID itemId) {
    return events.stream().filter(e -> e.itemId().equals(itemId)).toList();
  }

  @Override
  public synchronized Map<UUID, Integer> quantities(Connection c, Collection<UUID> itemIds) {
    Map<UUID, Integer> out = new HashMap<>();
    for (UUID id : itemIds) {
      ItemRecord r = items.get(id);
      if (r != null) {
        out.put(id, r.quantityCreated());
      }
    }
    return out;
  }

  @Override
  public synchronized Set<UUID> openAlerts(Connection c, Collection<UUID> itemIds) {
    Set<UUID> out = new HashSet<>();
    for (Alert a : alerts) {
      if (itemIds.contains(a.itemId())) {
        out.add(a.itemId());
      }
    }
    return out;
  }

  @Override
  public synchronized void insertAlert(
      Connection c, UUID itemId, int observed, String holdersJson, Instant at) {
    alerts.add(new Alert(itemId, observed, holdersJson));
  }

  @Override
  public synchronized int openAlertCount(Connection c) {
    return alerts.size();
  }
}
