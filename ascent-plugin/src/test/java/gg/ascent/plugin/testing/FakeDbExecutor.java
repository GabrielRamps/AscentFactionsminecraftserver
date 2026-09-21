package gg.ascent.plugin.testing;

import gg.ascent.api.db.DbException;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.db.DbStats;
import gg.ascent.api.db.SqlAction;
import gg.ascent.api.db.SqlFunction;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link DbExecutor} that runs work inline with no connection and parks each completion until
 * {@link #tick()} is called, so a test controls exactly when "the main thread" sees a result.
 */
public final class FakeDbExecutor implements DbExecutor {

  private final Deque<Runnable> completions = new ArrayDeque<>();
  public int transactions;

  /** Runs every queued completion, as one server tick would. */
  public void tick() {
    Runnable next;
    while ((next = completions.poll()) != null) {
      next.run();
    }
  }

  public int pendingCompletions() {
    return completions.size();
  }

  @Override
  public <T> CompletableFuture<T> supply(SqlFunction<T> work) {
    CompletableFuture<T> future = new CompletableFuture<>();
    try {
      T value = work.apply(null);
      completions.add(() -> future.complete(value));
    } catch (SQLException | RuntimeException e) {
      completions.add(() -> future.completeExceptionally(wrap(e)));
    }
    return future;
  }

  @Override
  public CompletableFuture<Void> run(SqlAction work) {
    return supply(
        c -> {
          work.run(c);
          return null;
        });
  }

  @Override
  public <T> CompletableFuture<T> transaction(SqlFunction<T> work) {
    transactions++;
    return supply(work);
  }

  @Override
  public <T> T supplyNow(SqlFunction<T> work) {
    try {
      return work.apply(null);
    } catch (SQLException e) {
      throw wrap(e);
    }
  }

  @Override
  public void runNow(SqlAction work) {
    supplyNow(
        c -> {
          work.run(c);
          return null;
        });
  }

  @Override
  public <T> T transactionNow(SqlFunction<T> work) {
    transactions++;
    return supplyNow(work);
  }

  @Override
  public DbStats stats() {
    return new DbStats(0, 0, 0, 0, completions.size(), 0, 0);
  }

  private static RuntimeException wrap(Exception e) {
    return e instanceof RuntimeException r ? r : new DbException(e.getMessage(), e);
  }
}
