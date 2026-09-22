package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.ItemTarget;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code condition} option on an effect, evaluated for one hit: {@code SELF_BELOW_HP:30},
 * {@code TARGET_BELOW_HP:25}, {@code SELF_HOLDING:SWORD}, {@code TARGET_HOLDING:BOW}, {@code
 * ATTACKER_HOLDING:AXE}, {@code ATTACKER_PROJECTILE}. "Self" is the enchant's wearer; "target" the
 * one they hit; "attacker" the one hitting them.
 */
public final class Conditions {

  private Conditions() {}

  /**
   * @param self the wearer of the enchant
   * @param other the other side of the hit
   * @param projectile whether the hit came from a projectile
   */
  public static boolean holds(
      EnchantDefinition enchant, Fighter self, Fighter other, boolean projectile) {
    Optional<String> condition = enchant.effect().option("condition");
    if (condition.isEmpty()) {
      return true;
    }
    String[] parts = condition.get().toUpperCase(Locale.ROOT).split(":", 2);
    String kind = parts[0];
    String arg = parts.length > 1 ? parts[1] : "";
    return switch (kind) {
      case "SELF_BELOW_HP" -> self.healthFraction() * 100 < percent(arg);
      case "TARGET_BELOW_HP" -> other.healthFraction() * 100 < percent(arg);
      case "SELF_HOLDING" -> self.holds(target(arg));
      case "TARGET_HOLDING", "ATTACKER_HOLDING" -> other.holds(target(arg));
      case "ATTACKER_PROJECTILE" -> projectile;
      default -> false;
    };
  }

  private static double percent(String arg) {
    try {
      return Double.parseDouble(arg);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static ItemTarget target(String arg) {
    try {
      return ItemTarget.valueOf(arg);
    } catch (IllegalArgumentException e) {
      return ItemTarget.ALL_WEAPONS;
    }
  }
}
