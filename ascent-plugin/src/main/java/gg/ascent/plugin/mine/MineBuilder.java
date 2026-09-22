package gg.ascent.plugin.mine;

import gg.ascent.api.config.MinesSettings;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;

/**
 * Puts blocks in the mines world. Both implementations complete their futures on the main thread.
 */
public interface MineBuilder {

  /**
   * Builds the whole plot: the bedrock slab, the walls, and the pit at {@code tier}. Uses the
   * tier's schematic when its file exists, the generated layout otherwise.
   */
  CompletableFuture<Void> build(World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier);

  /** Refills only the pit from the tier's block mix. */
  CompletableFuture<Void> fillPit(World world, PlotGrid.Bounds plot, MinesSettings.MineTier tier);

  /** A short name for the log. */
  String name();
}
