package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.enchant.station.TinkererMath.Offer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Reads a stack and prices it with {@link TinkererMath}. Empty for anything not accepted. */
public final class TinkererValuer {

  private final EnchantService enchants;
  private final ConfigService config;

  public TinkererValuer(EnchantService enchants, ConfigService config) {
    this.enchants = enchants;
    this.config = config;
  }

  public Optional<Offer> value(@Nullable ItemStack stack) {
    if (stack == null || stack.getType().isAir()) {
      return Optional.empty();
    }
    EnchantsSettings settings = config.enchants();
    Optional<OpenedBook> book = enchants.openedBook(stack);
    if (book.isPresent()) {
      Optional<EnchantDefinition> def = enchants.definition(book.get().enchantId());
      if (def.isEmpty()) {
        return Optional.empty();
      }
      Tier tier = def.get().tier();
      Tier dust = TinkererMath.dustEligible(settings, tier) ? tier : null;
      return Optional.of(
          new Offer(TinkererMath.bookValue(settings, tier), stack.getAmount(), dust));
    }
    Map<String, Integer> onGear = enchants.getEnchants(stack);
    if (onGear.isEmpty()) {
      return Optional.empty();
    }
    List<Tier> tiers = new ArrayList<>();
    for (String id : onGear.keySet()) {
      enchants.definition(id).ifPresent(def -> tiers.add(def.tier()));
    }
    if (tiers.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Offer(TinkererMath.gearValue(settings, tiers), stack.getAmount(), null));
  }
}
