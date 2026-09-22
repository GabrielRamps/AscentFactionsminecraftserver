package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.Incompatibility;
import gg.ascent.api.enchant.ItemTarget;
import java.util.Map;
import java.util.Optional;

/** The compatibility rules of PRD E3-S3, without Bukkit. */
public final class ApplyRules {

  private ApplyRules() {}

  /**
   * @param enchant the book's enchant
   * @param level the book's level
   * @param targetMaterial the gear's material name
   * @param current enchants already on the gear
   * @param slotCap the player's slot cap
   */
  public static Optional<Incompatibility> check(
      EnchantDefinition enchant,
      int level,
      String targetMaterial,
      Map<String, Integer> current,
      int slotCap) {
    Optional<ItemTarget> target = ItemTarget.classify(targetMaterial);
    if (target.isEmpty()) {
      return Optional.of(Incompatibility.NOT_GEAR);
    }
    if (!enchant.appliesTo(target.get())) {
      return Optional.of(Incompatibility.WRONG_ITEM);
    }
    Integer have = current.get(enchant.id());
    if (have != null && have >= level) {
      return Optional.of(Incompatibility.ALREADY_HAS);
    }
    // Upgrading an enchant already on the item takes no new slot.
    if (have == null && current.size() >= slotCap) {
      return Optional.of(Incompatibility.SLOTS_FULL);
    }
    return Optional.empty();
  }
}
