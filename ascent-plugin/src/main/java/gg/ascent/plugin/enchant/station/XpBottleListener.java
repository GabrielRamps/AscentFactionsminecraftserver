package gg.ascent.plugin.enchant.station;

import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click an XP bottle to gain its levels; sneak to drink the whole stack (PRD E3-S7). The
 * vanilla throw is suppressed, so the bottle is never a splash of orbs. As with books, a click
 * lands as an air, block or entity event and counts once per tick.
 */
public final class XpBottleListener implements Listener {

  private final EnchantService enchants;
  private final ItemRegistry items;
  private final Messages messages;
  private final Map<UUID, Integer> lastTick = new HashMap<>();

  public XpBottleListener(EnchantService enchants, ItemRegistry items, Messages messages) {
    this.enchants = enchants;
    this.items = items;
    this.messages = messages;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND
        || (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
      return;
    }
    if (drink(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (event.getHand() == EquipmentSlot.HAND && drink(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  /** Belt and braces: a custom bottle must never leave the hand as a projectile. */
  @EventHandler
  public void onLaunch(ProjectileLaunchEvent event) {
    if (event.getEntity() instanceof ThrownExpBottle bottle
        && bottle.getShooter() instanceof Player player
        && enchants.xpBottleLevels(player.getInventory().getItemInMainHand()).isPresent()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    lastTick.remove(event.getPlayer().getUniqueId());
  }

  private boolean drink(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    OptionalInt levels = enchants.xpBottleLevels(held);
    if (levels.isEmpty()) {
      return false;
    }
    int now = Bukkit.getCurrentTick();
    Integer last = lastTick.put(player.getUniqueId(), now);
    if (last != null && last == now) {
      return true;
    }
    int count = player.isSneaking() ? held.getAmount() : 1;
    int total = levels.getAsInt() * count;
    player.giveExpLevels(total);
    items
        .idOf(held)
        .ifPresent(
            id ->
                items.record(
                    id,
                    ItemEvent.CONSUMED,
                    player.getUniqueId(),
                    null,
                    Map.of("what", "xp_bottle", "levels", total, "count", count)));
    ItemStack rest = held.clone();
    rest.setAmount(held.getAmount() - count);
    player.getInventory().setItemInMainHand(rest.getAmount() <= 0 ? null : rest);
    messages.send(player, "xp-bottle.used", Placeholder.unparsed("levels", String.valueOf(total)));
    player.playSound(
        Sound.sound(Key.key("entity.player.levelup"), Sound.Source.PLAYER, 0.8f, 1.0f));
    return true;
  }
}
