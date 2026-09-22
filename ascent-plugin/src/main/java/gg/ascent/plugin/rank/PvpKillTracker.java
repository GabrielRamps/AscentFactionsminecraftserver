package gg.ascent.plugin.rank;

import gg.ascent.api.config.RanksSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The PvP kill XP rule (PRD E2-S1): full value for a kill, a fraction of it for killing the same
 * victim again within the repeat window, so farming one person does not climb the ladder.
 */
public final class PvpKillTracker {

  private record Pair(UUID killer, UUID victim) {}

  private final Clock clock;
  private final Map<Pair, Instant> lastKill = new HashMap<>();

  public PvpKillTracker(Clock clock) {
    this.clock = clock;
  }

  /** Records the kill and answers the XP it earns under {@code settings}. Main thread. */
  public long xpFor(UUID killer, UUID victim, RanksSettings.PvpKill settings) {
    Instant now = clock.instant();
    Duration window = Duration.ofMinutes(settings.repeatVictimWindowMinutes());
    prune(now, window);
    Pair pair = new Pair(killer, victim);
    Instant previous = lastKill.put(pair, now);
    boolean repeat =
        previous != null
            && !window.isZero()
            && Duration.between(previous, now).compareTo(window) < 0;
    return repeat ? Math.round(settings.base() * settings.repeatMultiplier()) : settings.base();
  }

  private void prune(Instant now, Duration window) {
    Iterator<Map.Entry<Pair, Instant>> it = lastKill.entrySet().iterator();
    while (it.hasNext()) {
      if (Duration.between(it.next().getValue(), now).compareTo(window) >= 0) {
        it.remove();
      }
    }
  }

  /** Pairs remembered right now. For tests. */
  int size() {
    return lastKill.size();
  }
}
