package gg.ascent.plugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.testing.FakeDbExecutor;
import gg.ascent.plugin.testing.InMemoryPlayerRepository;
import gg.ascent.plugin.testing.MutableClock;
import gg.ascent.plugin.testing.RecordingTransactionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** PRD E1-S4: every mutation leaves a money_transactions row; limits; offline paths. */
class EconomyServiceImplTest {

  private static final UUID STEVE = UUID.randomUUID();
  private static final UUID ALEX = UUID.randomUUID();
  private static final UUID GHOST = UUID.randomUUID();

  private InMemoryPlayerRepository repo;
  private RecordingTransactionRepository tx;
  private FakeDbExecutor db;
  private PlayerManager players;
  private EconomyServiceImpl economy;

  @BeforeEach
  void setUp() {
    repo = new InMemoryPlayerRepository();
    tx = new RecordingTransactionRepository();
    db = new FakeDbExecutor();
    MutableClock clock = new MutableClock(Instant.parse("2026-09-21T12:00:00Z"));
    players =
        new PlayerManager(
            db, repo, 1000, clock, LoggerFactory.getLogger(EconomyServiceImplTest.class));
    economy =
        new EconomyServiceImpl(
            players, repo, tx, db, clock, LoggerFactory.getLogger(EconomyServiceImplTest.class));
    join(STEVE, "Steve");
    join(ALEX, "Alex");
    repo.rows.put(GHOST, PlayerSnapshot.fresh(GHOST, "Ghost", 250, clock.instant()));
    db.tick();
  }

  private void join(UUID uuid, String name) {
    players.preLogin(uuid, name);
    players.join(uuid, name);
  }

  @Test
  void depositAndWithdrawChangeTheLiveBalanceAndLogRows() {
    economy.deposit(STEVE, 500, TxReason.SELL, "shop");
    assertTrue(economy.withdraw(STEVE, 200, TxReason.SHOP_BUY, "sword"));
    db.tick();

    assertEquals(1300, economy.getBalance(STEVE));
    assertTrue(players.require(STEVE).isDirty());
    assertEquals(2, tx.rows.size());
    assertEquals(
        new RecordingTransactionRepository.Row(STEVE, null, 500, 1500, TxReason.SELL, "shop"),
        tx.rows.get(0));
    assertEquals(
        new RecordingTransactionRepository.Row(STEVE, null, -200, 1300, TxReason.SHOP_BUY, "sword"),
        tx.rows.get(1));
  }

  @Test
  void withdrawRefusesOverdraftOfflineAndBadAmounts() {
    assertFalse(economy.withdraw(STEVE, 1001, TxReason.SHOP_BUY, null));
    assertFalse(economy.withdraw(GHOST, 1, TxReason.SHOP_BUY, null));
    assertFalse(economy.withdraw(STEVE, 0, TxReason.SHOP_BUY, null));
    assertFalse(economy.withdraw(STEVE, -5, TxReason.SHOP_BUY, null));
    assertFalse(economy.withdraw(STEVE, EconomyService.MAX_AMOUNT + 1, TxReason.SHOP_BUY, null));
    db.tick();

    assertEquals(1000, economy.getBalance(STEVE));
    assertTrue(tx.rows.isEmpty());
  }

  @Test
  void depositRejectsBadAmountsAndOfflinePlayers() {
    assertThrows(
        IllegalArgumentException.class, () -> economy.deposit(STEVE, 0, TxReason.ADMIN, null));
    assertThrows(
        IllegalStateException.class, () -> economy.deposit(GHOST, 5, TxReason.ADMIN, null));
  }

  @Test
  void transferBetweenOnlinePlayersIsAtomicAndLogsBothSides() {
    assertTrue(economy.transfer(STEVE, ALEX, 400, TxReason.PAY));
    db.tick();

    assertEquals(600, economy.getBalance(STEVE));
    assertEquals(1400, economy.getBalance(ALEX));
    assertEquals(2, tx.rows.size());
    assertEquals(-400, tx.rows.get(0).amount());
    assertEquals(ALEX.toString(), tx.rows.get(0).ref());
    assertEquals(400, tx.rows.get(1).amount());
    assertEquals(STEVE.toString(), tx.rows.get(1).ref());
  }

  @Test
  void transferRefusesSelfInsufficientAndOffline() {
    assertFalse(economy.transfer(STEVE, STEVE, 1, TxReason.PAY));
    assertFalse(economy.transfer(STEVE, ALEX, 5000, TxReason.PAY));
    assertFalse(economy.transfer(STEVE, GHOST, 1, TxReason.PAY));
    assertEquals(1000, economy.getBalance(STEVE));
  }

  @Test
  void offlineBalanceComesFromTheDatabase() {
    CompletableFuture<Optional<Long>> ghost = economy.getBalanceOffline(GHOST);
    CompletableFuture<Optional<Long>> nobody = economy.getBalanceOffline(UUID.randomUUID());
    db.tick();

    assertEquals(Optional.of(250L), ghost.join());
    assertEquals(Optional.empty(), nobody.join());
    assertEquals(Optional.of(1000L), economy.getBalanceOffline(STEVE).join());
  }

  @Test
  void transferToOfflinePlayerDebitsNowAndCreditsTheRow() {
    CompletableFuture<Boolean> result = economy.transferOffline(STEVE, GHOST, 100, TxReason.PAY);
    assertEquals(900, economy.getBalance(STEVE), "debited before the write completes");
    db.tick();

    assertTrue(result.join());
    assertEquals(350, repo.rows.get(GHOST).balance());
    assertEquals(1, db.transactions, "offline credit and its row share one transaction");
    assertTrue(tx.rows.stream().anyMatch(r -> GHOST.equals(r.player()) && r.amount() == 100));
  }

  @Test
  void transferToUnknownPlayerRefundsThePayer() {
    UUID nobody = UUID.randomUUID();
    CompletableFuture<Boolean> result = economy.transferOffline(STEVE, nobody, 100, TxReason.PAY);
    db.tick();
    db.tick();

    assertFalse(result.join());
    assertEquals(1000, economy.getBalance(STEVE));
    assertTrue(tx.rows.stream().anyMatch(r -> r.ref() != null && r.ref().startsWith("refund:")));
  }

  @Test
  void transferToOfflinePlayerFailsFastWhenPayerCannotAfford() {
    CompletableFuture<Boolean> result = economy.transferOffline(STEVE, GHOST, 5000, TxReason.PAY);

    assertTrue(result.isDone());
    assertFalse(result.join());
    assertEquals(1000, economy.getBalance(STEVE));
  }

  @Test
  void depositOfflineCreditsTheRowForUnknownAndKnownPlayers() {
    CompletableFuture<Boolean> ghost = economy.depositOffline(GHOST, 50, TxReason.ADMIN, "give");
    CompletableFuture<Boolean> nobody =
        economy.depositOffline(UUID.randomUUID(), 50, TxReason.ADMIN, "give");
    CompletableFuture<Boolean> online = economy.depositOffline(STEVE, 50, TxReason.ADMIN, "give");
    db.tick();

    assertTrue(ghost.join());
    assertFalse(nobody.join());
    assertTrue(online.join());
    assertEquals(300, repo.rows.get(GHOST).balance());
    assertEquals(1050, economy.getBalance(STEVE));
  }
}
