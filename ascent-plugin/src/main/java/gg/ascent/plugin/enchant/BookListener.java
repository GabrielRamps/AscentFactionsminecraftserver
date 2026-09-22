package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.event.BookRevealedEvent;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.item.LoreRenderer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click an unopened book to reveal it; sneak to reveal the whole stack (PRD E3-S2).
 *
 * <p>A right-click reaches the server as one of several events depending on what the crosshair is
 * on: air, a block, or an entity. All three reveal. Because a click on a nearby block can arrive as
 * a block event followed by an air event, one player reveals at most once per tick.
 */
public final class BookListener implements Listener {

  private final EnchantService enchants;
  private final Messages messages;
  private final Map<UUID, Integer> lastRevealTick = new HashMap<>();

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
    if (handle(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /** Right-clicking with a mob, NPC or armor stand under the crosshair. */
  @EventHandler(priority = EventPriority.HIGH)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    if (handle(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    lastRevealTick.remove(event.getPlayer().getUniqueId());
  }

  /**
   * Reveals from the main hand if it holds an unopened book.
   *
   * @return whether the click was a book click and the event should be cancelled
   */
  private boolean handle(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    Optional<Tier> tier = enchants.unopenedTier(held);
    if (tier.isEmpty()) {
      if (enchants.openedBook(held).isPresent() && firstThisTick(player)) {
        messages.send(player, "enchant.opened-hint"); // and let the click be a click
      }
      return false;
    }
    if (firstThisTick(player)) {
      reveal(player, held, tier.get());
    }
    return true;
  }

  /** False when this player already had a book click this tick: the same click, delivered twice. */
  private boolean firstThisTick(Player player) {
    int now = Bukkit.getCurrentTick();
    Integer last = lastRevealTick.put(player.getUniqueId(), now);
    return last == null || last != now;
  }

  private void reveal(Player player, ItemStack held, Tier tier) {
    int count = player.isSneaking() ? held.getAmount() : 1;
    OpenedBook last = null;
    for (int i = 0; i < count; i++) {
      ItemStack opened = enchants.reveal(held, player.getUniqueId());
      last = enchants.openedBook(opened).orElseThrow();
      player.getServer().getPluginManager().callEvent(new BookRevealedEvent(player, tier, last));
      for (ItemStack rest : player.getInventory().addItem(opened).values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), rest);
      }
    }
    // Write the smaller stack back through the live slot: the event's copy may not be it.
    ItemStack remaining = held.clone();
    remaining.setAmount(held.getAmount() - count);
    player.getInventory().setItemInMainHand(remaining.getAmount() <= 0 ? null : remaining);
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
