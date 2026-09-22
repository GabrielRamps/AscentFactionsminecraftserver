package gg.ascent.plugin.mine;

import gg.ascent.plugin.db.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** {@link MinePlotRepository} over the V1 schema. */
public final class SqlMinePlotRepository implements MinePlotRepository {

  @Override
  public List<Row> all(Connection c) throws SQLException {
    try (PreparedStatement ps =
            c.prepareStatement(
                "SELECT plot_index, owner_uuid, tier, allocated_at, last_used, mined_blocks"
                    + " FROM mine_plots ORDER BY plot_index");
        ResultSet rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) {
        Integer tier = Sql.getNullableInt(rs, "tier");
        out.add(
            new Row(
                rs.getInt("plot_index"),
                Sql.getNullableUuid(rs, "owner_uuid"),
                tier == null ? 1 : tier,
                Sql.getNullableInstant(rs, "allocated_at"),
                Sql.getNullableInstant(rs, "last_used"),
                rs.getInt("mined_blocks")));
      }
      return out;
    }
  }

  @Override
  public void save(Connection c, Row row) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO mine_plots (plot_index, owner_uuid, tier, allocated_at, last_used,"
                + " mined_blocks) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                + " owner_uuid = VALUES(owner_uuid), tier = VALUES(tier),"
                + " allocated_at = VALUES(allocated_at), last_used = VALUES(last_used),"
                + " mined_blocks = VALUES(mined_blocks)")) {
      ps.setInt(1, row.index());
      Sql.setNullableUuid(ps, 2, row.owner());
      ps.setInt(3, row.tier());
      Sql.setNullableInstant(ps, 4, row.allocatedAt());
      Sql.setNullableInstant(ps, 5, row.lastUsed());
      ps.setInt(6, row.minedBlocks());
      ps.executeUpdate();
    }
  }
}
