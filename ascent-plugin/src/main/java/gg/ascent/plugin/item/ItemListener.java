package gg.ascent.plugin.item;

import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemRegistry;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Records the item events Bukkit can see on its own: DROPPED, PICKED_UP and DESTROYED (burnt, blown
 * up or despawned). APPLIED, CONSUMED, TRADED, SOLD and ADMIN_GIVE are recorded by the modules that
 * perform them.
 */
public final class ItemListener implements Listener {

  private final ItemRegistry registry;

  public ItemListener(ItemRegistry registry) {
    this.registry = registry;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent event) {
    registry
        .idOf(event.getItemDrop().getItemStack())
        .ifPresent(
            id ->
                registry.record(
                    id,
                    ItemEvent.DROPPED,
                    event.getPlayer().getUniqueId(),
                    null,
                    where(
                        event.getItemDrop().getLocation(),
                        event.getItemDrop().getItemStack().getAmount())));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    Item drop = event.getItem();
    registry
        .idOf(drop.getItemStack())
        .ifPresent(
            id -> {
              UUID thrower = drop.getThrower();
              UUID counterparty =
                  thrower == null || thrower.equals(player.getUniqueId()) ? null : thrower;
              registry.record(
                  id,
                  ItemEvent.PICKED_UP,
                  player.getUniqueId(),
                  counterparty,
                  where(drop.getLocation(), drop.getItemStack().getAmount()));
            });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDespawn(ItemDespawnEvent event) {
    Item drop = event.getEntity();
    registry
        .idOf(drop.getItemStack())
        .ifPresent(
            id ->
                registry.record(
                    id,
                    ItemEvent.DESTROYED,
                    drop.getThrower(),
                    null,
                    Map.of("cause", "despawn", "count", drop.getItemStack().getAmount())));
  }

  /** A dropped item burning in lava, fire or an explosion is a dead item. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onItemDamaged(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Item drop)) {
      return;
    }
    registry
        .idOf(drop.getItemStack())
        .ifPresent(
            id ->
                registry.record(
                    id,
                    ItemEvent.DESTROYED,
                    drop.getThrower(),
                    null,
                    Map.of(
                        "cause",
                        event.getCause().name(),
                        "count",
                        drop.getItemStack().getAmount())));
  }

  private static Map<String, Object> where(Location at, int count) {
    return Map.of(
        "world",
        at.getWorld() == null ? "?" : at.getWorld().getName(),
        "x",
        at.getBlockX(),
        "y",
        at.getBlockY(),
        "z",
        at.getBlockZ(),
        "count",
        count);
  }
}
