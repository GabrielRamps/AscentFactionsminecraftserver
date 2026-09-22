package gg.ascent.plugin.enchant.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PRD E3-S7: the exchange table. */
class TinkererMathTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  @Test
  void bundledTableMatchesThePrd() {
    assertEquals(40, settings.tinkerer().bookValuePercent());
    assertEquals(50, settings.tinkerer().gearValuePercent());
    assertEquals(10, settings.tinkerer().dustChancePercent());
    assertEquals(Tier.ELITE, settings.tinkerer().dustMinTier());
    assertEquals(1, settings.tinkerer().dustPercent().min());
    assertEquals(8, settings.tinkerer().dustPercent().max());
    assertEquals(5, settings.alchemist().dustCostLevels());
  }

  @Test
  void openedBooksPayFortyPercentOfTheirTierCost() {
    assertEquals(4, TinkererMath.bookValue(settings, Tier.SIMPLE));
    assertEquals(8, TinkererMath.bookValue(settings, Tier.UNIQUE));
    assertEquals(14, TinkererMath.bookValue(settings, Tier.ELITE));
    assertEquals(22, TinkererMath.bookValue(settings, Tier.ULTIMATE));
    assertEquals(36, TinkererMath.bookValue(settings, Tier.LEGENDARY));
  }

  @Test
  void gearPaysHalfTheSumOfItsBookValues() {
    assertEquals(0, TinkererMath.gearValue(settings, List.of()));
    assertEquals(2, TinkererMath.gearValue(settings, List.of(Tier.SIMPLE)));
    assertEquals(
        (4 + 14 + 36) / 2,
        TinkererMath.gearValue(settings, List.of(Tier.SIMPLE, Tier.ELITE, Tier.LEGENDARY)));
  }

  @Test
  void onlyElitePlusBooksCanReturnDust() {
    assertFalse(TinkererMath.dustEligible(settings, Tier.SIMPLE));
    assertFalse(TinkererMath.dustEligible(settings, Tier.UNIQUE));
    assertTrue(TinkererMath.dustEligible(settings, Tier.ELITE));
    assertTrue(TinkererMath.dustEligible(settings, Tier.LEGENDARY));
    assertEquals(OptionalInt.empty(), TinkererMath.rollDust(settings, Tier.UNIQUE, new Random(1)));
  }

  @Test
  void dustLandsAboutTenPercentOfTheTimeInsideItsRange() {
    Random random = new Random(42);
    int hits = 0;
    for (int i = 0; i < 20_000; i++) {
      OptionalInt dust = TinkererMath.rollDust(settings, Tier.ELITE, random);
      if (dust.isPresent()) {
        hits++;
        assertTrue(dust.getAsInt() >= 1 && dust.getAsInt() <= 8, "percent " + dust.getAsInt());
      }
    }
    double rate = hits / 20_000.0;
    assertTrue(rate > 0.08 && rate < 0.12, "dust rate " + rate);
  }
}
