package gg.ascent.plugin.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.ApplyResult;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.Incompatibility;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PRD E3-S3: compatibility and the single honest roll. */
class ApplyRulesTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));
  private final EnchantDefinition lifesteal = settings.enchant("lifesteal").orElseThrow();

  @Test
  void compatibility() {
    assertEquals(Optional.empty(), ApplyRules.check(lifesteal, 1, "DIAMOND_SWORD", Map.of(), 6));
    assertEquals(
        Optional.of(Incompatibility.WRONG_ITEM),
        ApplyRules.check(lifesteal, 1, "DIAMOND_AXE", Map.of(), 6));
    assertEquals(
        Optional.of(Incompatibility.NOT_GEAR), ApplyRules.check(lifesteal, 1, "DIRT", Map.of(), 6));
    assertEquals(
        Optional.of(Incompatibility.ALREADY_HAS),
        ApplyRules.check(lifesteal, 2, "DIAMOND_SWORD", Map.of("lifesteal", 2), 6));
    assertEquals(
        Optional.of(Incompatibility.ALREADY_HAS),
        ApplyRules.check(lifesteal, 1, "DIAMOND_SWORD", Map.of("lifesteal", 3), 6));
    assertEquals(
        Optional.empty(),
        ApplyRules.check(lifesteal, 3, "DIAMOND_SWORD", Map.of("lifesteal", 2), 6),
        "a higher level upgrades");
  }

  @Test
  void slotCapCountsDistinctEnchantsAndUpgradesAreFree() {
    Map<String, Integer> full = new HashMap<>();
    for (String id :
        new String[] {"deathbringer", "double_strike", "silence", "vampire", "poison", "wither"}) {
      full.put(id, 1);
    }
    assertEquals(
        Optional.of(Incompatibility.SLOTS_FULL),
        ApplyRules.check(lifesteal, 1, "DIAMOND_SWORD", full, 6));
    assertEquals(Optional.empty(), ApplyRules.check(lifesteal, 1, "DIAMOND_SWORD", full, 7));
    EnchantDefinition poison = settings.enchant("poison").orElseThrow();
    assertEquals(
        Optional.empty(),
        ApplyRules.check(poison, 2, "DIAMOND_SWORD", full, 6),
        "upgrading poison takes no slot");
  }

  @Test
  void rollOddsMatchThePercentages() {
    Random random = new Random(5);
    int success = 0;
    int destroyed = 0;
    int failed = 0;
    for (int i = 0; i < 100_000; i++) {
      switch (ApplyRoller.roll(60, 50, false, random)) {
        case SUCCESS -> success++;
        case DESTROYED -> destroyed++;
        case FAILED -> failed++;
        default -> throw new AssertionError();
      }
    }
    // 60% succeed; of the remaining 40%, half destroy and half just fail.
    assertTrue(Math.abs(success - 60_000) < 1_000, "success " + success);
    assertTrue(Math.abs(destroyed - 20_000) < 1_000, "destroyed " + destroyed);
    assertTrue(Math.abs(failed - 20_000) < 1_000, "failed " + failed);
  }

  @Test
  void scrollTurnsDestroyIntoSaved() {
    Random random = new Random(9);
    for (int i = 0; i < 10_000; i++) {
      ApplyResult r = ApplyRoller.roll(0, 100, true, random);
      assertEquals(ApplyResult.SCROLL_SAVED, r);
    }
    assertEquals(ApplyResult.SUCCESS, ApplyRoller.roll(100, 100, false, random));
    assertEquals(ApplyResult.FAILED, ApplyRoller.roll(0, 0, false, random));
    assertTrue(ApplyResult.INCOMPATIBLE.consumedBook() == false);
  }
}
