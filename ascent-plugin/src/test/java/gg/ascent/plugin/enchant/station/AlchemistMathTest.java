package gg.ascent.plugin.enchant.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService.MagicDust;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.enchant.station.AlchemistMath.BookFusion;
import gg.ascent.plugin.enchant.station.AlchemistMath.DustFusion;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PRD E3-S8: the recipes. */
class AlchemistMathTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  private EnchantDefinition withMaxLevelAtLeast(int level) {
    return settings.enchants().values().stream()
        .filter(e -> e.maxLevel() >= level)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void twoMatchingBooksBecomeTheNextLevelWithAveragedOdds() {
    EnchantDefinition def = withMaxLevelAtLeast(3);
    OpenedBook a = new OpenedBook(def.id(), 2, 60, 20);
    OpenedBook b = new OpenedBook(def.id(), 2, 71, 31);
    BookFusion fusion = AlchemistMath.fuseBooks(settings, a, b).orElseThrow();
    assertEquals(def.id(), fusion.result().enchantId());
    assertEquals(3, fusion.result().level());
    assertEquals(66, fusion.result().success(), "65.5 rounds up");
    assertEquals(26, fusion.result().destroy());
    assertEquals(settings.tier(def.tier()).xpLevels() * 2, fusion.costLevels());
  }

  @Test
  void booksMustShareEnchantAndLevelAndSitBelowMax() {
    EnchantDefinition def = withMaxLevelAtLeast(2);
    OpenedBook a = new OpenedBook(def.id(), 1, 60, 20);
    assertTrue(AlchemistMath.fuseBooks(settings, a, new OpenedBook(def.id(), 2, 60, 20)).isEmpty());
    assertTrue(AlchemistMath.fuseBooks(settings, a, new OpenedBook("nope", 1, 60, 20)).isEmpty());
    OpenedBook max = new OpenedBook(def.id(), def.maxLevel(), 60, 20);
    assertTrue(AlchemistMath.fuseBooks(settings, max, max).isEmpty());
    assertTrue(AlchemistMath.fuseBooks(settings, a, a).isPresent());
  }

  @Test
  void twoDustsOfOneTierBecomeTheNextTier() {
    DustFusion fusion =
        AlchemistMath.fuseDust(
                settings, new MagicDust(Tier.SIMPLE, 3), new MagicDust(Tier.SIMPLE, 6))
            .orElseThrow();
    assertEquals(Tier.UNIQUE, fusion.result().tier());
    assertEquals(5, fusion.result().percent(), "4.5 rounds up");
    assertEquals(settings.alchemist().dustCostLevels(), fusion.costLevels());
  }

  @Test
  void legendaryDustAndMixedTiersDoNotCombine() {
    Optional<DustFusion> legendary =
        AlchemistMath.fuseDust(
            settings, new MagicDust(Tier.LEGENDARY, 5), new MagicDust(Tier.LEGENDARY, 5));
    assertTrue(legendary.isEmpty());
    assertTrue(
        AlchemistMath.fuseDust(
                settings, new MagicDust(Tier.ELITE, 5), new MagicDust(Tier.UNIQUE, 5))
            .isEmpty());
  }
}
