package gg.ascent.plugin.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** {@link MainThread} backed by the Bukkit scheduler. */
public final class BukkitMainThread implements MainThread {

  private final Plugin plugin;

  public BukkitMainThread(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void execute(Runnable task) {
    if (!plugin.isEnabled()) {
      // The scheduler refuses tasks from a disabled plugin. This happens only during shutdown,
      // when an in-flight query finishes after onDisable began; run the continuation where we
      // are so the future still completes and nothing waits on it forever.
      task.run();
      return;
    }
    Bukkit.getScheduler().runTask(plugin, task);
  }

  @Override
  public boolean isCurrent() {
    return Bukkit.isPrimaryThread();
  }
}
