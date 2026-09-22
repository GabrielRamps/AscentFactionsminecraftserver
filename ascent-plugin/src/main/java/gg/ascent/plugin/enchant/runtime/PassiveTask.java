package gg.ascent.plugin.enchant.runtime;

import gg.ascent.plugin.enchant.runtime.PassiveResolver.Passives;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * Every second: keep passive potions on players wearing them and set their extra hearts (PRD
 * E3-S4). Potions are given three seconds so a piece taken off wears out within a tick or two.
 */
public final class PassiveTask implements Runnable {

  /** The max-health modifier the runtime owns; removed when no gear grants hearts. */
  public static final NamespacedKey HEARTS = new NamespacedKey("ascent", "extra_hearts");

  private static final int POTION_TICKS = 60;

  private final Server server;
  private final FighterViews views;
  private final EffectApplier applier;
  private final SafeZones safeZones;

  public PassiveTask(
      Server server, FighterViews views, EffectApplier applier, SafeZones safeZones) {
    this.server = server;
    this.views = views;
    this.applier = applier;
    this.safeZones = safeZones;
  }

  @Override
  public void run() {
    for (Player player : server.getOnlinePlayers()) {
      List<GearEnchant> gear = new ArrayList<>(views.armor(player));
      gear.addAll(views.gear(player.getInventory().getItemInMainHand()));
      Passives passives = PassiveResolver.resolve(gear);
      boolean safe = safeZones.isSafezone(player.getLocation());
      if (!safe) {
        for (Map.Entry<String, Integer> e : passives.potions().entrySet()) {
          applier.potion(player, e.getKey(), e.getValue(), POTION_TICKS);
        }
      }
      hearts(player, safe ? 0 : passives.extraHearts());
    }
  }

  private static void hearts(Player player, double hearts) {
    AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
    if (max == null) {
      return;
    }
    AttributeModifier existing = max.getModifier(HEARTS);
    double want = hearts * 2.0;
    if (existing != null && existing.getAmount() == want) {
      return;
    }
    if (existing != null) {
      max.removeModifier(existing);
    }
    if (want > 0) {
      max.addModifier(
          new AttributeModifier(
              HEARTS, want, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }
    if (player.getHealth() > max.getValue()) {
      player.setHealth(max.getValue());
    }
  }
}
