package gg.ascent.plugin.enchant.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.ItemTarget;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * PRD E3-S4: damage order, stacking cap, conditions, Silence, cooldowns. Deterministic randomness.
 */
class CombatResolverTest {

  private final EnchantsSettings settings =
      SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));

  /** Every chance roll hits (or misses) on demand. */
  private static RandomGenerator fixed(double value) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        return 0;
      }

      @Override
      public double nextDouble() {
        return value;
      }
    };
  }

  private GearEnchant g(String id, int level) {
    return new GearEnchant(settings.enchant(id).orElseThrow(), level);
  }

  private static Fighter fighter(
      double hp,
      ItemTarget holding,
      List<GearEnchant> weapon,
      List<GearEnchant> armor,
      boolean silenced) {
    return new Fighter(
        UUID.randomUUID(), hp, 20.0, Optional.ofNullable(holding), weapon, armor, silenced);
  }

  @Test
  void plainHitPassesThrough() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    Fighter a = fighter(20, ItemTarget.SWORD, List.of(), List.of(), false);
    Fighter v = fighter(20, null, List.of(), List.of(), false);

    HitOutcome out = r.resolve(a, v, 6.0, false, 0);

    assertEquals(6.0, out.damage());
    assertEquals(1.0, out.multiplier());
    assertEquals(0.0, out.reductionPercent());
    assertTrue(out.procs().isEmpty());
  }

  @Test
  void multipliersThenReductionsInThatOrder() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    // Deathbringer III: x2.0 (chance rolls 0 -> hits). Armored V on four pieces: 4 x 20% = 80%,
    // capped 60%.
    Fighter a = fighter(20, ItemTarget.SWORD, List.of(g("deathbringer", 3)), List.of(), false);
    List<GearEnchant> armor = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      armor.add(g("armored", 5));
    }
    Fighter v = fighter(20, null, List.of(), armor, false);

    HitOutcome out = r.resolve(a, v, 10.0, false, 0);

    assertEquals(2.0, out.multiplier());
    assertEquals(60.0, out.reductionPercent(), "four pieces sum to 80 but cap at 60");
    assertEquals(10.0 * 2.0 * 0.4, out.damage(), 1e-9);
  }

  @Test
  void armoredOnlyReducesSwordHits() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    Fighter axeman = fighter(20, ItemTarget.AXE, List.of(), List.of(), false);
    Fighter v = fighter(20, null, List.of(), List.of(g("armored", 5)), false);

    assertEquals(0.0, r.resolve(axeman, v, 10.0, false, 0).reductionPercent());
    Fighter swordsman = fighter(20, ItemTarget.SWORD, List.of(), List.of(), false);
    assertEquals(20.0, r.resolve(swordsman, v, 10.0, false, 0).reductionPercent());
    assertEquals(
        20.0,
        r.resolve(swordsman, v, 10.0, true, 0).reductionPercent(),
        "arrow from a sword holder still counts as holding");
  }

  @Test
  void heavyReducesProjectilesOnlyAndExecuteNeedsALowTarget() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    Fighter archer = fighter(20, ItemTarget.BOW, List.of(), List.of(), false);
    Fighter heavy = fighter(20, null, List.of(), List.of(g("heavy", 5)), false);
    assertEquals(20.0, r.resolve(archer, heavy, 10.0, true, 0).reductionPercent());
    assertEquals(0.0, r.resolve(archer, heavy, 10.0, false, 0).reductionPercent());

    Fighter executioner = fighter(20, ItemTarget.SWORD, List.of(g("execute", 4)), List.of(), false);
    Fighter healthy = fighter(20, null, List.of(), List.of(), false);
    Fighter dying = fighter(4, null, List.of(), List.of(), false);
    assertEquals(1.0, r.resolve(executioner, healthy, 10.0, false, 0).multiplier());
    assertEquals(1.6, r.resolve(executioner, dying, 10.0, false, 0).multiplier());
  }

  @Test
  void procsCarryTheirSideAndDoubleStrikeDoubles() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    Fighter a =
        fighter(
            20,
            ItemTarget.SWORD,
            List.of(g("lifesteal", 5), g("double_strike", 3), g("silence", 3), g("poison", 3)),
            List.of(),
            false);
    Fighter v = fighter(20, null, List.of(), List.of(g("frozen", 3), g("ward", 3)), false);

    HitOutcome out = r.resolve(a, v, 5.0, false, 0);

    assertTrue(out.doubleStrike());
    assertEquals(10.0, out.damage());
    assertTrue(out.procs().contains(new Proc.Heal(Proc.Side.ATTACKER, 2.5, "lifesteal")));
    assertTrue(out.procs().contains(new Proc.Silence(Proc.Side.VICTIM, 100, "silence")));
    assertTrue(out.procs().contains(new Proc.Potion(Proc.Side.VICTIM, "POISON", 2, 100, "poison")));
    assertTrue(
        out.procs().contains(new Proc.Potion(Proc.Side.ATTACKER, "SLOWNESS", 1, 80, "frozen")),
        "Frozen slows the attacker");
    assertTrue(
        out.procs().contains(new Proc.Potion(Proc.Side.VICTIM, "ABSORPTION", 2, 120, "ward")),
        "Ward buffs the wearer");
  }

  @Test
  void missedChanceMeansNoProc() {
    CombatResolver r = new CombatResolver(fixed(0.99), new Cooldowns());
    Fighter a =
        fighter(
            20,
            ItemTarget.SWORD,
            List.of(g("lifesteal", 5), g("deathbringer", 3)),
            List.of(),
            false);
    Fighter v = fighter(20, null, List.of(), List.of(), false);

    HitOutcome out = r.resolve(a, v, 5.0, false, 0);

    assertEquals(1.0, out.multiplier());
    assertTrue(out.procs().isEmpty());
  }

  @Test
  void silencedFightersDoNothing() {
    CombatResolver r = new CombatResolver(fixed(0.0), new Cooldowns());
    Fighter a = fighter(20, ItemTarget.SWORD, List.of(g("deathbringer", 3)), List.of(), true);
    Fighter v = fighter(20, null, List.of(), List.of(g("armored", 5), g("frozen", 3)), true);

    HitOutcome out = r.resolve(a, v, 10.0, false, 0);

    assertEquals(10.0, out.damage());
    assertTrue(out.procs().isEmpty());
  }

  @Test
  void cooldownsBlockRepeatProcsPerPlayer() {
    Cooldowns cooldowns = new Cooldowns();
    CombatResolver r = new CombatResolver(fixed(0.0), cooldowns);
    Fighter a = fighter(20, ItemTarget.SWORD, List.of(g("lifesteal", 5)), List.of(), false);
    Fighter v = fighter(20, null, List.of(), List.of(), false);

    assertFalse(r.resolve(a, v, 5.0, false, 0).procs().isEmpty());
    assertTrue(r.resolve(a, v, 5.0, false, 10).procs().isEmpty(), "20-tick cooldown");
    assertFalse(r.resolve(a, v, 5.0, false, 20).procs().isEmpty());

    Fighter other = fighter(20, ItemTarget.SWORD, List.of(g("lifesteal", 5)), List.of(), false);
    assertFalse(r.resolve(other, v, 5.0, false, 21).procs().isEmpty(), "cooldowns are per player");
    cooldowns.prune(1000);
    assertEquals(0, cooldowns.size());
  }

  @Test
  void passivesMergeAndSumHearts() {
    var passives =
        PassiveResolver.resolve(
            List.of(
                g("gears", 3), g("overload", 3), g("overload", 2), g("haste", 1), g("gears", 1)));
    assertEquals(2, passives.potions().get("SPEED"), "highest amplifier wins");
    assertEquals(0, passives.potions().get("HASTE"));
    assertEquals(5.0, passives.extraHearts());
  }

  @Test
  void toolEffects() {
    assertEquals("IRON_INGOT", ToolEffects.smelt("RAW_IRON"));
    assertEquals("DIAMOND", ToolEffects.smelt("DIAMOND"));
    List<GearEnchant> pick = List.of(g("reforged", 5), g("experience", 3), g("auto_smelt", 1));
    assertTrue(ToolEffects.saveDurability(pick, fixed(0.5)), "80% chance beats a 0.5 roll");
    assertFalse(ToolEffects.saveDurability(pick, fixed(0.85)));
    assertEquals(30.0, ToolEffects.bonusXpPercent(pick, fixed(0.0)));
    assertTrue(ToolEffects.has(pick, gg.ascent.api.enchant.EffectType.AUTO_SMELT));
    assertEquals(Optional.of(2.0), ToolEffects.lightning(List.of(g("lightning", 3)), fixed(0.0)));
    assertEquals(Optional.empty(), ToolEffects.lightning(List.of(g("lightning", 3)), fixed(0.5)));
  }

  @Test
  void silencesExpire() {
    Silences s = new Silences();
    UUID p = UUID.randomUUID();
    s.silence(p, 40, 100);
    assertTrue(s.isSilenced(p, 120));
    assertFalse(s.isSilenced(p, 140));
  }
}
