package gg.ascent.api.config;

/** An inclusive integer range, as written in YAML as {@code [min, max]}. */
public record IntRange(int min, int max) {

  public IntRange {
    if (min > max) {
      throw new IllegalArgumentException("range min " + min + " exceeds max " + max);
    }
  }

  public boolean contains(int value) {
    return value >= min && value <= max;
  }
}
