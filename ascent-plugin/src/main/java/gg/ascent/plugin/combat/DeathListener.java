package gg.ascent.plugin.combat;

import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Writes a {@code death_log} row per death (PRD E9-S2): killer, victim, where, and the registry ids
 * of everything that dropped. Vanilla drops the whole inventory; the safezone never lets a player
 * die.
 */
public final class DeathListener implements Listener {

  private final DbExecutor db;
  private final DeathLogRepository repo;
  private final ItemRegistry items;
  private final Clock clock;
  private final Logger log;

  public DeathListener(
      DbExecutor db, DeathLogRepository repo, ItemRegistry items, Clock clock, Logger log) {
    this.db = db;
    this.repo = repo;
    this.items = items;
    this.clock = clock;
    this.log = log;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();
    Location where = victim.getLocation();
    List<UUID> dropped = new ArrayList<>();
    for (ItemStack drop : event.getDrops()) {
      items.idOf(drop).ifPresent(dropped::add);
    }
    UUID victimId = victim.getUniqueId();
    UUID killerId = killer == null ? null : killer.getUniqueId();
    String world = where.getWorld() == null ? "?" : where.getWorld().getName();
    int x = where.getBlockX();
    int y = where.getBlockY();
    int z = where.getBlockZ();
    Instant now = clock.instant();
    Futures.logFailure(
        db.run(c -> repo.insert(c, victimId, killerId, world, x, y, z, dropped, now)),
        log,
        "recording death");
  }
}
