package gg.ascent.plugin.enchant.runtime;

import gg.ascent.plugin.enchant.runtime.Proc.Side;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Turns {@link Proc}s into Bukkit calls. */
public final class EffectApplier {

  private final Plugin plugin;
  private final Silences silences;
  private final Logger log;

  public EffectApplier(Plugin plugin, Silences silences, Logger log) {
    this.plugin = plugin;
    this.silences = silences;
    this.log = log;
  }

  public void apply(List<Proc> procs, LivingEntity attacker, LivingEntity victim, long nowTick) {
    for (Proc proc : procs) {
      switch (proc) {
        case Proc.Potion p ->
            potion(pick(p.side(), attacker, victim), p.potion(), p.amplifier(), p.durationTicks());
        case Proc.Fire f -> {
          LivingEntity who = pick(f.side(), attacker, victim);
          who.setFireTicks(Math.max(who.getFireTicks(), f.durationTicks()));
        }
        case Proc.Heal h -> heal(pick(h.side(), attacker, victim), h.hearts());
        case Proc.Knockback k -> knockback(victim, k.multiplier());
        case Proc.Silence s ->
            silences.silence(
                pick(s.side(), attacker, victim).getUniqueId(), s.durationTicks(), nowTick);
      }
    }
  }

  private static LivingEntity pick(Side side, LivingEntity attacker, LivingEntity victim) {
    return side == Side.ATTACKER ? attacker : victim;
  }

  /** Applies or refreshes a potion effect; unknown names are logged once and skipped. */
  public void potion(LivingEntity who, String potion, int amplifier, int durationTicks) {
    PotionEffectType type = potionType(potion);
    if (type == null) {
      return;
    }
    who.addPotionEffect(
        new PotionEffect(type, durationTicks, Math.max(0, amplifier), false, true, true));
  }

  public @Nullable PotionEffectType potionType(String potion) {
    PotionEffectType type =
        Registry.EFFECT.get(NamespacedKey.minecraft(potion.toLowerCase(Locale.ROOT)));
    if (type == null) {
      log.warn("enchants.yml names unknown potion effect {}", potion);
    }
    return type;
  }

  /** Positive hearts heal, negative hearts hurt. */
  private static void heal(LivingEntity who, double hearts) {
    double hp = hearts * 2.0;
    if (hp >= 0) {
      AttributeInstance max = who.getAttribute(Attribute.MAX_HEALTH);
      double cap = max == null ? 20.0 : max.getValue();
      who.setHealth(Math.min(cap, who.getHealth() + hp));
    } else {
      who.damage(-hp);
    }
  }

  /** Vanilla applies knockback right after the damage event; scale it on the next tick. */
  private void knockback(LivingEntity victim, double multiplier) {
    if (multiplier == 1.0) {
      return;
    }
    plugin
        .getServer()
        .getScheduler()
        .runTask(plugin, () -> victim.setVelocity(victim.getVelocity().multiply(multiplier)));
  }
}
