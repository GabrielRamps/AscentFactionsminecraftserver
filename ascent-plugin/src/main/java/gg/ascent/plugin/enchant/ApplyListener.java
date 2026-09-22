package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.ApplyResult;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Incompatibility;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerService;
import gg.ascent.plugin.item.LoreRenderer;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Drag-and-drop: an opened book on the cursor clicked onto gear in the player's inventory rolls it
 * (PRD E3-S3). One message and one sound per outcome.
 */
public final class ApplyListener implements Listener {

  private final EnchantService enchants;
  private final PlayerService players;
  private final Messages messages;

  public ApplyListener(EnchantService enchants, PlayerService players, Messages messages) {
    this.enchants = enchants;
    this.players = players;
    this.messages = messages;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    ItemStack cursor = event.getCursor();
    ItemStack target = event.getCurrentItem();
    Optional<OpenedBook> book = enchants.openedBook(cursor);
    if (book.isEmpty() || target == null || target.getType().isAir()) {
      return;
    }
    if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
      return;
    }
    if (event.getClickedInventory() == null
        || !(event.getClickedInventory().getHolder() instanceof Player)) {
      return; // not the player's own inventory: let the click be a click
    }
    // From here the click means "apply", never "swap the items".
    event.setCancelled(true);
    if (players.profile(player.getUniqueId()).isEmpty()) {
      return;
    }
    EnchantDefinition def = enchants.definition(book.get().enchantId()).orElse(null);
    if (def == null) {
      return;
    }
    int rank = players.require(player.getUniqueId()).rank();
    Optional<Incompatibility> why = enchants.checkCompatible(cursor, target, rank);
    var name =
        Placeholder.unparsed(
            "enchant", def.display() + " " + LoreRenderer.roman(book.get().level()));
    if (why.isPresent()) {
      String key =
          switch (why.get()) {
            case NOT_GEAR -> "enchant.apply.not-gear";
            case WRONG_ITEM -> "enchant.apply.wrong-item";
            case SLOTS_FULL -> "enchant.apply.slots-full";
            case ALREADY_HAS -> "enchant.apply.already-has";
          };
      messages.send(
          player, key, name, Placeholder.unparsed("applies", BookLore.appliesTo(def.appliesTo())));
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    ApplyResult result = enchants.apply(cursor, target, player);
    switch (result) {
      case SUCCESS -> {
        messages.send(player, "enchant.apply.success", name);
        play(player, "entity.player.levelup", 1.2f);
      }
      case DESTROYED -> {
        event.setCurrentItem(null);
        messages.send(player, "enchant.apply.destroyed", name);
        play(player, "entity.item.break", 0.7f);
        play(player, "entity.generic.explode", 1.0f);
      }
      case SCROLL_SAVED -> {
        messages.send(player, "enchant.apply.scroll-saved", name);
        play(player, "item.shield.block", 1.0f);
      }
      case FAILED -> {
        messages.send(player, "enchant.apply.failed", name);
        play(player, "block.fire.extinguish", 1.0f);
      }
      case INCOMPATIBLE -> {
        // already reported above, or another plugin cancelled it
      }
    }
    // The service consumed one book from the stack; show the cursor as it now is.
    event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
    if (result != ApplyResult.DESTROYED) {
      event.setCurrentItem(target);
    }
  }

  private static void play(Player player, String key, float pitch) {
    player.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1.0f, pitch));
  }
}
