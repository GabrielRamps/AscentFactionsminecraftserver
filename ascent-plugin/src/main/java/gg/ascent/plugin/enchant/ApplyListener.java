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
    if (target == null || target.getType().isAir()) {
      return;
    }
    boolean scroll = enchants.isWhiteScroll(cursor);
    boolean dust = enchants.magicDust(cursor).isPresent();
    Optional<OpenedBook> book = enchants.openedBook(cursor);
    if (book.isEmpty() && !scroll && !dust) {
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
    if (scroll) {
      applyScroll(event, player, cursor, target);
      return;
    }
    if (dust) {
      applyDust(event, player, cursor, target);
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
    event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
    if (result != ApplyResult.DESTROYED) {
      event.setCurrentItem(target);
    }
  }

  /** PRD E3-S5: a White Scroll on gear. */
  private void applyScroll(
      InventoryClickEvent event, Player player, ItemStack scroll, ItemStack gear) {
    if (enchants.isProtected(gear)) {
      messages.send(player, "enchant.scroll.already");
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    if (!enchants.applyScroll(scroll, gear, player.getUniqueId())) {
      messages.send(player, "enchant.scroll.not-gear");
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    messages.send(player, "enchant.scroll.applied");
    play(player, "item.armor.equip_chain", 1.0f);
    event.getView().setCursor(scroll.getAmount() <= 0 ? null : scroll);
    event.setCurrentItem(gear);
  }

  /** PRD E3-S5: Magic Dust on an opened book. */
  private void applyDust(InventoryClickEvent event, Player player, ItemStack dust, ItemStack book) {
    Optional<OpenedBook> opened = enchants.openedBook(book);
    var d = enchants.magicDust(dust).orElseThrow();
    if (opened.isEmpty()) {
      messages.send(player, "enchant.dust.not-book");
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    EnchantDefinition def = enchants.definition(opened.get().enchantId()).orElse(null);
    if (def == null || def.tier() != d.tier()) {
      messages.send(
          player,
          "enchant.dust.wrong-tier",
          Placeholder.unparsed("tier", BookLore.tierName(d.tier())));
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    if (opened.get().success() >= 100) {
      messages.send(player, "enchant.dust.already-max");
      play(player, "block.note_block.bass", 0.8f);
      return;
    }
    enchants.applyDust(dust, book, player.getUniqueId());
    int after = enchants.openedBook(book).map(OpenedBook::success).orElse(100);
    messages.send(
        player,
        "enchant.dust.applied",
        Placeholder.unparsed("percent", String.valueOf(d.percent())),
        Placeholder.unparsed("success", String.valueOf(after)));
    play(player, "block.amethyst_block.chime", 1.2f);
    event.getView().setCursor(dust.getAmount() <= 0 ? null : dust);
    event.setCurrentItem(book);
  }

  private static void play(Player player, String key, float pitch) {
    player.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1.0f, pitch));
  }
}
