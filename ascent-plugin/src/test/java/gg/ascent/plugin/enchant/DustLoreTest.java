package gg.ascent.plugin.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.enchant.EnchanterCommand.EnchanterMath;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/** PRD E3-S5 and E3-S6 arithmetic and naming. */
class DustLoreTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  @Test
  void dustBoostCapsAtOneHundred() {
    assertEquals(65, DustLore.boostedSuccess(60, 5));
    assertEquals(100, DustLore.boostedSuccess(95, 15));
    assertEquals(60, DustLore.boostedSuccess(60, -3));
  }

  @Test
  void strongDustIsNamedInPurple() {
    assertTrue(DustLore.dustName(settings, Tier.ELITE, 12).startsWith("<light_purple>"));
    assertTrue(DustLore.dustName(settings, Tier.ELITE, 9).startsWith("<aqua>"));
    assertTrue(DustLore.dustName(settings, Tier.ELITE, 9).contains("+9%"));
    assertEquals("<white><bold>White Scroll</bold>", DustLore.scrollName());
  }

  @Test
  void enchanterBuysOneEightOrSixteenAtFullPrice() {
    assertEquals(1, EnchanterMath.countFor(ClickType.LEFT));
    assertEquals(8, EnchanterMath.countFor(ClickType.RIGHT));
    assertEquals(16, EnchanterMath.countFor(ClickType.SHIFT_LEFT));
    assertEquals(16, EnchanterMath.countFor(ClickType.SHIFT_RIGHT));
    assertEquals(0, EnchanterMath.countFor(ClickType.MIDDLE));
    assertEquals(90 * 16, EnchanterMath.cost(settings.tier(Tier.LEGENDARY).xpLevels(), 16));
    assertEquals(10, EnchanterMath.cost(settings.tier(Tier.SIMPLE).xpLevels(), 1));
  }
}
