package gg.ascent.plugin.player;

import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.db.Sql;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link PlayerRepository} over the V1 schema. */
public final class SqlPlayerRepository implements PlayerRepository {

  private static final String COLUMNS =
      "uuid, name, `rank`, xp, xp_total, xp_banked, balance, power, power_updated, faction_id,"
          + " starter_kit_claimed, first_seen, last_seen, play_seconds";

  @Override
  public Optional<PlayerSnapshot> find(Connection c, UUID uuid) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("SELECT " + COLUMNS + " FROM players WHERE uuid = ?")) {
      Sql.setUuid(ps, 1, uuid);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(read(rs)) : Optional.empty();
      }
    }
  }

  @Override
  public Optional<PlayerSnapshot> findByName(Connection c, String name) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT " + COLUMNS + " FROM players WHERE name = ? ORDER BY last_seen DESC LIMIT 1")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(read(rs)) : Optional.empty();
      }
    }
  }

  @Override
  public void insert(Connection c, PlayerSnapshot row) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO players ("
                + COLUMNS
                + ", updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      Sql.setUuid(ps, 1, row.uuid());
      ps.setString(2, row.name());
      ps.setInt(3, row.rank());
      ps.setLong(4, row.xp());
      ps.setLong(5, row.xpTotal());
      ps.setLong(6, row.xpBanked());
      ps.setLong(7, row.balance());
      ps.setBigDecimal(8, power(row.power()));
      Sql.setInstant(ps, 9, row.powerUpdated());
      if (row.factionId() == null) {
        ps.setNull(10, java.sql.Types.INTEGER);
      } else {
        ps.setInt(10, row.factionId());
      }
      ps.setBoolean(11, row.starterKitClaimed());
      Sql.setInstant(ps, 12, row.firstSeen());
      Sql.setInstant(ps, 13, row.lastSeen());
      ps.setLong(14, row.playSeconds());
      Sql.setInstant(ps, 15, Instant.now());
      ps.executeUpdate();
    }
  }

  @Override
  public void update(Connection c, PlayerSnapshot row) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE players SET name = ?, `rank` = ?, xp = ?, xp_total = ?, xp_banked = ?,"
                + " balance = ?, power = ?, power_updated = ?, faction_id = ?,"
                + " starter_kit_claimed = ?, last_seen = ?, play_seconds = ?, updated_at = ?"
                + " WHERE uuid = ?")) {
      ps.setString(1, row.name());
      ps.setInt(2, row.rank());
      ps.setLong(3, row.xp());
      ps.setLong(4, row.xpTotal());
      ps.setLong(5, row.xpBanked());
      ps.setLong(6, row.balance());
      ps.setBigDecimal(7, power(row.power()));
      Sql.setInstant(ps, 8, row.powerUpdated());
      if (row.factionId() == null) {
        ps.setNull(9, java.sql.Types.INTEGER);
      } else {
        ps.setInt(9, row.factionId());
      }
      ps.setBoolean(10, row.starterKitClaimed());
      Sql.setInstant(ps, 11, row.lastSeen());
      ps.setLong(12, row.playSeconds());
      Sql.setInstant(ps, 13, Instant.now());
      Sql.setUuid(ps, 14, row.uuid());
      if (ps.executeUpdate() == 0) {
        throw new SQLException("no players row for " + row.uuid());
      }
    }
  }

  @Override
  public long adjustBalance(Connection c, UUID uuid, long delta) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE players SET balance = balance + ?, updated_at = ? WHERE uuid = ?"
                + " AND balance + ? >= 0")) {
      ps.setLong(1, delta);
      Sql.setInstant(ps, 2, Instant.now());
      Sql.setUuid(ps, 3, uuid);
      ps.setLong(4, delta);
      if (ps.executeUpdate() == 0) {
        throw new SQLException("balance change refused for " + uuid + " (missing or overdrawn)");
      }
    }
    try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
      Sql.setUuid(ps, 1, uuid);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  @Override
  public long openSession(Connection c, UUID uuid, Instant joinedAt) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO sessions (player_uuid, joined_at) VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
      Sql.setUuid(ps, 1, uuid);
      Sql.setInstant(ps, 2, joinedAt);
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("sessions insert returned no id");
        }
        return keys.getLong(1);
      }
    }
  }

  @Override
  public void closeSession(Connection c, long sessionId, Instant quitAt) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("UPDATE sessions SET quit_at = ? WHERE id = ? AND quit_at IS NULL")) {
      Sql.setInstant(ps, 1, quitAt);
      ps.setLong(2, sessionId);
      ps.executeUpdate();
    }
  }

  @Override
  public List<BalanceEntry> topBalances(Connection c, int limit) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT uuid, name, balance FROM players ORDER BY balance DESC, name ASC LIMIT ?")) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        List<BalanceEntry> out = new ArrayList<>();
        while (rs.next()) {
          out.add(
              new BalanceEntry(
                  Sql.getUuid(rs, "uuid"), rs.getString("name"), rs.getLong("balance")));
        }
        return out;
      }
    }
  }

  @Override
  public List<RankEntry> topRanks(Connection c, int limit) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT uuid, name, `rank`, xp FROM players ORDER BY `rank` DESC, xp DESC, name ASC"
                + " LIMIT ?")) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        List<RankEntry> out = new ArrayList<>();
        while (rs.next()) {
          out.add(
              new RankEntry(
                  Sql.getUuid(rs, "uuid"),
                  rs.getString("name"),
                  rs.getInt("rank"),
                  rs.getLong("xp")));
        }
        return out;
      }
    }
  }

  private static PlayerSnapshot read(ResultSet rs) throws SQLException {
    return new PlayerSnapshot(
        Sql.getUuid(rs, "uuid"),
        rs.getString("name"),
        rs.getInt("rank"),
        rs.getLong("xp"),
        rs.getLong("xp_total"),
        rs.getLong("xp_banked"),
        rs.getLong("balance"),
        rs.getBigDecimal("power").doubleValue(),
        Sql.getInstant(rs, "power_updated"),
        Sql.getNullableInt(rs, "faction_id"),
        rs.getBoolean("starter_kit_claimed"),
        Sql.getInstant(rs, "first_seen"),
        Sql.getInstant(rs, "last_seen"),
        rs.getLong("play_seconds"));
  }

  private static BigDecimal power(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
