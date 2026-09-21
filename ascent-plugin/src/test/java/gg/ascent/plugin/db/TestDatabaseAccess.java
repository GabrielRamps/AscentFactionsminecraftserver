package gg.ascent.plugin.db;

import java.sql.SQLException;

/** Lets tests in other packages use {@link TestDatabase}. */
public final class TestDatabaseAccess {

  private TestDatabaseAccess() {}

  /** Wipes the CI schema and returns a freshly migrated database, or skips the test. */
  public static Database freshDatabase() throws SQLException {
    TestDatabase.wipe();
    return TestDatabase.open();
  }
}
