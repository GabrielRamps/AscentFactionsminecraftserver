package gg.ascent.plugin.mine;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.MinesSettings;
import gg.ascent.api.message.Messages;
import gg.ascent.api.mine.MineService;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.rank.UnlockService;
import gg.ascent.plugin.mine.MinePlots.Claim;
import gg.ascent.plugin.mine.MinePlots.Plot;
import gg.ascent.plugin.util.Futures;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Bukkit side of personal mines (PRD E4-S1): the void world, building and refilling plots,
 * moving players, and the periodic reset sweep. Rules live in {@link MinePlots}.
 */
public final class MineWorld implements MineService {

  private final Server server;
  private final ConfigService config;
  private final PlayerService players;
  private final UnlockService unlocks;
  private final MinePlots plots;
  private final MineBuilder builder;
  private final Messages messages;
  private final Logger log;
  private final PlotGrid grid;
  private final Set<Integer> building = new HashSet<>();
  private @Nullable World world;

  public MineWorld(
      Server server,
      ConfigService config,
      PlayerService players,
      UnlockService unlocks,
      MinePlots plots,
      MineBuilder builder,
      Messages messages,
      Logger log) {
    this.server = server;
    this.config = config;
    this.players = players;
    this.unlocks = unlocks;
    this.plots = plots;
    this.builder = builder;
    this.messages = messages;
    this.log = log;
    this.grid = new PlotGrid(config.mines());
  }

  /** Creates or loads the mines world with mobs, weather and PvP-relevant rules off. */
  public World loadWorld() {
    MinesSettings settings = config.mines();
    World existing = server.getWorld(settings.worldName());
    World w =
        existing != null
            ? existing
            : new WorldCreator(settings.worldName())
                .generator(new VoidGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .createWorld();
    if (w == null) {
      throw new IllegalStateException("could not create world " + settings.worldName());
    }
    w.setSpawnFlags(false, false);
    w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
    w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
    w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    w.setGameRule(GameRule.DO_FIRE_TICK, false);
    w.setGameRule(GameRule.MOB_GRIEFING, false);
    w.setGameRule(GameRule.KEEP_INVENTORY, true);
    w.setTime(6000);
    w.setPVP(false);
    w.setSpawnLocation(3, settings.layout().surfaceY() + 1, 3);
    this.world = w;
    log.info("Mines world '{}' ready; plots built with {}.", w.getName(), builder.name());
    return w;
  }

  public World world() {
    if (world == null) {
      throw new IllegalStateException("mines world not loaded");
    }
    return world;
  }

  public PlotGrid grid() {
    return grid;
  }

  public boolean isMinesWorld(@Nullable World w) {
    return w != null && world != null && w.getUID().equals(world.getUID());
  }

  @Override
  public Optional<PlotInfo> plotOf(UUID player) {
    return plots.plotOf(player).map(this::info);
  }

  private PlotInfo info(Plot plot) {
    return new PlotInfo(
        plot.index(), plot.owner(), plot.tier(), plot.mined(), config.mines().pitVolume());
  }

  @Override
  public void visit(UUID playerId) {
    Player player = server.getPlayer(playerId);
    if (player == null) {
      return;
    }
    int tier = unlocks.mineTier(players.require(playerId).rank());
    MinesSettings.MineTier tierSettings = config.mines().tiers().get(tier);
    if (tierSettings == null) {
      log.error("mines.yml has no tier{} but the ladder unlocks it; using tier1", tier);
      tier = 1;
      tierSettings = config.mines().tiers().get(1);
    }
    Claim claim = plots.claim(playerId, tier);
    Plot plot = claim.plot();
    PlotGrid.Bounds bounds = grid.bounds(plot.index());
    CompletableFuture<Void> ready =
        switch (claim.change()) {
          case ALLOCATED -> {
            messages.send(player, "mine.building");
            yield guarded(plot.index(), builder.build(world(), bounds, tierSettings));
          }
          case UPGRADED -> {
            messages.send(
                player, "mine.upgraded", Placeholder.unparsed("tier", String.valueOf(tier)));
            yield guarded(plot.index(), builder.fillPit(world(), bounds, tierSettings));
          }
          case UNCHANGED -> CompletableFuture.completedFuture(null);
        };
    Futures.logFailure(
        ready.thenRun(
            () -> {
              Player still = server.getPlayer(playerId);
              if (still != null) {
                still.teleport(spawn(bounds));
                messages.send(
                    still,
                    "mine.welcome",
                    Placeholder.unparsed("tier", String.valueOf(plot.tier())),
                    Placeholder.unparsed("plot", String.valueOf(plot.index())));
              }
            }),
        log,
        "building mine plot " + plot.index());
  }

  private CompletableFuture<Void> guarded(int index, CompletableFuture<Void> work) {
    building.add(index);
    return work.whenComplete((v, e) -> building.remove(index));
  }

  /** Whether the plot is mid-build; blocks are not to be broken then. */
  public boolean isBuilding(int index) {
    return building.contains(index);
  }

  @Override
  public void reset(int index) {
    plots.plot(index).ifPresent(this::resetPlot);
  }

  private void resetPlot(Plot plot) {
    if (building.contains(plot.index()) || plot.owner() == null) {
      return;
    }
    MinesSettings.MineTier tier = config.mines().tiers().get(plot.tier());
    if (tier == null) {
      return;
    }
    PlotGrid.Bounds bounds = grid.bounds(plot.index());
    Location spawn = spawn(bounds);
    for (Player p : world().getPlayers()) {
      Location at = p.getLocation();
      if (bounds.inPit(at.getBlockX(), at.getBlockY(), at.getBlockZ())
          || bounds.contains(at.getBlockX(), at.getBlockZ()) && at.getY() <= bounds.surfaceY()) {
        p.teleport(spawn);
        messages.send(p, "mine.resetting");
      }
    }
    plots.wasReset(plot);
    Futures.logFailure(
        guarded(plot.index(), builder.fillPit(world(), bounds, tier)),
        log,
        "resetting mine plot " + plot.index());
  }

  /** Refills every allocated plot whose timer ran out or that is mostly mined. Runs on a timer. */
  public void sweep() {
    for (Plot plot : plots.allocated()) {
      if (server.getPlayer(plot.owner()) != null && plots.dueForReset(plot)) {
        resetPlot(plot);
      }
    }
  }

  /** Called by the listener for every block the owner mines in their pit. */
  public void blockMined(Plot plot) {
    plots.mined(plot);
    if (plots.dueForReset(plot)) {
      resetPlot(plot);
    }
  }

  public Location spawn(PlotGrid.Bounds bounds) {
    return new Location(world(), bounds.spawnX(), bounds.spawnY(), bounds.spawnZ(), 45f, 0f);
  }

  public MinePlots plots() {
    return plots;
  }
}
