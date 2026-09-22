package gg.ascent.plugin.economy;

import gg.ascent.api.config.PricesSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Prices an inventory's worth of items (PRD E4-S2). Pure, so the receipt math is testable. */
public final class SellCalculator {

  private SellCalculator() {}

  /** One receipt line. */
  public record Line(String material, int count, long unitPrice, long total) {}

  /** The whole sale. {@code multiplier} is applied to the grand total, rounded down. */
  public record Receipt(List<Line> lines, long subtotal, double multiplier, long total) {
    public boolean isEmpty() {
      return lines.isEmpty();
    }
  }

  /**
   * @param counts material name to how many the player holds
   * @param prices what each sells for; unlisted materials are skipped
   * @param multiplier the player's sell multiplier
   */
  public static Receipt price(
      Map<String, Integer> counts, PricesSettings prices, double multiplier) {
    List<Line> lines = new ArrayList<>();
    long subtotal = 0;
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      OptionalLong unit = prices.sellPrice(e.getKey());
      if (unit.isEmpty() || e.getValue() <= 0) {
        continue;
      }
      long total = unit.getAsLong() * e.getValue();
      lines.add(new Line(e.getKey(), e.getValue(), unit.getAsLong(), total));
      subtotal += total;
    }
    lines.sort((a, b) -> Long.compare(b.total(), a.total()));
    long total = (long) Math.floor(subtotal * Math.max(0.0, multiplier));
    return new Receipt(List.copyOf(lines), subtotal, multiplier, total);
  }
}
