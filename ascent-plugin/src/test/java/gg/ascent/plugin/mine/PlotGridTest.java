package gg.ascent.plugin.mine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.MinesSettings;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PlotGridTest {

  private final MinesSettings settings = SettingsParsers.mines(TestYamlAccess.bundled("mines.yml"));
  private final PlotGrid grid = new PlotGrid(settings);

  @Test
  void plotsTileTheGridRowMajor() {
    PlotGrid.Bounds first = grid.bounds(0);
    assertEquals(0, first.minX());
    assertEquals(0, first.minZ());
    assertEquals(63, first.maxX());
    assertEquals(63, first.maxZ());

    PlotGrid.Bounds second = grid.bounds(1);
    assertEquals(64, second.minX());
    assertEquals(0, second.minZ());

    PlotGrid.Bounds nextRow = grid.bounds(PlotGrid.WIDTH);
    assertEquals(0, nextRow.minX());
    assertEquals(64, nextRow.minZ());
  }

  @Test
  void pitSitsInsideTheInsetAndSpansTheDepth() {
    PlotGrid.Bounds b = grid.bounds(0);
    assertEquals(8, b.pitMinX());
    assertEquals(55, b.pitMaxX());
    assertEquals(64, b.pitMaxY());
    assertEquals(41, b.pitMinY());
    assertTrue(b.inPit(8, 64, 8));
    assertTrue(b.inPit(55, 41, 55));
    assertFalse(b.inPit(7, 64, 8), "the rim is bedrock");
    assertFalse(b.inPit(8, 40, 8), "below the pit is bedrock");
    assertFalse(b.inPit(8, 65, 8), "above the surface is air");
    assertEquals(48 * 48 * 24, settings.pitVolume());
  }

  @Test
  void spawnIsOnTheRimInsideTheWalls() {
    PlotGrid.Bounds b = grid.bounds(5);
    assertTrue(b.contains((int) b.spawnX(), (int) b.spawnZ()));
    assertFalse(b.inPit((int) b.spawnX(), (int) b.spawnY() - 1, (int) b.spawnZ()));
    assertEquals(65, b.spawnY());
  }

  @Test
  void indexAtInvertsBounds() {
    for (int index : new int[] {0, 1, 31, 32, 33, 1000}) {
      PlotGrid.Bounds b = grid.bounds(index);
      assertEquals(OptionalInt.of(index), grid.indexAt(b.minX(), b.minZ()));
      assertEquals(OptionalInt.of(index), grid.indexAt(b.maxX(), b.maxZ()));
      assertEquals(OptionalInt.of(index), grid.indexAt((int) b.spawnX(), (int) b.spawnZ()));
    }
    assertTrue(grid.indexAt(-1, 0).isEmpty());
    assertTrue(grid.indexAt(0, -1).isEmpty());
    assertTrue(grid.indexAt(PlotGrid.WIDTH * 64, 0).isEmpty());
  }

  @Test
  void blockMixHonoursWeights() {
    BlockMix mix = new BlockMix(java.util.Map.of("STONE", 70, "COAL_ORE", 25, "IRON_ORE", 5));
    java.util.Map<String, Integer> seen = new java.util.HashMap<>();
    java.util.Random random = new java.util.Random(42);
    for (int i = 0; i < 100_000; i++) {
      seen.merge(mix.pick(random), 1, Integer::sum);
    }
    assertTrue(Math.abs(seen.get("STONE") - 70_000) < 2_000, seen.toString());
    assertTrue(Math.abs(seen.get("IRON_ORE") - 5_000) < 1_000, seen.toString());
  }
}
