package gg.ascent.plugin.mine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.MinesSettings;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.mine.MinePlotRepository.Row;
import gg.ascent.plugin.mine.MinePlots.Change;
import gg.ascent.plugin.mine.MinePlots.Claim;
import gg.ascent.plugin.mine.MinePlots.Plot;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.MutableClock;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** PRD E4-S1: allocation, 24h release, tier upgrades, and the two reset triggers. */
class MinePlotsTest {

  private final TreeMap<Integer, Row> table = new TreeMap<>();
  private final MinePlotRepository repo =
      new MinePlotRepository() {
        @Override
        public List<Row> all(Connection c) {
          return new ArrayList<>(table.values());
        }

        @Override
        public void save(Connection c, Row row) {
          table.put(row.index(), row);
        }
      };

  private FakeDbExecutor db;
  private MutableClock clock;
  private MinesSettings settings;
  private MinePlots plots;

  @BeforeEach
  void setUp() {
    settings = SettingsParsers.mines(TestYamlAccess.bundled("mines.yml"));
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.mines()).thenReturn(settings);
    db = new FakeDbExecutor();
    clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
    plots = new MinePlots(config, db, repo, clock, LoggerFactory.getLogger(MinePlotsTest.class));
    plots.load();
    db.tick();
  }

  @Test
  void firstVisitAllocatesTheLowestFreePlotAndPersistsIt() {
    UUID steve = UUID.randomUUID();
    UUID alex = UUID.randomUUID();

    Claim a = plots.claim(steve, 1);
    Claim b = plots.claim(alex, 1);
    db.tick();

    assertEquals(Change.ALLOCATED, a.change());
    assertEquals(0, a.plot().index());
    assertEquals(1, b.plot().index());
    assertEquals(steve, table.get(0).owner());
    assertEquals(Change.UNCHANGED, plots.claim(steve, 1).change());
    assertEquals(0, plots.plotOf(steve).orElseThrow().index());
  }

  @Test
  void higherTierOnRevisitUpgradesAndResetsTheCount() {
    UUID steve = UUID.randomUUID();
    Plot plot = plots.claim(steve, 1).plot();
    plots.mined(plot);
    plots.mined(plot);

    Claim again = plots.claim(steve, 2);

    assertEquals(Change.UPGRADED, again.change());
    assertEquals(2, again.plot().tier());
    assertEquals(0, again.plot().mined());
    assertEquals(Change.UNCHANGED, plots.claim(steve, 1).change(), "a lower rank never downgrades");
    assertEquals(2, plots.plotOf(steve).orElseThrow().tier());
  }

  @Test
  void plotsUnusedForADayAreReclaimed() {
    UUID steve = UUID.randomUUID();
    UUID alex = UUID.randomUUID();
    plots.claim(steve, 1);
    clock.advance(Duration.ofHours(23));
    assertEquals(1, plots.claim(alex, 1).plot().index(), "still Steve's");

    clock.advance(Duration.ofHours(2));
    UUID newcomer = UUID.randomUUID();
    Claim claim = plots.claim(newcomer, 1);

    assertEquals(0, claim.plot().index(), "Steve's plot expired and was reused");
    assertTrue(plots.plotOf(steve).isEmpty());
    assertEquals(newcomer, plots.plotOf(newcomer).orElseThrow().owner());
  }

  @Test
  void resetIsDueOnTheTimerOrWhenMostlyMined() {
    UUID steve = UUID.randomUUID();
    Plot plot = plots.claim(steve, 1).plot();
    assertFalse(plots.dueForReset(plot));

    clock.advance(Duration.ofMinutes(10));
    assertTrue(plots.dueForReset(plot), "10 minutes passed");
    plots.wasReset(plot);
    assertFalse(plots.dueForReset(plot));

    int threshold = (int) Math.ceil(settings.pitVolume() * settings.resetMinedFraction());
    for (int i = 0; i < threshold - 1; i++) {
      plots.mined(plot);
    }
    assertFalse(plots.dueForReset(plot));
    plots.mined(plot);
    assertTrue(plots.dueForReset(plot), "70% mined");
  }

  @Test
  void loadRestoresOwnershipFromRows() {
    UUID steve = UUID.randomUUID();
    table.put(3, new Row(3, steve, 2, clock.instant(), clock.instant(), 10));
    plots.load();
    db.tick();

    Plot plot = plots.plotOf(steve).orElseThrow();
    assertEquals(3, plot.index());
    assertEquals(2, plot.tier());
    assertEquals(10, plot.mined());
    assertNotEquals(3, plots.claim(UUID.randomUUID(), 1).plot().index());
  }
}
