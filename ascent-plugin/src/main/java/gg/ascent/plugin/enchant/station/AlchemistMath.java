package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService.MagicDust;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import java.util.Optional;

/** The Alchemist's recipes (PRD E3-S8), without Bukkit. */
public final class AlchemistMath {

  private AlchemistMath() {}

  /** Two identical books become one a level higher. */
  public record BookFusion(OpenedBook result, int costLevels) {}

  /** Two dusts of one tier become one of the next. */
  public record DustFusion(MagicDust result, int costLevels) {}

  /**
   * Same enchant, same level, level below the enchant's max. Success and destroy are the averages;
   * the cost is the tier's Enchanter cost times the input level.
   */
  public static Optional<BookFusion> fuseBooks(
      EnchantsSettings settings, OpenedBook a, OpenedBook b) {
    if (!a.enchantId().equals(b.enchantId()) || a.level() != b.level()) {
      return Optional.empty();
    }
    Optional<EnchantDefinition> def = settings.enchant(a.enchantId());
    if (def.isEmpty() || a.level() >= def.get().maxLevel()) {
      return Optional.empty();
    }
    OpenedBook result =
        new OpenedBook(
            a.enchantId(),
            a.level() + 1,
            average(a.success(), b.success()),
            average(a.destroy(), b.destroy()));
    int cost = settings.tier(def.get().tier()).xpLevels() * a.level();
    return Optional.of(new BookFusion(result, cost));
  }

  /** Same tier, not Legendary. The percent is the average; the cost is the flat dust fee. */
  public static Optional<DustFusion> fuseDust(EnchantsSettings settings, MagicDust a, MagicDust b) {
    if (a.tier() != b.tier() || a.tier() == Tier.LEGENDARY) {
      return Optional.empty();
    }
    Tier next = Tier.values()[a.tier().ordinal() + 1];
    MagicDust result = new MagicDust(next, average(a.percent(), b.percent()));
    return Optional.of(new DustFusion(result, settings.alchemist().dustCostLevels()));
  }

  /** Rounded to the nearest whole number, halves up. */
  static int average(int a, int b) {
    return (int) Math.round((a + b) / 2.0);
  }
}
