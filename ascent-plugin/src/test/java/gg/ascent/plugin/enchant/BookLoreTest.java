package gg.ascent.plugin.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.ItemTarget;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The PRD's exact book text. */
class BookLoreTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  @Test
  void openedBookMatchesThePrdExample() {
    EnchantDefinition lifesteal = settings.enchant("lifesteal").orElseThrow();
    OpenedBook book = new OpenedBook("lifesteal", 3, 100, 45);

    assertEquals("<gold><bold>Lifesteal III</bold>", BookLore.openedName(settings, lifesteal, 3));
    assertEquals(
        List.of(
            "<green>100% Success Rate",
            "<red>45% Destroy Rate",
            "<gray>Applies to: Swords",
            "<gray>Heals 1.5 hearts on hit (12% chance)",
            "<dark_gray>Drag and drop onto item to apply."),
        BookLore.openedLore(lifesteal, book));
  }

  @Test
  void unopenedBookNamesItsTier() {
    assertEquals(
        "<gold><bold>Legendary Book</bold>", BookLore.unopenedName(settings, Tier.LEGENDARY));
    assertEquals("<white><bold>Simple Book</bold>", BookLore.unopenedName(settings, Tier.SIMPLE));
    assertEquals(2, BookLore.unopenedLore(settings, Tier.ELITE).size());
  }

  @Test
  void targetsReadAsWords() {
    assertEquals("Swords, Axes", BookLore.appliesTo(EnumSet.of(ItemTarget.SWORD, ItemTarget.AXE)));
    assertEquals("All Armor", BookLore.appliesTo(EnumSet.of(ItemTarget.ALL_ARMOR)));
    assertEquals("Boots", BookLore.appliesTo(EnumSet.of(ItemTarget.BOOTS)));
    assertEquals("Pickaxes", BookLore.appliesTo(EnumSet.of(ItemTarget.PICKAXE)));
    assertEquals("Bows", BookLore.appliesTo(EnumSet.of(ItemTarget.BOW)));
  }
}
