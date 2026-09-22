package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.event.BookRevealedEvent;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.item.LoreRenderer;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-click an unopened book to reveal it; sneak to reveal the whole stack (PRD E3-S2). */
public final class BookListener implements Listener {

  private final EnchantService enchants;
  private final Messages messages;

  public BookListener(EnchantService enchants, Messages messages) {
    this.enchants = enchants;
    this.messages = messages;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND
        || (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
      return;
    }
    ItemStack held = event.getItem();
    Optional<Tier> tier = enchants.unopenedTier(held);
    if (held == null || tier.isEmpty()) {
      return;
    }
    event.setCancelled(true);
    Player player = event.getPlayer();
    int count = player.isSneaking() ? held.getAmount() : 1;
    OpenedBook last = null;
    for (int i = 0; i < count; i++) {
      ItemStack opened = enchants.reveal(held, player.getUniqueId());
      last = enchants.openedBook(opened).orElseThrow();
      player
          .getServer()
          .getPluginManager()
          .callEvent(new BookRevealedEvent(player, tier.get(), last));
      for (ItemStack rest : player.getInventory().addItem(opened).values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), rest);
      }
    }
    held.setAmount(held.getAmount() - count);
    if (count == 1 && last != null) {
      EnchantDefinition def = enchants.definition(last.enchantId()).orElseThrow();
      messages.send(
          player,
          "enchant.revealed",
          Placeholder.unparsed("enchant", def.display() + " " + LoreRenderer.roman(last.level())),
          Placeholder.unparsed("success", String.valueOf(last.success())),
          Placeholder.unparsed("destroy", String.valueOf(last.destroy())));
    } else {
      messages.send(
          player, "enchant.revealed-stack", Placeholder.unparsed("count", String.valueOf(count)));
    }
    player.playSound(
        Sound.sound(Key.key("block.enchantment_table.use"), Sound.Source.PLAYER, 1.0f, 1.2f));
  }
}
