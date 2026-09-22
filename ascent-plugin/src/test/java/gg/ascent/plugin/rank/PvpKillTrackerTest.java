package gg.ascent.plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.config.RanksSettings.PvpKill;
import gg.ascent.plugin.testing.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PvpKillTrackerTest {

  private static final PvpKill RULE = new PvpKill(500, 10, 0.1);
  private final UUID killer = UUID.randomUUID();
  private final UUID victim = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  private final MutableClock clock = new MutableClock(Instant.EPOCH);
  private final PvpKillTracker tracker = new PvpKillTracker(clock);

  @Test
  void firstKillIsFullValueRepeatWithinWindowIsTenPercent() {
    assertEquals(500, tracker.xpFor(killer, victim, RULE));
    clock.advance(Duration.ofMinutes(3));
    assertEquals(50, tracker.xpFor(killer, victim, RULE));
  }

  @Test
  void aDifferentVictimOrTheReverseDirectionIsFullValue() {
    tracker.xpFor(killer, victim, RULE);
    assertEquals(500, tracker.xpFor(killer, other, RULE));
    assertEquals(500, tracker.xpFor(victim, killer, RULE));
  }

  @Test
  void theWindowExpiresAndOldPairsAreForgotten() {
    tracker.xpFor(killer, victim, RULE);
    clock.advance(Duration.ofMinutes(10));
    assertEquals(500, tracker.xpFor(killer, victim, RULE));
    assertEquals(1, tracker.size());
  }

  @Test
  void repeatKillsKeepTheWindowSliding() {
    tracker.xpFor(killer, victim, RULE);
    clock.advance(Duration.ofMinutes(9));
    assertEquals(50, tracker.xpFor(killer, victim, RULE));
    clock.advance(Duration.ofMinutes(9));
    assertEquals(50, tracker.xpFor(killer, victim, RULE), "the last kill restarted the window");
  }
}
