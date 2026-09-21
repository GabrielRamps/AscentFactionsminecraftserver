package gg.ascent.plugin.economy;

import gg.ascent.api.economy.TxReason;
import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** {@link TransactionRepository} over the V1 schema. */
public final class SqlTransactionRepository implements TransactionRepository {

  @Override
  public void record(
      Connection c,
      @Nullable UUID player,
      @Nullable Integer factionId,
      long amount,
      long balanceAfter,
      TxReason reason,
      @Nullable String ref,
      Instant at)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO money_transactions (player_uuid, faction_id, amount, balance_after,"
                + " reason, ref, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
      Sql.setNullableUuid(ps, 1, player);
      if (factionId == null) {
        ps.setNull(2, java.sql.Types.INTEGER);
      } else {
        ps.setInt(2, factionId);
      }
      ps.setLong(3, amount);
      ps.setLong(4, balanceAfter);
      ps.setString(5, reason.name());
      if (ref == null) {
        ps.setNull(6, java.sql.Types.VARCHAR);
      } else {
        ps.setString(6, ref.length() > 64 ? ref.substring(0, 64) : ref);
      }
      Sql.setInstant(ps, 7, at);
      ps.executeUpdate();
    }
  }
}
