package gg.ascent.plugin.item;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Time-ordered UUIDs (RFC 9562 version 7): 48 bits of milliseconds since the epoch, then random
 * bits. Ids created later sort later, which keeps the {@code items} index in insertion order.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID next() {
    return next(Clock.systemUTC());
  }

  static UUID next(Clock clock) {
    long millis = clock.millis();
    long randA = RANDOM.nextLong() & 0x0FFFL;
    long msb = (millis << 16) | 0x7000L | randA;
    long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  /** The millisecond timestamp encoded in a version 7 UUID. */
  public static long millisOf(UUID uuid) {
    return uuid.getMostSignificantBits() >>> 16;
  }
}
