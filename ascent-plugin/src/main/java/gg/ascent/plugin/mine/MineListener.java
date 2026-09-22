package gg.ascent.plugin.mine;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.message.Messages;
import gg.ascent.api.progress.ObjectiveType;
import gg.ascent.api.progress.ProgressBus;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.XpSource;
import gg.ascent.plugin.mine.MinePlots.Plot;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The rules of the mines world (PRD E4-S1): mine only your own pit, never build, never fight, never
 * wander into someone else's plot; each mined block pays rank XP and reports progress.
 */
public final class MineListener implements Listener {

  private final MineWorld mines;
  private final RankService ranks;
  private final EnchantService enchants;
  private final ProgressBus progress;
  private final ConfigService config;
  private final Messages messages;
  private final Map<UUID, Long> lastWarning = new HashMap<>();

  public MineListener(
      MineWorld mines,
      RankService ranks,
      EnchantService enchants,
      ProgressBus progress,
      ConfigService config,
      Messages messages) {
    this.mines = mines;
    this.ranks = ranks;
    this.enchants = enchants;
    this.progress = progress;
    this.config = config;
    this.messages = messages;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (!mines.isMinesWorld(block.getWorld())) {
      return;
    }
    Player player = event.getPlayer();
    Optional<Plot> mine = mines.plots().plotOf(player.getUniqueId());
    PlotGrid.Bounds bounds = mine.map(p -> mines.grid().bounds(p.index())).orElse(null);
    if (bounds == null
        || !bounds.inPit(block.getX(), block.getY(), block.getZ())
        || mines.isBuilding(mine.get().index())) {
      event.setCancelled(true);
      return;
    }
    Plot plot = mine.get();
    long xp = config.ranks().sources().mineBlockByTier().getOrDefault(plot.tier(), 0L);
    double bonus = enchants.bonusXpPercent(player.getInventory().getItemInMainHand());
    ranks.addXp(player.getUniqueId(), XpSource.MINE_BLOCK, Math.round(xp * (1.0 + bonus / 100.0)));
    progress.publish(player.getUniqueId(), ObjectiveType.MINE_BLOCKS, 1, block.getType().name());
    mines.blockMined(plot);
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    if (mines.isMinesWorld(event.getBlock().getWorld())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onSpawn(CreatureSpawnEvent event) {
    if (mines.isMinesWorld(event.getLocation().getWorld())
        && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
      event.setCancelled(true);
    }
  }

  /** Solo-safe: nothing hurts anyone here, whatever WorldGuard thinks. */
  @EventHandler(ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getEntity() instanceof Player && mines.isMinesWorld(event.getEntity().getWorld())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    Location to = event.getTo();
    Location from = event.getFrom();
    if (!mines.isMinesWorld(to.getWorld())
        || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
      return;
    }
    if (!allowed(event.getPlayer(), to)) {
      event.setCancelled(true);
      warn(event.getPlayer());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    Location to = event.getTo();
    if (to == null || !mines.isMinesWorld(to.getWorld())) {
      return;
    }
    if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
      return; // /mine and resets move players on purpose
    }
    if (!allowed(event.getPlayer(), to)) {
      event.setCancelled(true);
      warn(event.getPlayer());
    }
  }

  private boolean allowed(Player player, Location at) {
    OptionalInt index = mines.grid().indexAt(at.getBlockX(), at.getBlockZ());
    Optional<Plot> mine = mines.plots().plotOf(player.getUniqueId());
    return index.isPresent() && mine.isPresent() && mine.get().index() == index.getAsInt();
  }

  private void warn(Player player) {
    long now = System.currentTimeMillis();
    Long last = lastWarning.get(player.getUniqueId());
    if (last == null || now - last > 3000) {
      lastWarning.put(player.getUniqueId(), now);
      messages.send(player, "mine.not-yours");
    }
  }
}
