package gg.ascent.plugin.combat;

import com.google.gson.Gson;
import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** {@link DeathLogRepository} over MariaDB. */
public final class SqlDeathLogRepository implements DeathLogRepository {

  private static final Gson GSON = new Gson();

  @Override
  public void insert(
      Connection c,
      UUID victim,
      @Nullable UUID killer,
      String world,
      int x,
      int y,
      int z,
      List<UUID> droppedItemIds,
      Instant at)
      throws SQLException {
    List<String> ids = new ArrayList<>(droppedItemIds.size());
    for (UUID id : droppedItemIds) {
      ids.add(id.toString());
    }
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO death_log (victim_uuid, killer_uuid, world, x, y, z, dropped_item_ids,"
                + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      Sql.setUuid(s, 1, victim);
      Sql.setNullableUuid(s, 2, killer);
      s.setString(3, world);
      s.setInt(4, x);
      s.setInt(5, y);
      s.setInt(6, z);
      s.setString(7, GSON.toJson(ids));
      Sql.setInstant(s, 8, at);
      s.executeUpdate();
    }
  }
}
