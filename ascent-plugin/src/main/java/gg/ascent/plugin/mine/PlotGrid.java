package gg.ascent.plugin.mine;

import gg.ascent.api.config.MinesSettings;
import java.util.OptionalInt;

/**
 * Where plot {@code i} sits in the mines world (PRD E4-S1): a row-major grid of squares starting at
 * the origin and growing toward positive x and z, {@link #WIDTH} plots per row. Pure geometry.
 */
public final class PlotGrid {

  /** Plots per row. With 64-block plots a row is 2,048 blocks wide. */
  public static final int WIDTH = 32;

  private final MinesSettings settings;

  public PlotGrid(MinesSettings settings) {
    this.settings = settings;
  }

  /** One plot's footprint and pit, in world coordinates, all bounds inclusive. */
  public record Bounds(
      int index,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int pitMinX,
      int pitMinZ,
      int pitMaxX,
      int pitMaxZ,
      int pitMinY,
      int pitMaxY,
      int surfaceY,
      double spawnX,
      double spawnY,
      double spawnZ) {

    public boolean contains(int x, int z) {
      return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean inPit(int x, int y, int z) {
      return x >= pitMinX
          && x <= pitMaxX
          && z >= pitMinZ
          && z <= pitMaxZ
          && y >= pitMinY
          && y <= pitMaxY;
    }
  }

  public Bounds bounds(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("plot index must be >= 0, got " + index);
    }
    int size = settings.plotSize();
    int minX = (index % WIDTH) * size;
    int minZ = (index / WIDTH) * size;
    MinesSettings.Layout layout = settings.layout();
    int inset = layout.pitInset();
    int surface = layout.surfaceY();
    return new Bounds(
        index,
        minX,
        minZ,
        minX + size - 1,
        minZ + size - 1,
        minX + inset,
        minZ + inset,
        minX + size - inset - 1,
        minZ + size - inset - 1,
        surface - layout.pitDepth() + 1,
        surface,
        surface,
        minX + 3.5,
        surface + 1,
        minZ + 3.5);
  }

  /** The plot whose footprint holds {@code (x, z)}, or empty outside the grid. */
  public OptionalInt indexAt(int x, int z) {
    if (x < 0 || z < 0) {
      return OptionalInt.empty();
    }
    int size = settings.plotSize();
    int gx = x / size;
    int gz = z / size;
    if (gx >= WIDTH) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(gz * WIDTH + gx);
  }
}
