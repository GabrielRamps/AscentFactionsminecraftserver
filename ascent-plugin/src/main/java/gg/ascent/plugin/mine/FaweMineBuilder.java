package gg.ascent.plugin.mine;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import gg.ascent.api.config.MinesSettings;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Builds plots with FastAsyncWorldEdit (PRD E4-S1): whole regions set at once, off the main thread.
 * A tier's schematic ({@code mines/tierN.schem} under the data folder) is pasted at the plot origin
 * when it exists; otherwise the generated layout is used, and the pit is a weighted random pattern
 * of the tier's block mix.
 *
 * <p>Only loaded when the FastAsyncWorldEdit plugin is present.
 */
public final class FaweMineBuilder implements MineBuilder {

  private final Plugin plugin;
  private final MinesSettings settings;
  private final Path dataFolder;
  private final Logger log;

  public FaweMineBuilder(Plugin plugin, MinesSettings settings, Path dataFolder, Logger log) {
    this.plugin = plugin;
    this.settings = settings;
    this.dataFolder = dataFolder;
    this.log = log;
  }

  @Override
  public String name() {
    return "FastAsyncWorldEdit";
  }

  @Override
  public CompletableFuture<Void> build(
      World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier) {
    return async(
        () -> {
          com.sk89q.worldedit.world.World we = BukkitAdapter.adapt(world);
          try (EditSession session = WorldEdit.getInstance().newEditSession(we)) {
            Path schematic = dataFolder.resolve(tier.schematic());
            if (Files.isRegularFile(schematic)) {
              paste(session, schematic, plot);
              return;
            }
            MinesSettings.Layout layout = settings.layout();
            Pattern bedrock = state("bedrock");
            Pattern glass = state("glass");
            int floorY = plot.pitMinY() - 1;
            session.setBlocks(
                cuboid(
                    we,
                    plot.minX(),
                    floorY,
                    plot.minZ(),
                    plot.maxX(),
                    plot.surfaceY(),
                    plot.maxZ()),
                bedrock);
            int top = plot.surfaceY() + layout.wallHeight();
            int y1 = plot.surfaceY() + 1;
            session.setBlocks(
                cuboid(we, plot.minX(), y1, plot.minZ(), plot.maxX(), top, plot.minZ()), glass);
            session.setBlocks(
                cuboid(we, plot.minX(), y1, plot.maxZ(), plot.maxX(), top, plot.maxZ()), glass);
            session.setBlocks(
                cuboid(we, plot.minX(), y1, plot.minZ(), plot.minX(), top, plot.maxZ()), glass);
            session.setBlocks(
                cuboid(we, plot.maxX(), y1, plot.minZ(), plot.maxX(), top, plot.maxZ()), glass);
            session.setBlocks(pitRegion(we, plot), mix(tier.composition()));
          }
        });
  }

  @Override
  public CompletableFuture<Void> fillPit(
      World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier) {
    return async(
        () -> {
          com.sk89q.worldedit.world.World we = BukkitAdapter.adapt(world);
          try (EditSession session = WorldEdit.getInstance().newEditSession(we)) {
            session.setBlocks(pitRegion(we, plot), mix(tier.composition()));
          }
        });
  }

  private void paste(EditSession session, Path file, PlotGrid.Bounds plot)
      throws IOException, WorldEditException {
    ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
    if (format == null) {
      throw new IOException("unknown schematic format: " + file);
    }
    try (ClipboardReader reader = format.getReader(new FileInputStream(file.toFile()))) {
      Clipboard clipboard = reader.read();
      Operations.complete(
          new ClipboardHolder(clipboard)
              .createPaste(session)
              .to(BlockVector3.at(plot.minX(), plot.pitMinY() - 1, plot.minZ()))
              .ignoreAirBlocks(false)
              .build());
    }
  }

  private static CuboidRegion cuboid(
      com.sk89q.worldedit.world.World world, int x1, int y1, int z1, int x2, int y2, int z2) {
    return new CuboidRegion(world, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
  }

  private static CuboidRegion pitRegion(
      com.sk89q.worldedit.world.World world, PlotGrid.Bounds plot) {
    return cuboid(
        world,
        plot.pitMinX(),
        plot.pitMinY(),
        plot.pitMinZ(),
        plot.pitMaxX(),
        plot.pitMaxY(),
        plot.pitMaxZ());
  }

  private Pattern mix(Map<String, Integer> composition) {
    RandomPattern pattern = new RandomPattern();
    for (Map.Entry<String, Integer> e : composition.entrySet()) {
      if (e.getValue() > 0) {
        pattern.add(state(e.getKey()), e.getValue());
      }
    }
    return pattern;
  }

  private Pattern state(String material) {
    String id = material.toLowerCase(Locale.ROOT);
    BlockType type = BlockTypes.get(id.contains(":") ? id : "minecraft:" + id);
    if (type == null) {
      log.warn("mines.yml names unknown block {}; using stone", material);
      type = BlockTypes.STONE;
    }
    return type.getDefaultState();
  }

  @FunctionalInterface
  private interface Edit {
    void run() throws Exception;
  }

  /** Runs the edit on an async thread and completes on the main thread. */
  private CompletableFuture<Void> async(Edit edit) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    plugin
        .getServer()
        .getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              Throwable failure = null;
              try {
                edit.run();
              } catch (Throwable t) {
                failure = t;
              }
              Throwable result = failure;
              plugin
                  .getServer()
                  .getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (result == null) {
                          done.complete(null);
                        } else {
                          done.completeExceptionally(result);
                        }
                      });
            });
    return done;
  }
}
