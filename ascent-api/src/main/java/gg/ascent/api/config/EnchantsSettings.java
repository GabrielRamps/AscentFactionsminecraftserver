package gg.ascent.api.config;

import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.Tier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code enchants.yml}: tier definitions (PRD §6.4.4) and the enchant catalog (E3-S1).
 *
 * @param tiers one entry per tier
 * @param enchants the catalog in file order, keyed by id
 */
public record EnchantsSettings(
    Map<Tier, TierSettings> tiers, Map<String, EnchantDefinition> enchants) {

  public EnchantsSettings {
    tiers = Map.copyOf(tiers);
    enchants = Map.copyOf(new LinkedHashMap<>(enchants));
    for (Tier tier : Tier.values()) {
      if (!tiers.containsKey(tier)) {
        throw new IllegalArgumentException("missing tier " + tier);
      }
    }
  }

  public TierSettings tier(Tier tier) {
    return tiers.get(tier);
  }

  public Optional<EnchantDefinition> enchant(String id) {
    return Optional.ofNullable(enchants.get(id));
  }

  /** The enchants of one tier: the pool an unopened book of that tier reveals from. */
  public List<EnchantDefinition> byTier(Tier tier) {
    List<EnchantDefinition> out = new ArrayList<>();
    for (EnchantDefinition e : enchants.values()) {
      if (e.tier() == tier) {
        out.add(e);
      }
    }
    return out;
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
