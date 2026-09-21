package gg.ascent.plugin.db;

/**
 * The seam between the database executor and the server's main thread.
 *
 * <p>In production this is the Bukkit scheduler ({@link BukkitMainThread}); tests substitute a
 * queue they drain by hand, so "completes on the main thread" is something a unit test can assert.
 */
public interface MainThread {

  /** Runs {@code task} on the main thread on a later tick. Never runs it inline. */
  void execute(Runnable task);

  /** Whether the calling thread is the main thread. */
  boolean isCurrent();
}
