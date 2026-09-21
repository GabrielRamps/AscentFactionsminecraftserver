package gg.ascent.plugin.db;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import gg.ascent.api.db.DbException;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.db.DbStats;
import gg.ascent.api.db.SqlAction;
import gg.ascent.api.db.SqlFunction;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.slf4j.Logger;

/**
 * {@link DbExecutor} over a {@link DataSource}: a fixed pool of worker threads runs the SQL, and
 * every asynchronous result is handed back through {@link MainThread}.
 *
 * <p>The worker count matches the connection pool size, so a worker never queues on the pool and
 * the task queue is the only place work waits.
 */
public final class HikariDbExecutor implements DbExecutor, AutoCloseable {

  private final DataSource dataSource;
  private final ThreadPoolExecutor workers;
  private final MainThread mainThread;
  private final Logger log;
  private final AtomicLong completed = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private volatile boolean shuttingDown;

  /**
   * @param dataSource where connections come from; pool statistics are reported when this is a
   *     {@link HikariDataSource}
   * @param threads worker threads, normally the connection pool size
   * @param mainThread how results reach the main thread
   * @param log where warnings go
   */
  public HikariDbExecutor(DataSource dataSource, int threads, MainThread mainThread, Logger log) {
    if (threads < 1) {
      throw new IllegalArgumentException("threads must be at least 1");
    }
    this.dataSource = dataSource;
    this.mainThread = mainThread;
    this.log = log;
    this.workers =
        new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory());
  }

  private static ThreadFactory factory() {
    AtomicInteger n = new AtomicInteger();
    return runnable -> {
      Thread t = new Thread(runnable, "Ascent-DB-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }

  @Override
  public <T> CompletableFuture<T> supply(SqlFunction<T> work) {
    return submit(() -> withConnection(work));
  }

  @Override
  public CompletableFuture<Void> run(SqlAction work) {
    return submit(
        () -> {
          withConnection(
              c -> {
                work.run(c);
                return null;
              });
          return null;
        });
  }

  @Override
  public <T> CompletableFuture<T> transaction(SqlFunction<T> work) {
    return submit(() -> inTransaction(work));
  }

  @Override
  public <T> T supplyNow(SqlFunction<T> work) {
    warnIfMainThread();
    return withConnection(work);
  }

  @Override
  public void runNow(SqlAction work) {
    warnIfMainThread();
    withConnection(
        c -> {
          work.run(c);
          return null;
        });
  }

  @Override
  public <T> T transactionNow(SqlFunction<T> work) {
    warnIfMainThread();
    return inTransaction(work);
  }

  @Override
  public DbStats stats() {
    int active = 0;
    int idle = 0;
    int total = 0;
    int waiting = 0;
    if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
      HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
      active = pool.getActiveConnections();
      idle = pool.getIdleConnections();
      total = pool.getTotalConnections();
      waiting = pool.getThreadsAwaitingConnection();
    }
    return new DbStats(
        active, idle, total, waiting, workers.getQueue().size(), completed.get(), failed.get());
  }

  /** Marks the executor as shutting down, so blocking calls on the main thread stop warning. */
  public void beginShutdown() {
    shuttingDown = true;
  }

  /**
   * Stops accepting work and waits up to {@code grace} for queued tasks to finish. Tasks still
   * running after that are abandoned and logged.
   */
  public void close(Duration grace) {
    shuttingDown = true;
    workers.shutdown();
    try {
      if (!workers.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
        int dropped = workers.shutdownNow().size();
        log.error(
            "Database executor did not drain within {}; {} queued task(s) were dropped.",
            grace,
            dropped);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      workers.shutdownNow();
    }
  }

  @Override
  public void close() {
    close(Duration.ofSeconds(10));
  }

  private <T> CompletableFuture<T> submit(Task<T> task) {
    CompletableFuture<T> future = new CompletableFuture<>();
    try {
      workers.execute(
          () -> {
            T value;
            try {
              value = task.call();
            } catch (Throwable t) {
              deliver(() -> future.completeExceptionally(t));
              return;
            }
            deliver(() -> future.complete(value));
          });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      future.completeExceptionally(new DbException("database executor is shut down", e));
    }
    return future;
  }

  private void deliver(Runnable completion) {
    try {
      mainThread.execute(completion);
    } catch (RuntimeException e) {
      // The scheduler is gone (plugin disabling). Completing here beats never completing.
      log.warn("Main-thread scheduler refused a database continuation; completing inline.", e);
      completion.run();
    }
  }

  private <T> T withConnection(SqlFunction<T> work) {
    try (Connection c = dataSource.getConnection()) {
      T value = work.apply(c);
      completed.incrementAndGet();
      return value;
    } catch (SQLException e) {
      completed.incrementAndGet();
      failed.incrementAndGet();
      throw new DbException("database call failed: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      completed.incrementAndGet();
      failed.incrementAndGet();
      throw e;
    }
  }

  private <T> T inTransaction(SqlFunction<T> work) {
    return withConnection(
        c -> {
          boolean previous = c.getAutoCommit();
          c.setAutoCommit(false);
          try {
            T value = work.apply(c);
            c.commit();
            return value;
          } catch (SQLException | RuntimeException e) {
            try {
              c.rollback();
            } catch (SQLException rollback) {
              e.addSuppressed(rollback);
            }
            throw e;
          } finally {
            c.setAutoCommit(previous);
          }
        });
  }

  private void warnIfMainThread() {
    if (!shuttingDown && mainThread.isCurrent()) {
      log.warn(
          "Blocking database call on the main thread; every player waits for it.",
          new DbException("called from"));
    }
  }

  @FunctionalInterface
  private interface Task<T> {
    T call() throws Exception;
  }
}
