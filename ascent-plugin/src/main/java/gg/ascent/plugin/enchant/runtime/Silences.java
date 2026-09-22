package gg.ascent.plugin.enchant.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who is silenced until which tick (PRD E3-S4). */
public final class Silences {

  private final Map<UUID, Long> until = new HashMap<>();

  public void silence(UUID player, int durationTicks, long nowTick) {
    until.merge(player, nowTick + durationTicks, Math::max);
  }

  public boolean isSilenced(UUID player, long nowTick) {
    Long end = until.get(player);
    if (end == null) {
      return false;
    }
    if (end <= nowTick) {
      until.remove(player);
      return false;
    }
    return true;
  }

  public void forget(UUID player) {
    until.remove(player);
  }
}
