package gg.ascent.plugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.PricesSettings;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.economy.SellCalculator.Receipt;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SellCalculatorTest {

  private final PricesSettings prices =
      SettingsParsers.prices(TestYamlAccess.bundled("prices.yml"));

  @Test
  void pricesEachLineSkipsUnsellableAndSortsByValue() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("STONE", 64);
    counts.put("DIRT", 10);
    counts.put("DIAMOND_ORE", 3);

    Receipt r = SellCalculator.price(counts, prices, 1.0);

    assertEquals(2, r.lines().size());
    assertEquals("DIAMOND_ORE", r.lines().get(0).material(), "biggest line first");
    assertEquals(360, r.lines().get(0).total());
    assertEquals(64, r.lines().get(1).total());
    assertEquals(424, r.subtotal());
    assertEquals(424, r.total());
  }

  @Test
  void multiplierAppliesToTheTotalRoundedDown() {
    Receipt r = SellCalculator.price(Map.of("STONE", 7), prices, 1.5);
    assertEquals(7, r.subtotal());
    assertEquals(10, r.total());
    assertEquals(0, SellCalculator.price(Map.of("STONE", 7), prices, -1).total());
  }

  @Test
  void nothingSellableIsEmpty() {
    assertTrue(SellCalculator.price(Map.of("DIRT", 5), prices, 1.0).isEmpty());
    assertTrue(SellCalculator.price(Map.of(), prices, 1.0).isEmpty());
    assertEquals("Diamond Ore", SellCommand.pretty("DIAMOND_ORE"));
  }
}
