package gg.ascent.plugin.combat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The {@code death_log} table (PRD E9-S2, §6.3). */
public interface DeathLogRepository {

  void insert(
      Connection c,
      UUID victim,
      @Nullable UUID killer,
      String world,
      int x,
      int y,
      int z,
      List<UUID> droppedItemIds,
      Instant at)
      throws SQLException;
}
