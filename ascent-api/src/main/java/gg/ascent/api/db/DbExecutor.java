package gg.ascent.api.db;

import java.util.concurrent.CompletableFuture;

/**
 * Runs database work off the main thread and hands the result back on it (PRD E1-S2).
 *
 * <p>The asynchronous methods run the work on a pool thread and complete the returned future on the
 * main thread, so a continuation attached with {@code thenAccept} may touch Bukkit state freely. A
 * failed call completes the future exceptionally with a {@link DbException}.
 *
 * <p>The blocking {@code ...Now} variants run the work on the calling thread. They exist for code
 * that is already off the main thread, such as the login handshake, and for the shutdown flush,
 * when no more ticks will run. Calling them from the main thread while the server is running stalls
 * every player and is logged as a warning.
 */
public interface DbExecutor {

  /** Runs {@code work} on a pool thread; the future completes on the main thread. */
  <T> CompletableFuture<T> supply(SqlFunction<T> work);

  /** Runs {@code work} on a pool thread; the future completes on the main thread. */
  CompletableFuture<Void> run(SqlAction work);

  /**
   * Runs {@code work} in one transaction on a pool thread: committed when it returns, rolled back
   * when it throws. The future completes on the main thread.
   */
  <T> CompletableFuture<T> transaction(SqlFunction<T> work);

  /** Runs {@code work} on the calling thread and returns its result. */
  <T> T supplyNow(SqlFunction<T> work);

  /** Runs {@code work} on the calling thread. */
  void runNow(SqlAction work);

  /** Runs {@code work} in one transaction on the calling thread. */
  <T> T transactionNow(SqlFunction<T> work);

  /** A point-in-time snapshot of the pool and the task queue. */
  DbStats stats();
}
