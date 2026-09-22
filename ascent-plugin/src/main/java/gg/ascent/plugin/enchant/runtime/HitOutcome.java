package gg.ascent.plugin.enchant.runtime;

import java.util.List;

/**
 * The resolved hit.
 *
 * @param damage what the victim takes, after multipliers and reductions
 * @param multiplier the attacker's combined multiplier (1.0 for none)
 * @param reductionPercent the victim's summed, capped reduction
 * @param doubleStrike whether Double Strike landed (already folded into {@code damage})
 * @param procs side effects to apply, in order
 */
public record HitOutcome(
    double damage,
    double multiplier,
    double reductionPercent,
    boolean doubleStrike,
    List<Proc> procs) {

  public HitOutcome {
    procs = List.copyOf(procs);
  }
}
