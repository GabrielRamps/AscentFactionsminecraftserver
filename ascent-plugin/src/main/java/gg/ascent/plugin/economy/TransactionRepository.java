package gg.ascent.plugin.economy;

import gg.ascent.api.economy.TxReason;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The {@code money_transactions} table. */
public interface TransactionRepository {

  /** One row per mutation: {@code amount} is signed, {@code balanceAfter} the resulting balance. */
  void record(
      Connection c,
      @Nullable UUID player,
      @Nullable Integer factionId,
      long amount,
      long balanceAfter,
      TxReason reason,
      @Nullable String ref,
      Instant at)
      throws SQLException;
}
