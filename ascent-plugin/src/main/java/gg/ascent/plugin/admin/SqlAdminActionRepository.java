package gg.ascent.plugin.admin;

import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** {@link AdminActionRepository} over the V1 schema. */
public final class SqlAdminActionRepository implements AdminActionRepository {

  @Override
  public void record(
      Connection c,
      UUID actor,
      String action,
      @Nullable String target,
      @Nullable String argsJson,
      Instant at)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO admin_actions (actor_uuid, action, target, args, created_at)"
                + " VALUES (?, ?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, actor);
      ps.setString(2, action.length() > 64 ? action.substring(0, 64) : action);
      if (target == null) {
        ps.setNull(3, java.sql.Types.VARCHAR);
      } else {
        ps.setString(3, target.length() > 64 ? target.substring(0, 64) : target);
      }
      if (argsJson == null) {
        ps.setNull(4, java.sql.Types.VARCHAR);
      } else {
        ps.setString(4, argsJson);
      }
      Sql.setInstant(ps, 5, at);
      ps.executeUpdate();
    }
  }
}
