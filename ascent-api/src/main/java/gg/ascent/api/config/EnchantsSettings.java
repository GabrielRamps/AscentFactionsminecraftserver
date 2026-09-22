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
 * @param tinkerer the Tinkerer's exchange table (E3-S7)
 * @param alchemist the Alchemist's prices (E3-S8)
 */
public record EnchantsSettings(
    Map<Tier, TierSettings> tiers,
    Map<String, EnchantDefinition> enchants,
    Tinkerer tinkerer,
    Alchemist alchemist) {

  /** Settings with the Tinkerer and Alchemist at their defaults. */
  public EnchantsSettings(Map<Tier, TierSettings> tiers, Map<String, EnchantDefinition> enchants) {
    this(tiers, enchants, Tinkerer.DEFAULT, Alchemist.DEFAULT);
  }

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

  /**
   * The Tinkerer's exchange table (PRD E3-S7).
   *
   * @param bookValuePercent an opened book pays this percent of its tier's Enchanter cost, in XP
   *     levels
   * @param gearValuePercent enchanted gear pays this percent of the sum of its enchants' book
   *     values
   * @param dustChancePercent chance per book of {@code dustMinTier} or above to also return Magic
   *     Dust of the book's tier
   * @param dustMinTier the lowest tier that can return dust
   * @param dustPercent the range the returned dust's percent is drawn from
   */
  public record Tinkerer(
      int bookValuePercent,
      int gearValuePercent,
      int dustChancePercent,
      Tier dustMinTier,
      IntRange dustPercent) {
    public static final Tinkerer DEFAULT = new Tinkerer(40, 50, 10, Tier.ELITE, new IntRange(1, 8));
  }

  /**
   * The Alchemist's prices (PRD E3-S8). Combining two books costs the tier's Enchanter cost times
   * the books' level; combining two dusts costs a flat fee.
   *
   * @param dustCostLevels XP levels to fuse two Magic Dusts
   */
  public record Alchemist(int dustCostLevels) {
    public static final Alchemist DEFAULT = new Alchemist(5);
  }
}
