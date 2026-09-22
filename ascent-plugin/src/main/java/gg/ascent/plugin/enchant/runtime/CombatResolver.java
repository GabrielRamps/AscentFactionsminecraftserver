package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EffectType;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.plugin.enchant.runtime.Proc.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Resolves one hit between two fighters (PRD E3-S4), without Bukkit.
 *
 * <p>Damage order: base 1.8 damage (from OldCombatMechanics) → attacker multipliers → victim
 * reductions → lifesteal and heals. Stackable reductions sum across worn pieces and are capped at
 * {@link #REDUCTION_CAP}. A silenced fighter's enchants do nothing. Procs respect each enchant's
 * cooldown for the player wearing it.
 */
public final class CombatResolver {

  /** The most damage reduction armor can add up to, in percent. */
  public static final double REDUCTION_CAP = 60.0;

  private final RandomGenerator random;
  private final Cooldowns cooldowns;

  public CombatResolver(RandomGenerator random, Cooldowns cooldowns) {
    this.random = random;
    this.cooldowns = cooldowns;
  }

  public HitOutcome resolve(
      Fighter attacker, Fighter victim, double baseDamage, boolean projectile, long nowTick) {
    List<Proc> procs = new ArrayList<>();
    double multiplier = 1.0;
    boolean doubleStrike = false;

    if (!attacker.silenced()) {
      List<GearEnchant> offense = new ArrayList<>(attacker.weapon());
      offense.addAll(attacker.armor());
      for (GearEnchant g : offense) {
        EnchantDefinition def = g.definition();
        EffectType type = def.effect().type();
        if (!isOffensive(type) || !Conditions.holds(def, attacker, victim, projectile)) {
          continue;
        }
        if (!procs(attacker, g, nowTick)) {
          continue;
        }
        switch (type) {
          case DAMAGE_MULTIPLIER -> multiplier *= param(g, "multiplier", 1.0);
          case DOUBLE_STRIKE -> doubleStrike = true;
          case LIFESTEAL ->
              procs.add(new Proc.Heal(Side.ATTACKER, param(g, "heal", 0.0), def.id()));
          case KNOCKBACK_MULTIPLIER ->
              procs.add(new Proc.Knockback(param(g, "multiplier", 1.0), def.id()));
          case SILENCE ->
              procs.add(new Proc.Silence(Side.VICTIM, seconds(g, "duration"), def.id()));
          case POTION_ON_HIT -> procs.add(potion(g, Side.VICTIM, Side.ATTACKER));
          default -> {
            // not a hit effect
          }
        }
      }
    }

    double reduction = 0.0;
    if (!victim.silenced()) {
      List<GearEnchant> defense = new ArrayList<>(victim.armor());
      defense.addAll(victim.weapon());
      for (GearEnchant g : defense) {
        EnchantDefinition def = g.definition();
        EffectType type = def.effect().type();
        if (!isDefensive(type) || !Conditions.holds(def, victim, attacker, projectile)) {
          continue;
        }
        if (!procs(victim, g, nowTick)) {
          continue;
        }
        switch (type) {
          case DAMAGE_REDUCTION_STACKABLE -> reduction += param(g, "percent", 0.0);
          case POTION_ON_HURT -> procs.add(potion(g, Side.ATTACKER, Side.VICTIM));
          default -> {
            // not a hurt effect
          }
        }
      }
    }
    reduction = Math.min(REDUCTION_CAP, Math.max(0.0, reduction));

    double damage = baseDamage * multiplier;
    if (doubleStrike) {
      damage *= 2;
    }
    damage *= 1.0 - reduction / 100.0;
    return new HitOutcome(Math.max(0.0, damage), multiplier, reduction, doubleStrike, procs);
  }

  /** Rolls the enchant's chance and, if it hits, claims its cooldown. */
  private boolean procs(Fighter wearer, GearEnchant g, long nowTick) {
    double chance = g.definition().effect().chance(g.level());
    if (chance < 100.0 && random.nextDouble() * 100.0 >= chance) {
      return false;
    }
    return cooldowns.tryProc(wearer.id(), g.id(), g.definition().cooldownTicks(), nowTick);
  }

  /**
   * A potion proc. The {@code target} option decides who gets it: TARGET/ATTACKER means the other
   * side of the hit, SELF the wearer. {@code FIRE} is a special potion meaning "set on fire".
   */
  private static Proc potion(GearEnchant g, Side other, Side self) {
    EnchantDefinition def = g.definition();
    String potion = def.effect().option("potion").orElse("SLOWNESS");
    String target = def.effect().option("target").orElse("TARGET");
    Side side = target.equals("SELF") ? self : other;
    int duration = seconds(g, "duration");
    if (potion.equals("FIRE")) {
      return new Proc.Fire(side, duration, def.id());
    }
    if (potion.equals("INSTANT_HEALTH") && def.effect().param("heal", g.level()).isPresent()) {
      return new Proc.Heal(side, param(g, "heal", 0.0), def.id());
    }
    if (potion.equals("INSTANT_DAMAGE") && def.effect().param("damage", g.level()).isPresent()) {
      return new Proc.Heal(side, -param(g, "damage", 0.0), def.id());
    }
    return new Proc.Potion(side, potion, (int) param(g, "amplifier", 0.0), duration, def.id());
  }

  private static double param(GearEnchant g, String key, double fallback) {
    return g.definition().effect().param(key, g.level()).orElse(fallback);
  }

  private static int seconds(GearEnchant g, String key) {
    return (int) Math.round(param(g, key, 1.0) * 20);
  }

  static boolean isOffensive(EffectType type) {
    return switch (type) {
      case DAMAGE_MULTIPLIER,
          DOUBLE_STRIKE,
          LIFESTEAL,
          KNOCKBACK_MULTIPLIER,
          SILENCE,
          POTION_ON_HIT ->
          true;
      default -> false;
    };
  }

  static boolean isDefensive(EffectType type) {
    return type == EffectType.DAMAGE_REDUCTION_STACKABLE || type == EffectType.POTION_ON_HURT;
  }
}
