package gg.ascent.plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.rank.RankService.Progress;
import gg.ascent.api.rank.XpSource;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import gg.ascent.plugin.testing.MutableClock;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** PRD E2-S1: thresholds, carry-over, multi-rank jumps, the cap and banking, and the log. */
class RankServiceImplTest {

  private static final UUID STEVE = UUID.randomUUID();

  private record Log(UUID player, XpSource source, long amount, Instant minute) {}

  private record RankUp(int from, int to) {}

  private final List<Log> logged = new ArrayList<>();
  private final List<RankUp> rankUps = new ArrayList<>();
  private FakeDbExecutor db;
  private PlayerManager players;
  private RankServiceImpl ranks;
  private RanksSettings settings;

  @BeforeEach
  void setUp() {
    settings = SettingsParsers.ranks(TestYamlAccess.bundled("ranks.yml"));
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.ranks()).thenReturn(settings);
    db = new FakeDbExecutor();
    MutableClock clock = new MutableClock(Instant.parse("2026-09-22T12:00:30Z"));
    var log = LoggerFactory.getLogger(RankServiceImplTest.class);
    players = new PlayerManager(db, new InMemoryPlayerRepository(), 1000, clock, log);
    XpLogRepository xpLog =
        (Connection c, UUID p, XpSource s, long a, Instant m) -> logged.add(new Log(p, s, a, m));
    ranks =
        new RankServiceImpl(
            players,
            config,
            db,
            xpLog,
            (p, from, to) -> rankUps.add(new RankUp(from, to)),
            clock,
            log);
    players.preLogin(STEVE, "Steve");
    players.join(STEVE, "Steve");
    db.tick();
  }

  private PlayerProfile steve() {
    return players.require(STEVE);
  }

  @Test
  void curveMatchesThePrd() {
    assertEquals(400, ranks.xpToNext(1));
    assertEquals(Math.round(400 * Math.pow(10, 1.4)), ranks.xpToNext(10));
    assertEquals(0, ranks.xpToNext(100), "nothing to earn past the top");
    assertEquals(100, ranks.maxRank());
  }

  @Test
  void xpBelowThresholdJustAccumulates() {
    ranks.addXp(STEVE, XpSource.MINE_BLOCK, 150);
    db.tick();

    Progress p = ranks.progress(STEVE);
    assertEquals(1, p.rank());
    assertEquals(150, p.xp());
    assertEquals(400, p.xpToNext());
    assertEquals(150, steve().xpTotal());
    assertTrue(rankUps.isEmpty());
    assertEquals(1, logged.size());
    assertEquals(Instant.parse("2026-09-22T12:00:30Z"), logged.get(0).minute());
  }

  @Test
  void reachingTheThresholdRanksUpAndCarriesExcess() {
    ranks.addXp(STEVE, XpSource.PVP_KILL, 500);

    assertEquals(2, ranks.getRank(STEVE));
    assertEquals(100, ranks.getXp(STEVE));
    assertEquals(List.of(new RankUp(1, 2)), rankUps);
  }

  @Test
  void aBigGrantJumpsSeveralRanksWithOneNotificationSpanningThem() {
    // 400 (1->2) + 1056 (2->3) = 1456; 1500 leaves 44 toward rank 4.
    ranks.addXp(STEVE, XpSource.ADMIN, 1500);

    assertEquals(3, ranks.getRank(STEVE));
    assertEquals(1500 - 400 - ranks.xpToNext(2), ranks.getXp(STEVE));
    assertEquals(List.of(new RankUp(1, 3)), rankUps);
  }

  @Test
  void theTopRankCapsAndBanksTheRest() {
    steve().setRank(99);
    steve().setXp(0);
    long need = ranks.xpToNext(99);

    ranks.addXp(STEVE, XpSource.KOTH_WIN, need + 250);
    assertEquals(100, ranks.getRank(STEVE));
    assertEquals(0, ranks.getXp(STEVE));
    assertEquals(250, steve().xpBanked());
    assertTrue(ranks.progress(STEVE).atTop());

    ranks.addXp(STEVE, XpSource.ENVOY_CRATE, 300);
    assertEquals(100, ranks.getRank(STEVE));
    assertEquals(550, steve().xpBanked());
    assertEquals(List.of(new RankUp(99, 100)), rankUps);
  }

  @Test
  void nonPositiveAmountsAndOfflinePlayersAreIgnored() {
    ranks.addXp(STEVE, XpSource.MINE_BLOCK, 0);
    ranks.addXp(STEVE, XpSource.MINE_BLOCK, -5);
    ranks.addXp(UUID.randomUUID(), XpSource.MINE_BLOCK, 50);
    db.tick();

    assertEquals(0, steve().xpTotal());
    assertTrue(logged.isEmpty());
  }

  @Test
  void progressFractionIsClamped() {
    assertEquals(0.0, new Progress(1, 0, 400, 0).fraction());
    assertEquals(0.5, new Progress(1, 200, 400, 0).fraction());
    assertEquals(1.0, new Progress(100, 0, 0, 9).fraction());
  }
}
