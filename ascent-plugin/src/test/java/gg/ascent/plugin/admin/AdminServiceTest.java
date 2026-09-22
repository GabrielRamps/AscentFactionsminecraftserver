package gg.ascent.plugin.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.admin.AdminService.Outcome;
import gg.ascent.plugin.admin.AdminService.Result;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.economy.EconomyServiceImpl;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.rank.RankServiceImpl;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import gg.ascent.plugin.testing.MutableClock;
import gg.ascent.plugin.testing.RecordingAdminActionRepository;
import gg.ascent.plugin.testing.RecordingTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** PRD E1-S6: give money/xp, rank set/add, and an admin_actions row for each. */
class AdminServiceTest {

  private static final UUID STAFF = UUID.randomUUID();
  private static final UUID STEVE = UUID.randomUUID();
  private static final UUID GHOST = UUID.randomUUID();

  private InMemoryPlayerRepository repo;
  private RecordingAdminActionRepository actions;
  private FakeDbExecutor db;
  private PlayerManager players;
  private AdminService admin;

  @BeforeEach
  void setUp() {
    repo = new InMemoryPlayerRepository();
    actions = new RecordingAdminActionRepository();
    db = new FakeDbExecutor();
    MutableClock clock = new MutableClock(Instant.parse("2026-09-21T12:00:00Z"));
    var log = LoggerFactory.getLogger(AdminServiceTest.class);
    players = new PlayerManager(db, repo, 1000, clock, log);
    EconomyServiceImpl economy =
        new EconomyServiceImpl(players, repo, new RecordingTransactionRepository(), db, clock, log);
    RanksSettings ranks = SettingsParsers.ranks(TestYamlAccess.bundled("ranks.yml"));
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.ranks()).thenReturn(ranks);
    RankServiceImpl rankService =
        new RankServiceImpl(
            players, config, db, (c, p, s, a, m) -> {}, (p, from, to) -> {}, clock, log);
    admin =
        new AdminService(
            players, economy, rankService, config, new AdminActionLog(db, actions, clock, log));
    players.preLogin(STEVE, "Steve");
    players.join(STEVE, "Steve");
    repo.rows.put(GHOST, PlayerSnapshot.fresh(GHOST, "Ghost", 5, clock.instant()));
    db.tick();
  }

  @Test
  void giveMoneyToOnlinePlayer() {
    CompletableFuture<Result> future = admin.giveMoney(STAFF, "steve", 500);
    db.tick();

    assertEquals(new Result(Outcome.OK, 1500), future.join());
    assertEquals(1500, players.require(STEVE).balance());
    assertEquals(1, actions.rows.size());
    assertEquals("give.money", actions.rows.get(0).action());
    assertEquals("Steve", actions.rows.get(0).target());
    assertEquals("{\"amount\":500}", actions.rows.get(0).args());
  }

  @Test
  void giveMoneyToOfflinePlayerWritesTheRow() {
    CompletableFuture<Result> future = admin.giveMoney(STAFF, "Ghost", 95);
    db.tick();
    db.tick();
    db.tick();

    assertEquals(new Result(Outcome.OK, 100), future.join());
    assertEquals(100, repo.rows.get(GHOST).balance());
  }

  @Test
  void giveMoneyToUnknownPlayerOrBadAmount() {
    CompletableFuture<Result> unknown = admin.giveMoney(STAFF, "nobody", 5);
    db.tick();
    assertEquals(Outcome.UNKNOWN_PLAYER, unknown.join().outcome());
    assertEquals(Outcome.BAD_AMOUNT, admin.giveMoney(STAFF, "Steve", 0).join().outcome());
    assertTrue(actions.rows.isEmpty());
  }

  @Test
  void giveXpNeedsAnOnlinePlayer() {
    assertEquals(new Result(Outcome.OK, 250), admin.giveXp(STAFF, "Steve", 250));
    assertEquals(250, players.require(STEVE).xpTotal());
    // 400 XP is rank 2 on the bundled curve: XP now goes through the ladder.
    assertEquals(new Result(Outcome.OK, 100), admin.giveXp(STAFF, "Steve", 250));
    assertEquals(2, players.require(STEVE).rank());
    assertEquals(Outcome.NOT_ONLINE, admin.giveXp(STAFF, "Ghost", 1).outcome());
    assertEquals(Outcome.BAD_AMOUNT, admin.giveXp(STAFF, "Steve", 0).outcome());
    assertEquals(2, actions.rows.size());
  }

  @Test
  void rankSetClampsToTheLadder() {
    assertEquals(new Result(Outcome.OK, 42), admin.rankSet(STAFF, "Steve", 42));
    assertEquals(42, players.require(STEVE).rank());
    assertEquals(Outcome.BAD_RANK, admin.rankSet(STAFF, "Steve", 101).outcome());
    assertEquals(Outcome.BAD_RANK, admin.rankSet(STAFF, "Steve", 0).outcome());
    assertEquals(Outcome.NOT_ONLINE, admin.rankSet(STAFF, "Ghost", 5).outcome());
    assertEquals("{\"from\":1,\"to\":42}", actions.rows.get(0).args());
  }

  @Test
  void rankAddMovesWithinTheLadder() {
    admin.rankSet(STAFF, "Steve", 98);
    assertEquals(new Result(Outcome.OK, 100), admin.rankAdd(STAFF, "Steve", 5));
    assertEquals(new Result(Outcome.OK, 1), admin.rankAdd(STAFF, "Steve", -500));
    assertEquals(Outcome.BAD_RANK, admin.rankAdd(STAFF, "Steve", 0).outcome());
    assertEquals(3, actions.rows.size());
  }

  @Test
  void consoleActionsUseTheNilUuid() {
    admin.giveXp(AdminActionLog.CONSOLE, "Steve", 1);
    assertEquals(new UUID(0, 0), actions.rows.get(0).actor());
  }
}
