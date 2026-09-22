package gg.ascent.plugin.zone;

import gg.ascent.api.message.Messages;
import gg.ascent.api.zone.Zone;
import gg.ascent.api.zone.ZoneService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Enforces the zones (PRD E8-S1): an action-bar line on every border crossing, no damage and no PvP
 * in the safezone, and no building in the safezone or the warzone without {@code
 * ascent.zones.bypass}.
 */
public final class ZoneListener implements Listener {

  public static final String BYPASS = "ascent.zones.bypass";
  private static final long WARN_INTERVAL_MS = 2_000;

  private final ZoneService zones;
  private final Messages messages;
  private final Map<UUID, Zone> lastZone = new HashMap<>();
  private final Map<UUID, Long> lastWarn = new HashMap<>();

  public ZoneListener(ZoneService zones, Messages messages) {
    this.zones = zones;
    this.messages = messages;
  }

  // --- border crossings ------------------------------------------------------------------------

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (event.hasChangedBlock()) {
      track(event.getPlayer(), zones.zoneAt(event.getTo()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    track(event.getPlayer(), zones.zoneAt(event.getTo()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    track(event.getPlayer(), zones.zoneAt(event.getPlayer().getLocation()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    lastZone.put(event.getPlayer().getUniqueId(), zones.zoneAt(event.getPlayer().getLocation()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    lastZone.remove(event.getPlayer().getUniqueId());
    lastWarn.remove(event.getPlayer().getUniqueId());
  }

  private void track(Player player, Zone now) {
    Zone before = lastZone.put(player.getUniqueId(), now);
    if (before != null && before != now) {
      player.sendActionBar(messages.render("zones.enter." + now.name().toLowerCase(Locale.ROOT)));
    }
  }

  // --- damage ----------------------------------------------------------------------------------

  /** Nothing hurts a player standing in the safezone. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player victim && zones.isSafezone(victim.getLocation())) {
      event.setCancelled(true);
    }
  }

  /** No PvP when either side stands in the safezone. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPvp(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    Player attacker = attackerOf(event.getDamager());
    if (attacker == null || attacker.equals(victim)) {
      return;
    }
    if (zones.isSafezone(victim.getLocation()) || zones.isSafezone(attacker.getLocation())) {
      event.setCancelled(true);
      warn(attacker, "zones.no-pvp");
    }
  }

  static Player attackerOf(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Player shooter) {
      return shooter;
    }
    return null;
  }

  // --- building --------------------------------------------------------------------------------

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    if (protectedFrom(event.getPlayer(), zones.zoneAt(event.getBlock().getLocation()))) {
      event.setCancelled(true);
      warn(event.getPlayer(), "zones.no-build");
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    if (protectedFrom(event.getPlayer(), zones.zoneAt(event.getBlock().getLocation()))) {
      event.setCancelled(true);
      warn(event.getPlayer(), "zones.no-build");
    }
  }

  private static boolean protectedFrom(Player player, Zone zone) {
    return zone != Zone.WILDERNESS && !player.hasPermission(BYPASS);
  }

  private void warn(Player player, String key) {
    long now = System.nanoTime() / 1_000_000; // monotonic: WSL2 wall clocks jump
    Long last = lastWarn.get(player.getUniqueId());
    if (last != null && now - last < WARN_INTERVAL_MS) {
      return;
    }
    lastWarn.put(player.getUniqueId(), now);
    messages.send(player, key);
  }
}
