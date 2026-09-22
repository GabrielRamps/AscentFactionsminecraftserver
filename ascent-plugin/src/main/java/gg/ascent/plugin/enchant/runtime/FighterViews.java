package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.ItemTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Builds {@link Fighter} views from Bukkit entities. */
public final class FighterViews {

  private final EnchantService enchants;
  private final ConfigService config;
  private final Silences silences;

  public FighterViews(EnchantService enchants, ConfigService config, Silences silences) {
    this.enchants = enchants;
    this.config = config;
    this.silences = silences;
  }

  /** Every catalog enchant on one item. */
  public List<GearEnchant> gear(@Nullable ItemStack item) {
    List<GearEnchant> out = new ArrayList<>();
    if (item == null || item.getType().isAir()) {
      return out;
    }
    for (Map.Entry<String, Integer> e : enchants.getEnchants(item).entrySet()) {
      config
          .enchants()
          .enchant(e.getKey())
          .ifPresent(def -> out.add(new GearEnchant(def, e.getValue())));
    }
    return out;
  }

  /** The four worn pieces. */
  public List<GearEnchant> armor(LivingEntity entity) {
    List<GearEnchant> out = new ArrayList<>();
    EntityEquipment equipment = entity.getEquipment();
    if (equipment == null) {
      return out;
    }
    for (ItemStack piece : equipment.getArmorContents()) {
      out.addAll(gear(piece));
    }
    return out;
  }

  public Fighter of(LivingEntity entity, long nowTick) {
    EntityEquipment equipment = entity.getEquipment();
    ItemStack held = equipment == null ? null : equipment.getItemInMainHand();
    Optional<ItemTarget> holding =
        held == null ? Optional.empty() : ItemTarget.classify(held.getType().name());
    AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
    boolean silenced =
        entity instanceof Player && silences.isSilenced(entity.getUniqueId(), nowTick);
    return new Fighter(
        entity.getUniqueId(),
        entity.getHealth(),
        max == null ? 20.0 : max.getValue(),
        holding,
        gear(held),
        armor(entity),
        silenced);
  }
}
