package gg.ascent.plugin.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.ascent.api.db.DbException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The executor's threading contract, against a mocked pool: no database needed. */
class HikariDbExecutorTest {

  private DataSource dataSource;
  private Connection connection;
  private QueueMainThread mainThread;
  private HikariDbExecutor executor;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    mainThread = new QueueMainThread();
    executor =
        new HikariDbExecutor(
            dataSource, 2, mainThread, LoggerFactory.getLogger(HikariDbExecutorTest.class));
  }

  @AfterEach
  void tearDown() {
    executor.close();
  }

  @Test
  void supplyRunsOffTheMainThreadAndCompletesOnIt() throws Exception {
    AtomicReference<String> workThread = new AtomicReference<>();
    AtomicReference<Boolean> completedOnMain = new AtomicReference<>();

    CompletableFuture<Integer> future =
        executor.supply(
            c -> {
              workThread.set(Thread.currentThread().getName());
              return 42;
            });
    CompletableFuture<Void> chained =
        future.thenAccept(v -> completedOnMain.set(mainThread.isCurrent()));

    mainThread.awaitAndDrain();

    assertTrue(workThread.get().startsWith("Ascent-DB-"), workThread.get());
    assertEquals(42, future.join());
    chained.join();
    assertEquals(Boolean.TRUE, completedOnMain.get());
    verify(connection).close();
  }

  @Test
  void futureIsNotCompleteUntilTheMainThreadRuns() throws Exception {
    CompletableFuture<Integer> future = executor.supply(c -> 1);

    long deadline = System.currentTimeMillis() + 5000;
    while (mainThread.pending() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(1, mainThread.pending());
    assertFalse(future.isDone(), "must not complete before the main thread ticks");

    mainThread.drain();
    assertTrue(future.isDone());
  }

  @Test
  void sqlExceptionBecomesDbExceptionOnTheFuture() throws Exception {
    CompletableFuture<Integer> future =
        executor.supply(
            c -> {
              throw new SQLException("table missing");
            });
    mainThread.awaitAndDrain();

    CompletionException e = assertThrows(CompletionException.class, future::join);
    DbException cause = assertInstanceOf(DbException.class, e.getCause());
    assertInstanceOf(SQLException.class, cause.getCause());
    assertTrue(cause.getMessage().contains("table missing"));
    assertEquals(1, executor.stats().failedTasks());
    assertEquals(1, executor.stats().completedTasks());
  }

  @Test
  void transactionCommitsOnSuccess() throws Exception {
    CompletableFuture<String> future = executor.transaction(c -> "ok");
    mainThread.awaitAndDrain();

    assertEquals("ok", future.join());
    verify(connection).setAutoCommit(false);
    verify(connection).commit();
    verify(connection, never()).rollback();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void transactionRollsBackOnFailure() throws Exception {
    CompletableFuture<String> future =
        executor.transaction(
            c -> {
              throw new SQLException("boom");
            });
    mainThread.awaitAndDrain();

    assertThrows(CompletionException.class, future::join);
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).setAutoCommit(true);
  }

  @Test
  void transactionNowRollsBackOnRuntimeExceptionAndRethrowsIt() throws Exception {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                executor.transactionNow(
                    c -> {
                      throw new IllegalStateException("logic bug");
                    }));

    assertEquals("logic bug", thrown.getMessage());
    verify(connection).rollback();
    verify(connection, never()).commit();
  }

  @Test
  void supplyNowRunsInlineOnTheCallingThread() {
    String thread = executor.supplyNow(c -> Thread.currentThread().getName());

    assertEquals(Thread.currentThread().getName(), thread);
    assertEquals(0, mainThread.pending());
  }

  @Test
  void runNowWrapsSqlException() {
    DbException e =
        assertThrows(
            DbException.class,
            () ->
                executor.runNow(
                    c -> {
                      throw new SQLException("nope");
                    }));
    assertNotNull(e.getCause());
  }

  @Test
  void statsCountTasks() throws Exception {
    executor.supply(c -> 1);
    executor.supply(c -> 2);
    mainThread.awaitAndDrain();
    // Both may finish before the first drain; wait for the second continuation if not.
    while (executor.stats().completedTasks() < 2) {
      Thread.sleep(5);
    }
    mainThread.drain();

    assertEquals(2, executor.stats().completedTasks());
    assertEquals(0, executor.stats().failedTasks());
    assertEquals(0, executor.stats().poolTotal(), "no Hikari pool behind a mocked DataSource");
  }

  @Test
  void closedExecutorRejectsNewWork() {
    executor.close();

    CompletableFuture<Integer> future = executor.supply(c -> 1);

    assertTrue(future.isCompletedExceptionally());
  }
}
