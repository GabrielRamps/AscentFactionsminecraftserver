package gg.ascent.plugin.enchant.runtime;

/** Something the Bukkit layer must do after a hit is resolved. */
public sealed interface Proc {

  /** Who a proc lands on. */
  enum Side {
    ATTACKER,
    VICTIM
  }

  /** A potion effect. {@code potion} is the catalog's name, such as {@code SLOWNESS}. */
  record Potion(Side side, String potion, int amplifier, int durationTicks, String enchantId)
      implements Proc {}

  /** Set on fire. */
  record Fire(Side side, int durationTicks, String enchantId) implements Proc {}

  /** Heal by whole hearts. */
  record Heal(Side side, double hearts, String enchantId) implements Proc {}

  /** Scale the victim's knockback. */
  record Knockback(double multiplier, String enchantId) implements Proc {}

  /** Block the side's enchants for a while. */
  record Silence(Side side, int durationTicks, String enchantId) implements Proc {}
}
