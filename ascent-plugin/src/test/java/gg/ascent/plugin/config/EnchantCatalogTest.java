package gg.ascent.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EffectType;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.ItemTarget;
import gg.ascent.api.enchant.Tier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PRD E3-S1: the bundled catalog is complete and well-formed, and bad entries are named. */
class EnchantCatalogTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYaml.bundled("enchants.yml"));

  @Test
  void theMvpCatalogIsAllThere() {
    assertEquals(41, settings.enchants().size(), "the PRD names 41 MVP enchants");
    for (Tier tier : Tier.values()) {
      assertTrue(settings.byTier(tier).size() >= 8, tier + " has too few enchants");
    }
    EnchantDefinition lifesteal = settings.enchant("lifesteal").orElseThrow();
    assertEquals(Tier.LEGENDARY, lifesteal.tier());
    assertEquals(5, lifesteal.maxLevel());
    assertTrue(lifesteal.appliesTo(ItemTarget.SWORD));
    assertFalse(lifesteal.appliesTo(ItemTarget.BOW));
    assertEquals("Heals 1.5 hearts on hit (12% chance)", lifesteal.description(3));
    assertEquals(12.0, lifesteal.effect().chance(3));
  }

  @Test
  void everyDescriptionRendersWithoutLeftoverPlaceholders() {
    for (EnchantDefinition e : settings.enchants().values()) {
      for (int level = 1; level <= e.maxLevel(); level++) {
        String line = e.description(level);
        assertFalse(
            line.matches(".*<[a-z][a-z0-9_-]*>.*"),
            e.id() + " level " + level + " leaves a placeholder: " + line);
      }
      assertFalse(e.concreteTargets().isEmpty(), e.id());
      if (e.effect().type().needsPotion()) {
        assertTrue(e.effect().option("potion").isPresent(), e.id());
      }
    }
  }

  @Test
  void allArmorAndAllWeaponsExpand() {
    EnchantDefinition ward = settings.enchant("ward").orElseThrow();
    assertTrue(ward.appliesTo(ItemTarget.BOOTS));
    assertFalse(ward.appliesTo(ItemTarget.SWORD));
    assertEquals(4, ward.concreteTargets().size());
    EnchantDefinition reforged = settings.enchant("reforged").orElseThrow();
    assertTrue(reforged.appliesTo(ItemTarget.BOW));
    assertTrue(reforged.appliesTo(ItemTarget.PICKAXE));
    assertFalse(reforged.appliesTo(ItemTarget.HOE));
  }

  @Test
  void materialsClassifyByName() {
    assertEquals(Optional.of(ItemTarget.SWORD), ItemTarget.classify("diamond_sword"));
    assertEquals(Optional.of(ItemTarget.HELMET), ItemTarget.classify("TURTLE_HELMET"));
    assertEquals(Optional.of(ItemTarget.BOW), ItemTarget.classify("CROSSBOW"));
    assertEquals(Optional.of(ItemTarget.PICKAXE), ItemTarget.classify("NETHERITE_PICKAXE"));
    assertEquals(Optional.empty(), ItemTarget.classify("DIRT"));
    assertEquals(Optional.empty(), ItemTarget.classify("ENCHANTED_BOOK"));
  }

  @Test
  void aSingleNumberAppliesToEveryLevel() {
    EnchantDefinition execute = settings.enchant("execute").orElseThrow();
    assertEquals(100.0, execute.effect().chance(1));
    assertEquals(100.0, execute.effect().chance(4));
    assertEquals(Optional.of("TARGET_BELOW_HP:25"), execute.effect().option("condition"));
    assertEquals(EffectType.DAMAGE_MULTIPLIER, execute.effect().type());
  }

  private static String catalogWith(String entry) {
    return TestYaml.bundledText("enchants.yml")
        .replaceAll("(?s)enchants:.*", "enchants:\n" + entry);
  }

  @Test
  void aMissingRequiredParamNamesTheEnchantAndField() {
    String yaml =
        catalogWith(
            """
              broken:
                display: "Broken"
                tier: SIMPLE
                applies-to: [SWORD]
                max-level: 2
                description: "x"
                effect: { type: LIFESTEAL, per-level: { heal: [1, 2] } }
            """);
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> SettingsParsers.enchants(TestYaml.node("enchants.yml", yaml)));
    assertTrue(e.getMessage().contains("enchants.broken"), e.getMessage());
    assertTrue(e.getMessage().contains("chance"), e.getMessage());
  }

  @Test
  void aWrongLevelCountNamesTheParameter() {
    String yaml =
        catalogWith(
            """
              broken:
                display: "Broken"
                tier: SIMPLE
                applies-to: [SWORD]
                max-level: 3
                description: "x"
                effect: { type: LIFESTEAL, per-level: { heal: [1, 2], chance: 5 } }
            """);
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> SettingsParsers.enchants(TestYaml.node("enchants.yml", yaml)));
    assertTrue(e.getMessage().contains("enchants.broken.effect.per-level.heal"), e.getMessage());
    assertTrue(e.getMessage().contains("expected 3 values"), e.getMessage());
  }

  @Test
  void anUnknownTargetOrTypeIsNamed() {
    String yaml =
        catalogWith(
            """
              broken:
                display: "Broken"
                tier: SIMPLE
                applies-to: [SPOON]
                max-level: 1
                description: "x"
                effect: { type: AUTO_SMELT }
            """);
    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> SettingsParsers.enchants(TestYaml.node("enchants.yml", yaml)));
    assertTrue(e.getMessage().contains("applies-to"), e.getMessage());
    assertTrue(e.getMessage().contains("SPOON"), e.getMessage());
  }
}
