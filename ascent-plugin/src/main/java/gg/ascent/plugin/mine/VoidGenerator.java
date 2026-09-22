package gg.ascent.plugin.mine;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/** An empty world: no terrain, no structures, no caves. Plots are placed by {@link MineBuilder}. */
public final class VoidGenerator extends ChunkGenerator {

  @Override
  public boolean shouldGenerateNoise() {
    return false;
  }

  @Override
  public boolean shouldGenerateSurface() {
    return false;
  }

  @Override
  public boolean shouldGenerateCaves() {
    return false;
  }

  @Override
  public boolean shouldGenerateDecorations() {
    return false;
  }

  @Override
  public boolean shouldGenerateMobs() {
    return false;
  }

  @Override
  public boolean shouldGenerateStructures() {
    return false;
  }

  @Override
  public Location getFixedSpawnLocation(World world, Random random) {
    return new Location(world, 3.5, 65, 3.5);
  }
}
