package gg.ascent.plugin.enchant.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-enchant proc cooldowns in server ticks (PRD E3-S4). */
public final class Cooldowns {

  private record Key(UUID player, String enchantId) {}

  private final Map<Key, Long> readyAt = new HashMap<>();

  /**
   * Whether {@code enchantId} may proc for {@code player} at {@code nowTick}; if so, the cooldown
   * starts. A cooldown of zero never blocks.
   */
  public boolean tryProc(UUID player, String enchantId, int cooldownTicks, long nowTick) {
    if (cooldownTicks <= 0) {
      return true;
    }
    Key key = new Key(player, enchantId);
    Long ready = readyAt.get(key);
    if (ready != null && ready > nowTick) {
      return false;
    }
    readyAt.put(key, nowTick + cooldownTicks);
    return true;
  }

  /** Forgets a player's cooldowns, on quit. */
  public void forget(UUID player) {
    readyAt.keySet().removeIf(k -> k.player().equals(player));
  }

  /** Drops expired entries so the map does not grow forever. */
  public void prune(long nowTick) {
    readyAt.values().removeIf(ready -> ready <= nowTick);
  }

  public int size() {
    return readyAt.size();
  }
}
