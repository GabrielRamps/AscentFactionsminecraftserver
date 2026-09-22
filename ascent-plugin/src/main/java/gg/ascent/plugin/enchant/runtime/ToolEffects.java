package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EffectType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Tool-side effects (PRD E3-S4): Auto Smelt, Telepathy, durability, bonus XP, arrows. Pure. */
public final class ToolEffects {

  private ToolEffects() {}

  /** Raw drop to its smelted form. Anything else drops as is. */
  public static final Map<String, String> SMELT =
      Map.ofEntries(
          Map.entry("RAW_IRON", "IRON_INGOT"),
          Map.entry("RAW_GOLD", "GOLD_INGOT"),
          Map.entry("RAW_COPPER", "COPPER_INGOT"),
          Map.entry("COBBLESTONE", "STONE"),
          Map.entry("COBBLED_DEEPSLATE", "DEEPSLATE"),
          Map.entry("SAND", "GLASS"),
          Map.entry("ANCIENT_DEBRIS", "NETHERITE_SCRAP"),
          Map.entry("NETHERRACK", "NETHER_BRICK"),
          Map.entry("CLAY_BALL", "BRICK"));

  public static boolean has(List<GearEnchant> tool, EffectType type) {
    return tool.stream().anyMatch(g -> g.definition().effect().type() == type);
  }

  public static Optional<GearEnchant> first(List<GearEnchant> tool, EffectType type) {
    return tool.stream().filter(g -> g.definition().effect().type() == type).findFirst();
  }

  /** Smelted name for a drop, or the drop itself. */
  public static String smelt(String dropMaterial) {
    return SMELT.getOrDefault(dropMaterial, dropMaterial);
  }

  /** Whether a durability hit is skipped (NO_DURABILITY_LOSS). */
  public static boolean saveDurability(List<GearEnchant> tool, RandomGenerator random) {
    return first(tool, EffectType.NO_DURABILITY_LOSS)
        .map(g -> random.nextDouble() * 100.0 < g.definition().effect().chance(g.level()))
        .orElse(false);
  }

  /** Extra rank XP percent from BONUS_XP, or 0. */
  public static double bonusXpPercent(List<GearEnchant> tool, RandomGenerator random) {
    return first(tool, EffectType.BONUS_XP)
        .filter(g -> random.nextDouble() * 100.0 < g.definition().effect().chance(g.level()))
        .map(g -> g.definition().effect().param("percent", g.level()).orElse(0.0))
        .orElse(0.0);
  }

  /** Lightning damage in hearts if the arrow's bow procs LIGHTNING_ON_ARROW, else empty. */
  public static Optional<Double> lightning(List<GearEnchant> bow, RandomGenerator random) {
    return first(bow, EffectType.LIGHTNING_ON_ARROW)
        .filter(g -> random.nextDouble() * 100.0 < g.definition().effect().chance(g.level()))
        .map(g -> g.definition().effect().param("damage", g.level()).orElse(1.0));
  }

  /** Heal on kill in hearts, summed over the weapon's HEAL_ON_KILL enchants. */
  public static double healOnKill(List<GearEnchant> weapon) {
    double hearts = 0;
    for (GearEnchant g : weapon) {
      if (g.definition().effect().type() == EffectType.HEAL_ON_KILL) {
        hearts += g.definition().effect().param("heal", g.level()).orElse(0.0);
      }
    }
    return hearts;
  }
}
