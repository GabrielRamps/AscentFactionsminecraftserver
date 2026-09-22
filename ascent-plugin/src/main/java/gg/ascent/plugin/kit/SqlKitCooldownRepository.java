package gg.ascent.plugin.kit;

import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** {@link KitCooldownRepository} over the V1 schema. */
public final class SqlKitCooldownRepository implements KitCooldownRepository {

  @Override
  public Map<String, Instant> load(Connection c, UUID player) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("SELECT kit_id, next_at FROM kit_cooldowns WHERE player_uuid = ?")) {
      Sql.setUuid(ps, 1, player);
      try (ResultSet rs = ps.executeQuery()) {
        Map<String, Instant> out = new HashMap<>();
        while (rs.next()) {
          out.put(rs.getString("kit_id"), Sql.getInstant(rs, "next_at"));
        }
        return out;
      }
    }
  }

  @Override
  public void set(Connection c, UUID player, String kitId, Instant nextAt) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO kit_cooldowns (player_uuid, kit_id, next_at) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE next_at = VALUES(next_at)")) {
      Sql.setUuid(ps, 1, player);
      ps.setString(2, kitId);
      Sql.setInstant(ps, 3, nextAt);
      ps.executeUpdate();
    }
  }
}
