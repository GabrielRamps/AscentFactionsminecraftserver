package gg.ascent.plugin.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Small helpers for the column conventions in PRD §6.3: {@code DATETIME(3)} columns hold UTC and
 * are read and written as {@link LocalDateTime} so no driver or session time zone ever shifts them;
 * UUIDs are {@code CHAR(36)}.
 */
public final class Sql {

  private Sql() {}

  /** Binds an instant as UTC wall-clock time. */
  public static void setInstant(PreparedStatement statement, int index, Instant instant)
      throws SQLException {
    statement.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
  }

  /** Binds an instant, or SQL NULL. */
  public static void setNullableInstant(
      PreparedStatement statement, int index, @Nullable Instant instant) throws SQLException {
    if (instant == null) {
      statement.setNull(index, java.sql.Types.TIMESTAMP);
    } else {
      setInstant(statement, index, instant);
    }
  }

  /** Reads a UTC {@code DATETIME(3)} column. */
  public static Instant getInstant(ResultSet row, String column) throws SQLException {
    LocalDateTime value = row.getObject(column, LocalDateTime.class);
    if (value == null) {
      throw new SQLException("column " + column + " is NULL");
    }
    return value.toInstant(ZoneOffset.UTC);
  }

  /** Reads a nullable UTC {@code DATETIME(3)} column. */
  public static @Nullable Instant getNullableInstant(ResultSet row, String column)
      throws SQLException {
    LocalDateTime value = row.getObject(column, LocalDateTime.class);
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }

  /** Binds a UUID as its 36-character text form. */
  public static void setUuid(PreparedStatement statement, int index, UUID uuid)
      throws SQLException {
    statement.setString(index, uuid.toString());
  }

  /** Binds a UUID, or SQL NULL. */
  public static void setNullableUuid(PreparedStatement statement, int index, @Nullable UUID uuid)
      throws SQLException {
    if (uuid == null) {
      statement.setNull(index, java.sql.Types.CHAR);
    } else {
      statement.setString(index, uuid.toString());
    }
  }

  /** Reads a {@code CHAR(36)} column as a UUID. */
  public static UUID getUuid(ResultSet row, String column) throws SQLException {
    String value = row.getString(column);
    if (value == null) {
      throw new SQLException("column " + column + " is NULL");
    }
    return UUID.fromString(value);
  }

  /** Reads a nullable {@code CHAR(36)} column. */
  public static @Nullable UUID getNullableUuid(ResultSet row, String column) throws SQLException {
    String value = row.getString(column);
    return value == null ? null : UUID.fromString(value);
  }

  /** Reads a nullable integer column as a boxed value. */
  public static @Nullable Integer getNullableInt(ResultSet row, String column) throws SQLException {
    int value = row.getInt(column);
    return row.wasNull() ? null : value;
  }
}
