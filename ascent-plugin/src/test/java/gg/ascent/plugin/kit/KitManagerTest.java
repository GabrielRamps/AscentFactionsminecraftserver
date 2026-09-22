package gg.ascent.plugin.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.kit.KitService.ClaimResult;
import gg.ascent.api.kit.KitService.KitStatus;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.rank.UnlockServiceImpl;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import gg.ascent.plugin.testing.MutableClock;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** PRD E2-S3: rank gates, cooldowns that persist, the starter flag, and the join-time load. */
class KitManagerTest {

  private static final UUID STEVE = UUID.randomUUID();

  /** The kit_cooldowns table as a map. */
  private final Map<UUID, Map<String, Instant>> table = new HashMap<>();

  private final KitCooldownRepository repo =
      new KitCooldownRepository() {
        @Override
        public Map<String, Instant> load(Connection c, UUID player) {
          return new HashMap<>(table.getOrDefault(player, Map.of()));
        }

        @Override
        public void set(Connection c, UUID player, String kitId, Instant nextAt) {
          table.computeIfAbsent(player, k -> new HashMap<>()).put(kitId, nextAt);
        }
      };

  private final List<String> given = new ArrayList<>();
  private FakeDbExecutor db;
  private MutableClock clock;
  private PlayerManager players;
  private KitManager kits;

  @BeforeEach
  void setUp() {
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.ranks())
        .thenReturn(SettingsParsers.ranks(TestYamlAccess.bundled("ranks.yml")));
    Mockito.when(config.enchants())
        .thenReturn(SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml")));
    Mockito.when(config.kits())
        .thenReturn(SettingsParsers.kits(TestYamlAccess.bundled("kits.yml")));
    db = new FakeDbExecutor();
    clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
    var log = LoggerFactory.getLogger(KitManagerTest.class);
    players = new PlayerManager(db, new InMemoryPlayerRepository(), 1000, clock, log);
    kits =
        new KitManager(
            config,
            players,
            new UnlockServiceImpl(config),
            db,
            repo,
            (player, kit) -> given.add(kit.id()),
            clock,
            log);
    players.preLogin(STEVE, "Steve");
    players.join(STEVE, "Steve");
    kits.playerJoined(STEVE);
    db.tick();
  }

  @Test
  void statusesFollowRankAndCooldown() {
    List<KitStatus> statuses = kits.kits(STEVE);

    assertEquals(5, statuses.size());
    assertTrue(statuses.get(0).ready(), "starter is open at rank 1");
    assertFalse(statuses.get(1).unlocked(), "rank10 is locked at rank 1");
    assertEquals(10, statuses.get(1).minRank());
  }

  @Test
  void claimGivesStartsTheCooldownAndSetsTheStarterFlag() {
    assertEquals(ClaimResult.OK, kits.claim(STEVE, "starter"));
    db.tick();

    assertEquals(List.of("starter"), given);
    assertTrue(players.require(STEVE).starterKitClaimed());
    assertEquals(clock.instant().plus(Duration.ofHours(24)), table.get(STEVE).get("starter"));
    assertEquals(ClaimResult.ON_COOLDOWN, kits.claim(STEVE, "starter"));
    assertEquals(Duration.ofHours(24), kits.kits(STEVE).get(0).cooldownLeft());

    clock.advance(Duration.ofHours(23));
    assertEquals(ClaimResult.ON_COOLDOWN, kits.claim(STEVE, "starter"));
    clock.advance(Duration.ofHours(1));
    assertEquals(ClaimResult.OK, kits.claim(STEVE, "starter"));
    assertEquals(2, given.size());
  }

  @Test
  void lockedAndUnknownKits() {
    assertEquals(ClaimResult.LOCKED, kits.claim(STEVE, "rank10"));
    assertEquals(ClaimResult.UNKNOWN_KIT, kits.claim(STEVE, "nope"));
    assertEquals(ClaimResult.UNKNOWN_KIT, kits.claim(UUID.randomUUID(), "starter"));
    assertTrue(given.isEmpty());

    players.require(STEVE).setRank(10);
    assertEquals(ClaimResult.OK, kits.claim(STEVE, "rank10"));
  }

  @Test
  void cooldownsSurviveARelog() {
    kits.claim(STEVE, "starter");
    db.tick();
    kits.playerQuit(STEVE);
    players.quit(STEVE);
    db.tick();
    clock.advance(Duration.ofHours(2));

    players.preLogin(STEVE, "Steve");
    players.join(STEVE, "Steve");
    kits.playerJoined(STEVE);
    assertFalse(kits.isLoaded(STEVE), "the load has not completed yet");
    assertEquals(ClaimResult.NOT_READY, kits.claim(STEVE, "starter"));
    db.tick();

    assertTrue(kits.isLoaded(STEVE));
    assertEquals(ClaimResult.ON_COOLDOWN, kits.claim(STEVE, "starter"));
    assertEquals(Duration.ofHours(22), kits.kits(STEVE).get(0).cooldownLeft());
  }

  @Test
  void humanDurations() {
    assertEquals("0s", Durations.humanize(Duration.ZERO));
    assertEquals("45s", Durations.humanize(Duration.ofSeconds(45)));
    assertEquals("4m 12s", Durations.humanize(Duration.ofSeconds(252)));
    assertEquals("23h 59m", Durations.humanize(Duration.ofHours(24).minusSeconds(1)));
    assertEquals("2d 3h", Durations.humanize(Duration.ofHours(51)));
    assertEquals("Diamond Sword", KitMenu.pretty("DIAMOND_SWORD"));
  }
}
