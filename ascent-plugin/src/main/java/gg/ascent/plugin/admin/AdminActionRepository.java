package gg.ascent.plugin.admin;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The {@code admin_actions} table (PRD E1-S6). */
public interface AdminActionRepository {

  /** Console actions are recorded under {@code AdminActionLog.CONSOLE}. */
  void record(
      Connection c,
      UUID actor,
      String action,
      @Nullable String target,
      @Nullable String argsJson,
      Instant at)
      throws SQLException;
}
