package gg.ascent.plugin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void isVersion7Variant2AndCarriesTheTimestamp() {
    Instant at = Instant.parse("2026-09-22T10:00:00.123Z");
    UUID id = UuidV7.next(Clock.fixed(at, ZoneOffset.UTC));

    assertEquals(7, id.version());
    assertEquals(2, id.variant());
    assertEquals(at.toEpochMilli(), UuidV7.millisOf(id));
  }

  @Test
  void laterIdsSortLater() {
    UUID earlier = UuidV7.next(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    UUID later = UuidV7.next(Clock.fixed(Instant.parse("2026-01-01T00:00:01Z"), ZoneOffset.UTC));

    assertTrue(earlier.toString().compareTo(later.toString()) < 0);
  }

  @Test
  void consecutiveIdsDiffer() {
    assertNotEquals(UuidV7.next(), UuidV7.next());
  }
}
