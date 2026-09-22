package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.IntRange;
import gg.ascent.api.enchant.Tier;
import java.util.Collection;
import java.util.OptionalInt;
import java.util.random.RandomGenerator;

/** The Tinkerer's exchange table (PRD E3-S7), without Bukkit. */
public final class TinkererMath {

  private TinkererMath() {}

  /** XP levels an opened book of {@code tier} pays: its Enchanter cost times the book percent. */
  public static int bookValue(EnchantsSettings settings, Tier tier) {
    int levels = settings.tier(tier).xpLevels() * settings.tinkerer().bookValuePercent() / 100;
    return Math.max(1, levels);
  }

  /**
   * XP levels a piece of gear pays: the sum of its enchants' book values times the gear percent.
   * Zero for gear without custom enchants.
   */
  public static int gearValue(EnchantsSettings settings, Collection<Tier> enchantTiers) {
    if (enchantTiers.isEmpty()) {
      return 0;
    }
    int sum = 0;
    for (Tier tier : enchantTiers) {
      sum += bookValue(settings, tier);
    }
    return Math.max(1, sum * settings.tinkerer().gearValuePercent() / 100);
  }

  /** Whether a book of {@code tier} has a chance of returning Magic Dust. */
  public static boolean dustEligible(EnchantsSettings settings, Tier tier) {
    EnchantsSettings.Tinkerer t = settings.tinkerer();
    return t.dustChancePercent() > 0 && tier.isAtLeast(t.dustMinTier());
  }

  /** Rolls the dust chance for one book; the percent of the dust to return, or empty. */
  public static OptionalInt rollDust(EnchantsSettings settings, Tier tier, RandomGenerator random) {
    if (!dustEligible(settings, tier)) {
      return OptionalInt.empty();
    }
    EnchantsSettings.Tinkerer t = settings.tinkerer();
    if (random.nextInt(100) >= t.dustChancePercent()) {
      return OptionalInt.empty();
    }
    IntRange range = t.dustPercent();
    return OptionalInt.of(range.min() + random.nextInt(range.max() - range.min() + 1));
  }

  /**
   * What the Tinkerer pays for one stack.
   *
   * @param levelsEach XP levels per item
   * @param amount items in the stack
   * @param dustTier the tier whose dust each book might add, or null when none can
   */
  public record Offer(int levelsEach, int amount, Tier dustTier) {
    public int totalLevels() {
      return levelsEach * amount;
    }
  }
}
