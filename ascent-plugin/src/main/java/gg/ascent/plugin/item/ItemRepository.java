package gg.ascent.plugin.item;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The {@code items}, {@code item_events} and {@code dupe_alerts} tables. */
public interface ItemRepository {

  void insertItem(Connection c, ItemRecord item) throws SQLException;

  Optional<ItemRecord> findItem(Connection c, UUID itemId) throws SQLException;

  void markDestroyed(Connection c, UUID itemId, Instant at) throws SQLException;

  void insertEvent(
      Connection c,
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable String contextJson,
      Instant at)
      throws SQLException;

  List<ItemEventRecord> events(Connection c, UUID itemId) throws SQLException;

  /** {@code quantity_created} for each id that exists. Unknown ids are absent from the map. */
  Map<UUID, Integer> quantities(Connection c, Collection<UUID> itemIds) throws SQLException;

  /** Ids among {@code itemIds} that already have an unresolved alert. */
  Set<UUID> openAlerts(Connection c, Collection<UUID> itemIds) throws SQLException;

  void insertAlert(Connection c, UUID itemId, int observed, String holdersJson, Instant at)
      throws SQLException;

  /** Unresolved alerts, for {@code /ascent debug items}. */
  int openAlertCount(Connection c) throws SQLException;
}
