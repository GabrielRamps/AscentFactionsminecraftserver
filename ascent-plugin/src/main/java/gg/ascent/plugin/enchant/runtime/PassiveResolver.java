package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EffectType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What a player's worn and held gear keeps on them all the time (PRD E3-S4 passive tick). */
public final class PassiveResolver {

  private PassiveResolver() {}

  /**
   * @param potions potion name to amplifier, the highest amplifier per potion winning
   * @param extraHearts summed EXTRA_HEARTS across pieces
   */
  public record Passives(Map<String, Integer> potions, double extraHearts) {
    public Passives {
      potions = Map.copyOf(potions);
    }
  }

  public static Passives resolve(List<GearEnchant> gear) {
    Map<String, Integer> potions = new LinkedHashMap<>();
    double hearts = 0;
    for (GearEnchant g : gear) {
      EffectType type = g.definition().effect().type();
      if (type == EffectType.POTION_PASSIVE) {
        String potion = g.definition().effect().option("potion").orElse(null);
        if (potion != null) {
          int amplifier =
              (int) g.definition().effect().param("amplifier", g.level()).orElse(0.0).doubleValue();
          potions.merge(potion, amplifier, Math::max);
        }
      } else if (type == EffectType.EXTRA_HEARTS) {
        hearts += g.definition().effect().param("hearts", g.level()).orElse(0.0);
      }
    }
    return new Passives(potions, hearts);
  }
}
