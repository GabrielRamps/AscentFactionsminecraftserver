package gg.ascent.plugin.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.CombatSettings;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import org.junit.jupiter.api.Test;

/** PRD E9-S2: the banned list. */
class GearRulesTest {

  private final CombatSettings.Gear gear =
      SettingsParsers.combat(TestYamlAccess.bundled("combat.yml")).gear();

  @Test
  void theMetaBreakersAreBanned() {
    for (String name :
        new String[] {
          "SHIELD",
          "ELYTRA",
          "TRIDENT",
          "CROSSBOW",
          "TOTEM_OF_UNDYING",
          "END_CRYSTAL",
          "NETHERITE_SWORD",
          "NETHERITE_CHESTPLATE",
          "NETHERITE_INGOT",
          "NETHERITE_BLOCK"
        }) {
      assertTrue(GearRules.isBanned(name, gear), name);
    }
  }

  @Test
  void ordinaryGearIsNot() {
    for (String name :
        new String[] {"DIAMOND_SWORD", "BOW", "GOLDEN_APPLE", "ENDER_PEARL", "ANCIENT_DEBRIS"}) {
      assertFalse(GearRules.isBanned(name, gear), name);
    }
  }

  @Test
  void cooldownsMatchThePrd() {
    assertTrue(gear.godAppleCooldownSeconds() == 60);
    assertTrue(gear.enderpearlCooldownSeconds() == 16);
  }
}
