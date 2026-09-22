package gg.ascent.plugin.item;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** {@link ItemRepository} over the V1 schema. */
public final class SqlItemRepository implements ItemRepository {

  @Override
  public void insertItem(Connection c, ItemRecord item) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO items (item_id, kind, data, quantity_created, created_by,"
                + " created_reason, created_at, destroyed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, item.itemId());
      ps.setString(2, item.kind().name());
      ps.setString(3, item.data());
      ps.setInt(4, item.quantityCreated());
      Sql.setNullableUuid(ps, 5, item.createdBy());
      ps.setString(6, clip(item.createdReason(), 32));
      Sql.setInstant(ps, 7, item.createdAt());
      Sql.setNullableInstant(ps, 8, item.destroyedAt());
      ps.executeUpdate();
    }
  }

  @Override
  public Optional<ItemRecord> findItem(Connection c, UUID itemId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT item_id, kind, data, quantity_created, created_by, created_reason,"
                + " created_at, destroyed_at FROM items WHERE item_id = ?")) {
      Sql.setUuid(ps, 1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new ItemRecord(
                Sql.getUuid(rs, "item_id"),
                ItemKind.valueOf(rs.getString("kind")),
                rs.getString("data"),
                rs.getInt("quantity_created"),
                Sql.getNullableUuid(rs, "created_by"),
                rs.getString("created_reason"),
                Sql.getInstant(rs, "created_at"),
                Sql.getNullableInstant(rs, "destroyed_at")));
      }
    }
  }

  @Override
  public void markDestroyed(Connection c, UUID itemId, Instant at) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE items SET destroyed_at = ? WHERE item_id = ? AND destroyed_at IS NULL")) {
      Sql.setInstant(ps, 1, at);
      Sql.setUuid(ps, 2, itemId);
      ps.executeUpdate();
    }
  }

  @Override
  public void insertEvent(
      Connection c,
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable String contextJson,
      Instant at)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO item_events (item_id, event, actor_uuid, counterparty_uuid, context,"
                + " created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, itemId);
      ps.setString(2, event.name());
      Sql.setNullableUuid(ps, 3, actor);
      Sql.setNullableUuid(ps, 4, counterparty);
      if (contextJson == null) {
        ps.setNull(5, java.sql.Types.VARCHAR);
      } else {
        ps.setString(5, contextJson);
      }
      Sql.setInstant(ps, 6, at);
      ps.executeUpdate();
    }
  }

  @Override
  public List<ItemEventRecord> events(Connection c, UUID itemId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT id, item_id, event, actor_uuid, counterparty_uuid, context, created_at"
                + " FROM item_events WHERE item_id = ? ORDER BY created_at, id")) {
      Sql.setUuid(ps, 1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        List<ItemEventRecord> out = new ArrayList<>();
        while (rs.next()) {
          out.add(
              new ItemEventRecord(
                  rs.getLong("id"),
                  Sql.getUuid(rs, "item_id"),
                  ItemEvent.valueOf(rs.getString("event")),
                  Sql.getNullableUuid(rs, "actor_uuid"),
                  Sql.getNullableUuid(rs, "counterparty_uuid"),
                  rs.getString("context"),
                  Sql.getInstant(rs, "created_at")));
        }
        return out;
      }
    }
  }

  @Override
  public Map<UUID, Integer> quantities(Connection c, Collection<UUID> itemIds) throws SQLException {
    Map<UUID, Integer> out = new HashMap<>();
    if (itemIds.isEmpty()) {
      return out;
    }
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT item_id, quantity_created FROM items WHERE item_id IN ("
                + placeholders(itemIds.size())
                + ")")) {
      bind(ps, itemIds);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.put(Sql.getUuid(rs, "item_id"), rs.getInt("quantity_created"));
        }
      }
    }
    return out;
  }

  @Override
  public Set<UUID> openAlerts(Connection c, Collection<UUID> itemIds) throws SQLException {
    Set<UUID> out = new HashSet<>();
    if (itemIds.isEmpty()) {
      return out;
    }
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT DISTINCT item_id FROM dupe_alerts WHERE resolved_at IS NULL AND item_id IN ("
                + placeholders(itemIds.size())
                + ")")) {
      bind(ps, itemIds);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(Sql.getUuid(rs, "item_id"));
        }
      }
    }
    return out;
  }

  @Override
  public void insertAlert(Connection c, UUID itemId, int observed, String holdersJson, Instant at)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO dupe_alerts (item_id, observed_count, holders, created_at)"
                + " VALUES (?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, itemId);
      ps.setInt(2, observed);
      ps.setString(3, holdersJson);
      Sql.setInstant(ps, 4, at);
      ps.executeUpdate();
    }
  }

  @Override
  public int openAlertCount(Connection c) throws SQLException {
    try (PreparedStatement ps =
            c.prepareStatement("SELECT COUNT(*) FROM dupe_alerts WHERE resolved_at IS NULL");
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static String placeholders(int n) {
    return String.join(", ", java.util.Collections.nCopies(n, "?"));
  }

  private static void bind(PreparedStatement ps, Collection<UUID> ids) throws SQLException {
    int i = 1;
    for (UUID id : ids) {
      Sql.setUuid(ps, i++, id);
    }
  }

  private static String clip(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }
}
