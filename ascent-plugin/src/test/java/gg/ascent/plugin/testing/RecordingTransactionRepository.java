package gg.ascent.plugin.testing;

import gg.ascent.api.economy.TxReason;
import gg.ascent.plugin.economy.TransactionRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Remembers every {@code money_transactions} row instead of writing it. */
public final class RecordingTransactionRepository implements TransactionRepository {

  public record Row(
      @Nullable UUID player,
      @Nullable Integer factionId,
      long amount,
      long balanceAfter,
      TxReason reason,
      @Nullable String ref) {}

  public final List<Row> rows = new ArrayList<>();

  @Override
  public synchronized void record(
      Connection c,
      @Nullable UUID player,
      @Nullable Integer factionId,
      long amount,
      long balanceAfter,
      TxReason reason,
      @Nullable String ref,
      Instant at) {
    rows.add(new Row(player, factionId, amount, balanceAfter, reason, ref));
  }
}
