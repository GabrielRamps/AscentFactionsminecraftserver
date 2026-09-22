package gg.ascent.plugin.kit;

import java.time.Duration;

/** Human wording for cooldowns: {@code 23h 59m}, {@code 4m 12s}, {@code 30s}. */
public final class Durations {

  private Durations() {}

  public static String humanize(Duration d) {
    if (d.isNegative() || d.isZero()) {
      return "0s";
    }
    long seconds = d.toSeconds();
    long days = seconds / 86_400;
    long hours = (seconds % 86_400) / 3_600;
    long minutes = (seconds % 3_600) / 60;
    long secs = seconds % 60;
    if (days > 0) {
      return days + "d " + hours + "h";
    }
    if (hours > 0) {
      return hours + "h " + minutes + "m";
    }
    if (minutes > 0) {
      return minutes + "m " + secs + "s";
    }
    return secs + "s";
  }
}
