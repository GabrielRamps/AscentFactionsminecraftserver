package gg.ascent.plugin.mine;

import gg.ascent.api.config.MinesSettings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Sets blocks through the Bukkit API a few thousand per tick, so the server never stalls. Used when
 * FastAsyncWorldEdit is not installed; slower than {@link FaweMineBuilder} but needs nothing.
 */
public final class BukkitMineBuilder implements MineBuilder {

  private static final int BLOCKS_PER_TICK = 4000;

  private final Plugin plugin;
  private final MinesSettings settings;
  private final Logger log;

  public BukkitMineBuilder(Plugin plugin, MinesSettings settings, Logger log) {
    this.plugin = plugin;
    this.settings = settings;
    this.log = log;
  }

  @Override
  public String name() {
    return "bukkit";
  }

  @Override
  public CompletableFuture<Void> build(
      World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier) {
    Deque<Runnable> jobs = new ArrayDeque<>();
    MinesSettings.Layout layout = settings.layout();
    int floorY = plot.pitMinY() - 1;
    // Slab: bedrock from under the pit up to the surface, whole footprint.
    box(
        jobs,
        world,
        plot.minX(),
        floorY,
        plot.minZ(),
        plot.maxX(),
        plot.surfaceY(),
        plot.maxZ(),
        Material.BEDROCK);
    // Walls: glass ring above the surface.
    int top = plot.surfaceY() + layout.wallHeight();
    box(
        jobs,
        world,
        plot.minX(),
        plot.surfaceY() + 1,
        plot.minZ(),
        plot.maxX(),
        top,
        plot.minZ(),
        Material.GLASS);
    box(
        jobs,
        world,
        plot.minX(),
        plot.surfaceY() + 1,
        plot.maxZ(),
        plot.maxX(),
        top,
        plot.maxZ(),
        Material.GLASS);
    box(
        jobs,
        world,
        plot.minX(),
        plot.surfaceY() + 1,
        plot.minZ(),
        plot.minX(),
        top,
        plot.maxZ(),
        Material.GLASS);
    box(
        jobs,
        world,
        plot.maxX(),
        plot.surfaceY() + 1,
        plot.minZ(),
        plot.maxX(),
        top,
        plot.maxZ(),
        Material.GLASS);
    pit(jobs, world, plot, tier);
    return run(jobs);
  }

  @Override
  public CompletableFuture<Void> fillPit(
      World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier) {
    Deque<Runnable> jobs = new ArrayDeque<>();
    pit(jobs, world, plot, tier);
    return run(jobs);
  }

  private void pit(
      Deque<Runnable> jobs, World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier) {
    BlockMix mix = new BlockMix(tier.composition());
    Material[] byName =
        mix.names().stream().map(BukkitMineBuilder::material).toArray(Material[]::new);
    int width = plot.pitMaxX() - plot.pitMinX() + 1;
    for (int y = plot.pitMinY(); y <= plot.pitMaxY(); y++) {
      for (int z = plot.pitMinZ(); z <= plot.pitMaxZ(); z += Math.max(1, BLOCKS_PER_TICK / width)) {
        int y0 = y;
        int z0 = z;
        int z1 = Math.min(plot.pitMaxZ(), z + Math.max(1, BLOCKS_PER_TICK / width) - 1);
        jobs.add(
            () -> {
              ThreadLocalRandom random = ThreadLocalRandom.current();
              for (int zz = z0; zz <= z1; zz++) {
                for (int x = plot.pitMinX(); x <= plot.pitMaxX(); x++) {
                  Material m = byName[mix.pickIndex(random)];
                  world.getBlockAt(x, y0, zz).setType(m, false);
                }
              }
            });
      }
    }
  }

  private static void box(
      Deque<Runnable> jobs,
      World world,
      int x1,
      int y1,
      int z1,
      int x2,
      int y2,
      int z2,
      Material m) {
    int width = x2 - x1 + 1;
    int rowsPerJob = Math.max(1, BLOCKS_PER_TICK / width);
    for (int y = y1; y <= y2; y++) {
      for (int z = z1; z <= z2; z += rowsPerJob) {
        int y0 = y;
        int za = z;
        int zb = Math.min(z2, z + rowsPerJob - 1);
        jobs.add(
            () -> {
              for (int zz = za; zz <= zb; zz++) {
                for (int x = x1; x <= x2; x++) {
                  world.getBlockAt(x, y0, zz).setType(m, false);
                }
              }
            });
      }
    }
  }

  private CompletableFuture<Void> run(Deque<Runnable> jobs) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    int total = jobs.size();
    plugin
        .getServer()
        .getScheduler()
        .runTaskTimer(
            plugin,
            task -> {
              Runnable job = jobs.poll();
              if (job == null) {
                task.cancel();
                done.complete(null);
                return;
              }
              job.run();
            },
            0L,
            1L);
    log.debug("Queued {} block jobs", total);
    return done;
  }

  static Material material(String name) {
    Material m = Material.matchMaterial(name);
    return m == null ? Material.STONE : m;
  }
}
