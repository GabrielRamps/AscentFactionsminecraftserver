package gg.ascent.plugin.combat;

import gg.ascent.api.config.CombatSettings;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.message.Messages;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Gear gating and item cooldowns (PRD E9-S2): banned materials cannot be crafted or picked up and
 * are deleted when an inventory opens; god apples and ender pearls carry a cooldown the client
 * shows on the item.
 */
public final class GearListener implements Listener {

  private static final long WARN_INTERVAL_MS = 2_000;

  private final Plugin plugin;
  private final ConfigService config;
  private final Messages messages;
  private final Map<UUID, Long> lastWarn = new HashMap<>();

  public GearListener(Plugin plugin, ConfigService config, Messages messages) {
    this.plugin = plugin;
    this.config = config;
    this.messages = messages;
  }

  private CombatSettings.Gear gear() {
    return config.combat().gear();
  }

  private boolean banned(ItemStack item) {
    return item != null
        && !item.getType().isAir()
        && GearRules.isBanned(item.getType().name(), gear());
  }

  // --- crafting --------------------------------------------------------------------------------

  @EventHandler
  public void onPrepareCraft(PrepareItemCraftEvent event) {
    if (banned(event.getInventory().getResult())) {
      event.getInventory().setResult(null);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onCraft(CraftItemEvent event) {
    ItemStack result = event.getRecipe().getResult();
    if (banned(result) && event.getWhoClicked() instanceof Player player) {
      event.setCancelled(true);
      warnBanned(player, result.getType());
    }
  }

  // --- pickup and possession ---------------------------------------------------------------------

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    ItemStack stack = event.getItem().getItemStack();
    if (banned(stack)) {
      event.setCancelled(true);
      event.getItem().remove();
      warnBanned(player, stack.getType());
    }
  }

  @EventHandler
  public void onOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    int removed = purge(player.getInventory());
    if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
      removed += purge(event.getInventory());
    }
    report(player, removed);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    report(player, purge(player.getInventory()) + purge(player.getEnderChest()));
  }

  /** Deletes banned stacks; answers how many. */
  private int purge(Inventory inventory) {
    int removed = 0;
    ItemStack[] contents = inventory.getContents();
    for (int i = 0; i < contents.length; i++) {
      if (banned(contents[i])) {
        inventory.setItem(i, null);
        removed++;
      }
    }
    return removed;
  }

  private void report(Player player, int removed) {
    if (removed > 0) {
      messages.send(
          player, "combat.gear.removed", Placeholder.unparsed("count", String.valueOf(removed)));
    }
  }

  private void warnBanned(Player player, Material material) {
    long now = System.currentTimeMillis();
    Long last = lastWarn.get(player.getUniqueId());
    if (last != null && now - last < WARN_INTERVAL_MS) {
      return;
    }
    lastWarn.put(player.getUniqueId(), now);
    messages.send(player, "combat.gear.banned", Placeholder.unparsed("item", pretty(material)));
  }

  static String pretty(Material material) {
    String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder out = new StringBuilder();
    for (String word : words) {
      if (!out.isEmpty()) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return out.toString();
  }

  // --- cooldowns ---------------------------------------------------------------------------------

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEatGodApple(PlayerItemConsumeEvent event) {
    if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) {
      return;
    }
    Player player = event.getPlayer();
    if (player.hasCooldown(Material.ENCHANTED_GOLDEN_APPLE)) {
      event.setCancelled(true);
      int seconds = Math.max(1, player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) / 20);
      messages.send(
          player,
          "combat.gear.god-apple-cooldown",
          Placeholder.unparsed("seconds", String.valueOf(seconds)));
    }
  }

  /** The apple went down: start the clock. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onAteGodApple(PlayerItemConsumeEvent event) {
    if (event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) {
      int ticks = gear().godAppleCooldownSeconds() * 20;
      if (ticks > 0) {
        event.getPlayer().setCooldown(Material.ENCHANTED_GOLDEN_APPLE, ticks);
      }
    }
  }

  /** Vanilla sets its own one-second pearl cooldown after the launch; ours lands a tick later. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPearl(ProjectileLaunchEvent event) {
    if (event.getEntity() instanceof EnderPearl pearl
        && pearl.getShooter() instanceof Player player) {
      int ticks = gear().enderpearlCooldownSeconds() * 20;
      if (ticks > 0) {
        plugin
            .getServer()
            .getScheduler()
            .runTask(plugin, () -> player.setCooldown(Material.ENDER_PEARL, ticks));
      }
    }
  }
}
