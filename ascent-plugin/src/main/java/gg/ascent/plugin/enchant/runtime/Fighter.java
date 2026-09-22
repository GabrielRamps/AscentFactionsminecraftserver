package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.ItemTarget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the resolver needs to know about one side of a hit: no Bukkit types, so combat math is
 * testable.
 *
 * @param id the player
 * @param health current health in half-hearts
 * @param maxHealth maximum health in half-hearts
 * @param holding the kind of gear in the main hand, if any
 * @param weapon enchants on the held item
 * @param armor enchants across the four worn pieces
 * @param silenced whether a Silence proc is blocking this player's enchants right now
 */
public record Fighter(
    UUID id,
    double health,
    double maxHealth,
    Optional<ItemTarget> holding,
    List<GearEnchant> weapon,
    List<GearEnchant> armor,
    boolean silenced) {

  public Fighter {
    weapon = List.copyOf(weapon);
    armor = List.copyOf(armor);
  }

  /** 0.0 to 1.0. */
  public double healthFraction() {
    return maxHealth <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, health / maxHealth));
  }

  public boolean holds(ItemTarget kind) {
    return holding.map(kind::covers).orElse(false);
  }
}
