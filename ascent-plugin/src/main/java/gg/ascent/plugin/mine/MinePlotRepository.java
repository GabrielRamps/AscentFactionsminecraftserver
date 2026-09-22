package gg.ascent.plugin.mine;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The {@code mine_plots} table. */
public interface MinePlotRepository {

  /** One row. A released plot has a null owner. */
  record Row(
      int index,
      @Nullable UUID owner,
      int tier,
      @Nullable Instant allocatedAt,
      @Nullable Instant lastUsed,
      int minedBlocks) {}

  List<Row> all(Connection c) throws SQLException;

  /** Inserts or replaces the row for {@code row.index()}. */
  void save(Connection c, Row row) throws SQLException;
}
