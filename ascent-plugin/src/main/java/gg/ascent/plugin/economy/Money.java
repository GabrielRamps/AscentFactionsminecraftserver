package gg.ascent.plugin.economy;

import gg.ascent.api.economy.EconomyService;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.OptionalLong;

/** Formatting and parsing of whole-dollar amounts as players see them. */
public final class Money {

  private Money() {}

  /** {@code 1234567} becomes {@code $1,234,567}. */
  public static String format(long amount) {
    return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
  }

  /**
   * Parses what a player typed: digits, optionally with commas, a leading {@code $}, or a {@code
   * k}/{@code m}/{@code b} suffix. Empty when it is not a valid single amount (1 to 2^53).
   */
  public static OptionalLong parse(String text) {
    String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
    if (s.isEmpty()) {
      return OptionalLong.empty();
    }
    long multiplier = 1;
    char last = s.charAt(s.length() - 1);
    if (last == 'k' || last == 'm' || last == 'b') {
      multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
      s = s.substring(0, s.length() - 1);
    }
    if (s.isEmpty() || !s.chars().allMatch(Character::isDigit) || s.length() > 18) {
      return OptionalLong.empty();
    }
    long value;
    try {
      value = Math.multiplyExact(Long.parseLong(s), multiplier);
    } catch (ArithmeticException e) {
      return OptionalLong.empty();
    }
    return EconomyService.isValidAmount(value) ? OptionalLong.of(value) : OptionalLong.empty();
  }
}
