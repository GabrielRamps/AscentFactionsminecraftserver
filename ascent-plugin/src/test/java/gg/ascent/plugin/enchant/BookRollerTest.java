package gg.ascent.plugin.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.IntRange;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PRD E3-S2: the odds, proven on the bundled catalog. */
class BookRollerTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  @Test
  void levelsAreWeightedOneOverLevel() {
    Random random = new Random(7);
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < 100_000; i++) {
      seen.merge(BookRoller.rollLevel(3, random), 1, Integer::sum);
    }
    // weights 1, 1/2, 1/3 -> 54.5%, 27.3%, 18.2%
    assertTrue(Math.abs(seen.get(1) - 54_545) < 1_500, seen.toString());
    assertTrue(Math.abs(seen.get(2) - 27_273) < 1_500, seen.toString());
    assertTrue(Math.abs(seen.get(3) - 18_182) < 1_500, seen.toString());
    assertEquals(1, BookRoller.rollLevel(1, random));
  }

  @Test
  void rollsStayInsideTheTierRangesAndPool() {
    Random random = new Random(11);
    for (Tier tier : Tier.values()) {
      IntRange success = settings.tier(tier).success();
      IntRange destroy = settings.tier(tier).destroy();
      boolean sawMin = false;
      boolean sawMax = false;
      for (int i = 0; i < 5_000; i++) {
        OpenedBook book = BookRoller.roll(settings, tier, random);
        var def = settings.enchant(book.enchantId()).orElseThrow();
        assertEquals(tier, def.tier(), book.enchantId() + " is not " + tier);
        assertTrue(book.level() >= 1 && book.level() <= def.maxLevel(), book.toString());
        assertTrue(
            book.success() >= success.min() && book.success() <= success.max(), book.toString());
        assertTrue(
            book.destroy() >= destroy.min() && book.destroy() <= destroy.max(), book.toString());
        sawMin |= book.success() == success.min();
        sawMax |= book.success() == success.max();
      }
      assertTrue(sawMin && sawMax, tier + " range ends are reachable");
    }
  }

  @Test
  void everyEnchantInAPoolCanBeRolled() {
    Random random = new Random(3);
    Map<String, Integer> seen = new HashMap<>();
    for (int i = 0; i < 20_000; i++) {
      seen.merge(BookRoller.roll(settings, Tier.LEGENDARY, random).enchantId(), 1, Integer::sum);
    }
    assertEquals(settings.byTier(Tier.LEGENDARY).size(), seen.size());
  }
}
