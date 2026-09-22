package gg.ascent.plugin.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.zone.Zone;
import org.junit.jupiter.api.Test;

/** PRD E8-S1: circles around spawn. */
class RadiusZonesTest {

  @Test
  void insideTheSafeRadiusIsSafe() {
    assertEquals(Zone.SAFEZONE, RadiusZones.classify(0, 0, 100, 400));
    assertEquals(Zone.SAFEZONE, RadiusZones.classify(100, 0, 100, 400), "the edge counts");
    assertEquals(Zone.SAFEZONE, RadiusZones.classify(-60, 80, 100, 400), "3-4-5 triangle");
  }

  @Test
  void theRingIsWarzoneAndBeyondItIsWilderness() {
    assertEquals(Zone.WARZONE, RadiusZones.classify(101, 0, 100, 400));
    assertEquals(Zone.WARZONE, RadiusZones.classify(0, -400, 100, 400));
    assertEquals(Zone.WILDERNESS, RadiusZones.classify(300, 300, 100, 400), "424 blocks out");
  }

  @Test
  void aSquareCornerIsNotInsideTheCircle() {
    // 71,71 is 100.4 blocks out: a square zone would call it safe; a circle does not.
    assertEquals(Zone.WARZONE, RadiusZones.classify(71, 71, 100, 400));
  }
}
