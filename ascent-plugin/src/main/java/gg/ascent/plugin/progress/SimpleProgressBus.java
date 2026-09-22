package gg.ascent.plugin.progress;

import gg.ascent.api.progress.ObjectiveType;
import gg.ascent.api.progress.ProgressBus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** {@link ProgressBus}: a list of listeners, each shielded from the others' failures. */
public final class SimpleProgressBus implements ProgressBus {

  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final Logger log;

  public SimpleProgressBus(Logger log) {
    this.log = log;
  }

  @Override
  public void publish(UUID player, ObjectiveType type, int amount, @Nullable String qualifier) {
    if (amount < 1) {
      return;
    }
    for (Listener listener : listeners) {
      try {
        listener.onProgress(player, type, amount, qualifier);
      } catch (RuntimeException e) {
        log.error("Progress listener {} failed on {} x{}", listener, type, amount, e);
      }
    }
  }

  @Override
  public void subscribe(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void unsubscribe(Listener listener) {
    listeners.remove(listener);
  }

  public int listenerCount() {
    return listeners.size();
  }
}
