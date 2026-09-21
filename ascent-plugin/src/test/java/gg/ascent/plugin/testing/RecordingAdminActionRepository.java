package gg.ascent.plugin.testing;

import gg.ascent.plugin.admin.AdminActionRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Remembers every {@code admin_actions} row instead of writing it. */
public final class RecordingAdminActionRepository implements AdminActionRepository {

  public record Row(UUID actor, String action, @Nullable String target, @Nullable String args) {}

  public final List<Row> rows = new ArrayList<>();

  @Override
  public synchronized void record(
      Connection c,
      UUID actor,
      String action,
      @Nullable String target,
      @Nullable String argsJson,
      Instant at) {
    rows.add(new Row(actor, action, target, argsJson));
  }
}
