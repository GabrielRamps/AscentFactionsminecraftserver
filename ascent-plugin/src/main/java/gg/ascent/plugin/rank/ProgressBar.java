package gg.ascent.plugin.rank;

/** The text progress bar {@code /rank} shows. */
public final class ProgressBar {

  private ProgressBar() {}

  /**
   * {@code width} cells, the first {@code round(fraction * width)} filled.
   *
   * @param fraction 0.0 to 1.0; values outside are clamped
   */
  public static String render(double fraction, int width, char filled, char empty) {
    double f = Math.max(0.0, Math.min(1.0, fraction));
    int done = (int) Math.round(f * width);
    StringBuilder out = new StringBuilder(width);
    for (int i = 0; i < width; i++) {
      out.append(i < done ? filled : empty);
    }
    return out.toString();
  }
}
