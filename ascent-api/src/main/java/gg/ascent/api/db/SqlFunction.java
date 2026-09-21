package gg.ascent.api.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that produces a value. */
@FunctionalInterface
public interface SqlFunction<T> {
  T apply(Connection connection) throws SQLException;
}
