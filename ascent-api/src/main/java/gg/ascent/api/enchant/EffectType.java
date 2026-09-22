package gg.ascent.api.enchant;

import java.util.Set;

/**
 * What an enchant does (PRD E3-S1). The catalog is data; these are the behaviours the runtime
 * (E3-S4) implements. Each type names the per-level parameters it requires.
 */
public enum EffectType {
  /**
   * Applies a potion effect when the wearer hits. Needs {@code potion}; params amplifier, duration,
   * chance.
   */
  POTION_ON_HIT(Set.of("amplifier", "duration", "chance"), true),
  /**
   * Applies a potion effect when the wearer is hit. Needs {@code potion}; params amplifier,
   * duration, chance.
   */
  POTION_ON_HURT(Set.of("amplifier", "duration", "chance"), true),
  /**
   * A potion effect kept on the wearer while the item is worn. Needs {@code potion}; param
   * amplifier.
   */
  POTION_PASSIVE(Set.of("amplifier"), true),
  /** Multiplies outgoing damage; optional {@code condition}. Params multiplier, chance. */
  DAMAGE_MULTIPLIER(Set.of("multiplier", "chance"), false),
  /**
   * Reduces incoming damage by a percentage, summed across pieces and capped; optional condition.
   */
  DAMAGE_REDUCTION_STACKABLE(Set.of("percent", "chance"), false),
  /** Heals the attacker on hit. Params heal (hearts), chance. */
  LIFESTEAL(Set.of("heal", "chance"), false),
  /** Raises max health while worn. Param hearts. */
  EXTRA_HEARTS(Set.of("hearts"), false),
  /** Heals on a kill. Param heal (hearts). */
  HEAL_ON_KILL(Set.of("heal"), false),
  /** Arrows strike lightning. Params chance, damage. */
  LIGHTNING_ON_ARROW(Set.of("chance", "damage"), false),
  /** Ores drop their smelted form. No params. */
  AUTO_SMELT(Set.of(), false),
  /** Drops go straight to the inventory. No params. */
  TELEPATHY(Set.of(), false),
  /** A chance for the item not to lose durability. Param chance. */
  NO_DURABILITY_LOSS(Set.of("chance"), false),
  /** Multiplies knockback dealt. Params multiplier, chance. */
  KNOCKBACK_MULTIPLIER(Set.of("multiplier", "chance"), false),
  /** A hit lands twice. Param chance. */
  DOUBLE_STRIKE(Set.of("chance"), false),
  /** Blocks the target's enchant procs for a while. Params chance, duration. */
  SILENCE(Set.of("chance", "duration"), false),
  /** Extra rank XP from mined blocks. Params percent, chance. Added for the Experience enchant. */
  BONUS_XP(Set.of("percent", "chance"), false);

  private final Set<String> requiredParams;
  private final boolean needsPotion;

  EffectType(Set<String> requiredParams, boolean needsPotion) {
    this.requiredParams = requiredParams;
    this.needsPotion = needsPotion;
  }

  /** Per-level numeric parameters the catalog must supply. */
  public Set<String> requiredParams() {
    return requiredParams;
  }

  /** Whether the effect needs a {@code potion} option. */
  public boolean needsPotion() {
    return needsPotion;
  }
}
