package gg.ascent.api.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that produces nothing. */
@FunctionalInterface
public interface SqlAction {
  void run(Connection connection) throws SQLException;
}
