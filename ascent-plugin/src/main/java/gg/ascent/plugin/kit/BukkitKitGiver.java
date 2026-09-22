package gg.ascent.plugin.kit;

import gg.ascent.api.config.KitsSettings;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Builds a kit's items and puts them in the inventory, dropping what does not fit. An item with
 * pre-applied enchants is registry-tagged as a KIT_ITEM and gets its enchants without a roll.
 */
public final class BukkitKitGiver implements KitGiver {

  private final Server server;
  private final ItemRegistry items;
  private final EnchantService enchants;
  private final Messages messages;
  private final Logger log;

  public BukkitKitGiver(
      Server server, ItemRegistry items, EnchantService enchants, Messages messages, Logger log) {
    this.server = server;
    this.items = items;
    this.enchants = enchants;
    this.messages = messages;
    this.log = log;
  }

  @Override
  public void give(UUID playerId, KitsSettings.Kit kit) {
    Player player = server.getPlayer(playerId);
    if (player == null) {
      return;
    }
    int dropped = 0;
    for (KitsSettings.KitItem spec : kit.items()) {
      Material material = Material.matchMaterial(spec.material());
      if (material == null || material.isAir()) {
        log.warn("kits.yml: kit {} names unknown material {}; skipped", kit.id(), spec.material());
        continue;
      }
      ItemStack stack =
          new ItemStack(material, Math.min(spec.amount(), material.getMaxStackSize()));
      if (!spec.enchants().isEmpty()) {
        Map<String, Object> data = new HashMap<>();
        data.put("kit", kit.id());
        data.put("enchants", spec.enchants());
        items.tag(stack, ItemKind.KIT_ITEM, data, playerId, "kit:" + kit.id());
        for (Map.Entry<String, Integer> e : spec.enchants().entrySet()) {
          if (!enchants.setEnchant(stack, e.getKey(), e.getValue(), playerId, "kit:" + kit.id())) {
            log.warn(
                "kits.yml: kit {} names enchant {} {} which does not fit {}",
                kit.id(),
                e.getKey(),
                e.getValue(),
                spec.material());
          }
        }
      }
      Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
      for (ItemStack rest : overflow.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), rest);
        dropped++;
      }
    }
    if (dropped > 0) {
      messages.send(player, "kit.dropped", Placeholder.unparsed("count", String.valueOf(dropped)));
    }
  }
}
