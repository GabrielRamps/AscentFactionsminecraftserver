package gg.ascent.api.progress;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Where gameplay reports progress so contracts (and later quests) can count it (PRD §6.4.2).
 * Publishing is cheap and synchronous on the main thread; listeners must not block.
 */
public interface ProgressBus {

  /**
   * @param player who progressed
   * @param type what kind of objective this feeds
   * @param amount how much, at least 1
   * @param qualifier a detail some objectives filter on, such as a block or mob type; may be null
   */
  void publish(UUID player, ObjectiveType type, int amount, @Nullable String qualifier);

  void subscribe(Listener listener);

  void unsubscribe(Listener listener);

  @FunctionalInterface
  interface Listener {
    void onProgress(UUID player, ObjectiveType type, int amount, @Nullable String qualifier);
  }
}
