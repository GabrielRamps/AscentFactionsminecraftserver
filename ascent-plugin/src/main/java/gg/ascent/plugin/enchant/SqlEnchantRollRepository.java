package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.ApplyResult;
import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** {@link EnchantRollRepository} over the V1 schema. */
public final class SqlEnchantRollRepository implements EnchantRollRepository {

  @Override
  public void record(
      Connection c,
      UUID player,
      UUID bookItemId,
      UUID targetItemId,
      String enchantId,
      int level,
      int success,
      int destroy,
      ApplyResult outcome,
      Instant at)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO enchant_rolls (player_uuid, book_item_id, target_item_id, enchant_id,"
                + " level, success, destroy, outcome, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
                + " ?)")) {
      Sql.setUuid(ps, 1, player);
      Sql.setUuid(ps, 2, bookItemId);
      Sql.setUuid(ps, 3, targetItemId);
      ps.setString(4, enchantId);
      ps.setInt(5, level);
      ps.setInt(6, success);
      ps.setInt(7, destroy);
      ps.setString(8, outcome.name());
      Sql.setInstant(ps, 9, at);
      ps.executeUpdate();
    }
  }
}
