package gg.ascent.plugin.enchant;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.IntRange;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The reveal roll (PRD E3-S2): a random enchant from the tier's pool, a level in {@code [1,
 * max_level]} weighted {@code 1/level}, success and destroy uniform over the tier's ranges. Pure,
 * so the odds can be proven.
 */
public final class BookRoller {

  private BookRoller() {}

  public static OpenedBook roll(EnchantsSettings settings, Tier tier, RandomGenerator random) {
    List<EnchantDefinition> pool = settings.byTier(tier);
    if (pool.isEmpty()) {
      throw new IllegalStateException("enchants.yml has no " + tier + " enchants");
    }
    EnchantDefinition enchant = pool.get(random.nextInt(pool.size()));
    int level = rollLevel(enchant.maxLevel(), random);
    EnchantsSettings.TierSettings t = settings.tier(tier);
    return new OpenedBook(
        enchant.id(), level, uniform(t.success(), random), uniform(t.destroy(), random));
  }

  /** Level {@code l} has weight {@code 1/l}: level 1 is most likely, the top level rarest. */
  static int rollLevel(int maxLevel, RandomGenerator random) {
    double total = 0;
    for (int l = 1; l <= maxLevel; l++) {
      total += 1.0 / l;
    }
    double roll = random.nextDouble() * total;
    double acc = 0;
    for (int l = 1; l <= maxLevel; l++) {
      acc += 1.0 / l;
      if (roll < acc) {
        return l;
      }
    }
    return maxLevel;
  }

  static int uniform(IntRange range, RandomGenerator random) {
    return range.min() + random.nextInt(range.max() - range.min() + 1);
  }
}
