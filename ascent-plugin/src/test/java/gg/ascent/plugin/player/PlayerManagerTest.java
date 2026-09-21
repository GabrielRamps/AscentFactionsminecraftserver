package gg.ascent.plugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.db.DbException;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** PRD E1-S3: first-join defaults, load-or-kick, dirty-flag autosave, quit and shutdown flush. */
class PlayerManagerTest {

  private static final UUID STEVE = UUID.randomUUID();

  private InMemoryPlayerRepository repo;
  private FakeDbExecutor db;
  private MutableClock clock;
  private PlayerManager manager;

  @BeforeEach
  void setUp() {
    repo = new InMemoryPlayerRepository();
    db = new FakeDbExecutor();
    clock = new MutableClock(Instant.parse("2026-09-21T12:00:00Z"));
    manager =
        new PlayerManager(db, repo, 1000, clock, LoggerFactory.getLogger(PlayerManagerTest.class));
  }

  @Test
  void firstJoinCreatesTheRowWithTheDocumentedDefaults() {
    manager.preLogin(STEVE, "Steve");

    PlayerSnapshot row = repo.rows.get(STEVE);
    assertNotNull(row, "row must be inserted during pre-login");
    assertEquals(1, row.rank());
    assertEquals(0, row.xp());
    assertEquals(1000, row.balance());
    assertFalse(row.starterKitClaimed());
    assertEquals(clock.instant(), row.firstSeen());
    assertEquals(1, manager.pendingCount());
  }

  @Test
  void returningPlayerLoadsTheExistingRow() {
    repo.rows.put(
        STEVE,
        new PlayerSnapshot(
            STEVE,
            "Steve",
            7,
            300,
            9000,
            0,
            5500,
            0,
            clock.instant(),
            null,
            true,
            clock.instant().minus(Duration.ofDays(3)),
            clock.instant(),
            100));

    manager.preLogin(STEVE, "Steve");
    PlayerProfile profile = manager.join(STEVE, "Steve").orElseThrow();

    assertEquals(7, profile.rank());
    assertEquals(5500, profile.balance());
    assertTrue(profile.starterKitClaimed());
    assertEquals(0, manager.pendingCount());
    assertEquals(1, manager.online().size());
  }

  @Test
  void loadFailurePropagatesSoTheListenerCanKick() {
    repo.failNext = true;

    assertThrows(DbException.class, () -> manager.preLogin(STEVE, "Steve"));
    assertEquals(0, manager.pendingCount());
  }

  @Test
  void joinWithoutPreLoginIsEmpty() {
    assertTrue(manager.join(STEVE, "Steve").isEmpty());
  }

  @Test
  void joinOpensASessionAndRecordsItsIdOnTheNextTick() {
    manager.preLogin(STEVE, "Steve");
    manager.join(STEVE, "Steve");

    assertTrue(repo.calls.contains("openSession"));
    db.tick();
    clock.advance(Duration.ofMinutes(5));
    manager.quit(STEVE);
    db.tick();

    assertEquals(1, repo.sessions.size());
    Instant[] session = repo.sessions.values().iterator().next();
    assertNotNull(session[1], "quit closes the session");
    assertEquals(300, repo.rows.get(STEVE).playSeconds());
  }

  @Test
  void autosaveWritesOnlyDirtyProfilesAndClearsTheFlag() {
    UUID alex = UUID.randomUUID();
    manager.preLogin(STEVE, "Steve");
    manager.preLogin(alex, "Alex");
    manager.join(STEVE, "Steve");
    manager.join(alex, "Alex");
    db.tick();
    // join itself dirties the profile (last_seen); write that out first.
    manager.autosave();
    db.tick();
    repo.calls.clear();

    manager.require(STEVE).setBalance(1234);
    assertTrue(manager.require(STEVE).isDirty());
    assertFalse(manager.require(alex).isDirty());

    CompletableFuture<Void> save = manager.autosave();
    db.tick();

    assertTrue(save.isDone());
    assertEquals(1, repo.calls.stream().filter("update"::equals).count());
    assertEquals(1234, repo.rows.get(STEVE).balance());
    assertFalse(manager.require(STEVE).isDirty());
  }

  @Test
  void nothingDirtyMeansNoDatabaseCall() {
    manager.preLogin(STEVE, "Steve");
    manager.join(STEVE, "Steve");
    db.tick();
    manager.autosave();
    db.tick();
    repo.calls.clear();

    CompletableFuture<Void> save = manager.autosave();

    assertTrue(save.isDone());
    assertTrue(repo.calls.isEmpty());
  }

  @Test
  void failedAutosaveKeepsTheProfileDirty() {
    manager.preLogin(STEVE, "Steve");
    manager.join(STEVE, "Steve");
    db.tick();
    manager.require(STEVE).setBalance(42);
    repo.failNext = true;

    manager.autosave();
    db.tick();

    assertTrue(manager.require(STEVE).isDirty(), "must retry next interval");
    assertEquals(1000, repo.rows.get(STEVE).balance());
  }

  @Test
  void quitWritesAndForgetsThePlayer() {
    manager.preLogin(STEVE, "Steve");
    manager.join(STEVE, "Steve");
    db.tick();
    manager.require(STEVE).setRank(3);

    manager.quit(STEVE);
    db.tick();

    assertEquals(3, repo.rows.get(STEVE).rank());
    assertTrue(manager.profile(STEVE).isEmpty());
    assertThrows(IllegalStateException.class, () -> manager.require(STEVE));
  }

  @Test
  void flushAllWritesEveryoneSynchronously() {
    UUID alex = UUID.randomUUID();
    manager.preLogin(STEVE, "Steve");
    manager.preLogin(alex, "Alex");
    manager.join(STEVE, "Steve");
    manager.join(alex, "Alex");
    db.tick();
    manager.require(STEVE).setXp(50);
    clock.advance(Duration.ofSeconds(90));

    manager.flushAll();

    // No tick was needed: the flush is blocking.
    assertEquals(50, repo.rows.get(STEVE).xp());
    assertEquals(90, repo.rows.get(alex).playSeconds());
    assertTrue(manager.online().isEmpty());
    assertTrue(repo.sessions.values().stream().allMatch(s -> s[1] != null));
  }

  @Test
  void pendingLoginsThatNeverJoinAreForgotten() {
    manager.preLogin(STEVE, "Steve");
    clock.advance(PlayerManager.PENDING_TTL.plusSeconds(1));

    manager.autosave();

    assertEquals(0, manager.pendingCount());
    assertTrue(manager.join(STEVE, "Steve").isEmpty());
  }

  @Test
  void refusedLoginIsForgotten() {
    manager.preLogin(STEVE, "Steve");
    manager.loginRefused(STEVE);

    assertEquals(0, manager.pendingCount());
  }

  @Test
  void lookupPrefersTheLiveProfile() {
    manager.preLogin(STEVE, "Steve");
    manager.join(STEVE, "Steve");
    manager.require(STEVE).setBalance(77);

    Optional<PlayerSnapshot> snapshot = manager.lookup(STEVE).join();

    assertEquals(77, snapshot.orElseThrow().balance());
  }

  @Test
  void lookupByNameFallsBackToTheDatabase() {
    repo.rows.put(STEVE, PlayerSnapshot.fresh(STEVE, "Steve", 10, clock.instant()));

    CompletableFuture<Optional<PlayerSnapshot>> future = manager.lookupByName("steve");
    db.tick();

    assertEquals(STEVE, future.join().orElseThrow().uuid());
    assertTrue(manager.lookupByName("nobody").isDone() || db.pendingCompletions() > 0);
  }

  /** A clock a test can move. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
