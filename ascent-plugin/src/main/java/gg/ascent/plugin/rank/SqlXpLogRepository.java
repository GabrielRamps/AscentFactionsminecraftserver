package gg.ascent.plugin.rank;

import gg.ascent.api.rank.XpSource;
import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** {@link XpLogRepository} over the V1 schema, using MariaDB's upsert. */
public final class SqlXpLogRepository implements XpLogRepository {

  @Override
  public void add(Connection c, UUID player, XpSource source, long amount, Instant minute)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO xp_log (player_uuid, source, amount, minute) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)")) {
      Sql.setUuid(ps, 1, player);
      ps.setString(2, source.name());
      ps.setInt(3, (int) Math.min(Integer.MAX_VALUE, amount));
      Sql.setInstant(ps, 4, minute.truncatedTo(ChronoUnit.MINUTES));
      ps.executeUpdate();
    }
  }
}
