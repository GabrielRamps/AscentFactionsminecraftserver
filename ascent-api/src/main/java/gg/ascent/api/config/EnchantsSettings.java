package gg.ascent.api.config;

import gg.ascent.api.enchant.Tier;
import java.util.Map;

/**
 * {@code enchants.yml}: tier definitions (PRD §6.4.4). The enchant catalog itself is added by E3-S1
 * and lives in the same file.
 */
public record EnchantsSettings(Map<Tier, TierSettings> tiers) {

  public EnchantsSettings {
    tiers = Map.copyOf(tiers);
    for (Tier tier : Tier.values()) {
      if (!tiers.containsKey(tier)) {
        throw new IllegalArgumentException("missing tier " + tier);
      }
    }
  }

  public TierSettings tier(Tier tier) {
    return tiers.get(tier);
  }

  /**
   * @param color MiniMessage color tag used for the tier's name and lore
   * @param xpLevels XP levels an unopened book costs at the Enchanter
   * @param success success% range a revealed book rolls from
   * @param destroy destroy% range a revealed book rolls from
   * @param minRank personal rank required to buy the tier
   */
  public record TierSettings(
      String color, int xpLevels, IntRange success, IntRange destroy, int minRank) {}
}
