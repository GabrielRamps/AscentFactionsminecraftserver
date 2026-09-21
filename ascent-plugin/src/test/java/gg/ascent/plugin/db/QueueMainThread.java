package gg.ascent.plugin.db;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link MainThread} for tests: continuations queue up until the test drains them, on the test
 * thread, which plays the part of the server's main thread.
 */
final class QueueMainThread implements MainThread {

  private final Thread owner = Thread.currentThread();
  private final Deque<Runnable> queue = new ArrayDeque<>();

  @Override
  public synchronized void execute(Runnable task) {
    queue.add(task);
  }

  @Override
  public boolean isCurrent() {
    return Thread.currentThread() == owner;
  }

  synchronized int pending() {
    return queue.size();
  }

  /** Runs everything queued so far, in order, on the calling thread. */
  void drain() {
    while (true) {
      Runnable next;
      synchronized (this) {
        next = queue.poll();
      }
      if (next == null) {
        return;
      }
      next.run();
    }
  }

  /** Waits until at least one continuation is queued, then drains. */
  void awaitAndDrain() throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (pending() == 0) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("no continuation reached the main thread within 5s");
      }
      Thread.sleep(5);
    }
    drain();
  }
}
