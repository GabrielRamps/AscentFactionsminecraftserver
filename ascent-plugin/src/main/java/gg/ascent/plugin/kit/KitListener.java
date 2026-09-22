package gg.ascent.plugin.kit;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.kit.KitService;
import gg.ascent.api.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads and forgets cooldowns with the player, and turns menu clicks into claims. */
public final class KitListener implements Listener {

  private final KitManager kits;
  private final KitCommand command;
  private final ConfigService config;
  private final Messages messages;

  public KitListener(KitManager kits, KitCommand command, ConfigService config, Messages messages) {
    this.kits = kits;
    this.command = command;
    this.config = config;
    this.messages = messages;
  }

  /**
   * After the player service has promoted the profile (LOWEST), so the cache has somewhere to go.
   */
  @EventHandler(priority = EventPriority.LOW)
  public void onJoin(PlayerJoinEvent event) {
    kits.playerJoined(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    kits.playerQuit(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof KitMenu menu)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || event.getClickedInventory() != menu.getInventory()) {
      return;
    }
    String kitId = menu.kitAt(event.getSlot());
    if (kitId == null) {
      return;
    }
    KitService.ClaimResult result = kits.claim(player.getUniqueId(), kitId);
    command.report(player, kitId, result);
    if (result == KitService.ClaimResult.OK) {
      player.closeInventory();
    } else {
      // Refresh the icons so the cooldown text stays honest.
      player.openInventory(KitMenu.build(player, kits, config, messages).getInventory());
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof KitMenu) {
      event.setCancelled(true);
    }
  }
}
