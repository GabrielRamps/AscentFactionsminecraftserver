package gg.ascent.plugin.economy;

import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.player.PlayerRepository;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * {@link EconomyService} over live profiles and the players table.
 *
 * <p>An online player's balance lives on the profile; the autosave persists it. Each mutation also
 * writes its own {@code money_transactions} row, asynchronously, so the audit trail never blocks
 * the game. For offline players both the balance and the row are written in one transaction.
 */
public final class EconomyServiceImpl implements EconomyService {

  private final PlayerService players;
  private final PlayerRepository playerRepo;
  private final TransactionRepository transactions;
  private final DbExecutor db;
  private final Clock clock;
  private final Logger log;

  public EconomyServiceImpl(
      PlayerService players,
      PlayerRepository playerRepo,
      TransactionRepository transactions,
      DbExecutor db,
      Clock clock,
      Logger log) {
    this.players = players;
    this.playerRepo = playerRepo;
    this.transactions = transactions;
    this.db = db;
    this.clock = clock;
    this.log = log;
  }

  @Override
  public long getBalance(UUID player) {
    return players.require(player).balance();
  }

  @Override
  public boolean withdraw(UUID player, long amount, TxReason reason, @Nullable String ref) {
    if (!EconomyService.isValidAmount(amount)) {
      return false;
    }
    Optional<PlayerProfile> profile = players.profile(player);
    if (profile.isEmpty() || profile.get().balance() < amount) {
      return false;
    }
    apply(profile.get(), -amount, reason, ref);
    return true;
  }

  @Override
  public void deposit(UUID player, long amount, TxReason reason, @Nullable String ref) {
    requireValid(amount);
    apply(players.require(player), amount, reason, ref);
  }

  @Override
  public boolean transfer(UUID from, UUID to, long amount, TxReason reason) {
    if (!EconomyService.isValidAmount(amount) || from.equals(to)) {
      return false;
    }
    Optional<PlayerProfile> payer = players.profile(from);
    Optional<PlayerProfile> payee = players.profile(to);
    if (payer.isEmpty() || payee.isEmpty() || payer.get().balance() < amount) {
      return false;
    }
    String ref = to.toString();
    apply(payer.get(), -amount, reason, ref);
    apply(payee.get(), amount, reason, from.toString());
    return true;
  }

  @Override
  public CompletableFuture<Optional<Long>> getBalanceOffline(UUID player) {
    Optional<PlayerProfile> live = players.profile(player);
    if (live.isPresent()) {
      return CompletableFuture.completedFuture(Optional.of(live.get().balance()));
    }
    return db.supply(c -> playerRepo.find(c, player).map(PlayerSnapshot::balance));
  }

  @Override
  public CompletableFuture<Boolean> depositOffline(
      UUID player, long amount, TxReason reason, @Nullable String ref) {
    requireValid(amount);
    Optional<PlayerProfile> live = players.profile(player);
    if (live.isPresent()) {
      apply(live.get(), amount, reason, ref);
      return CompletableFuture.completedFuture(true);
    }
    return adjustOffline(player, amount, reason, ref);
  }

  @Override
  public CompletableFuture<Boolean> transferOffline(
      UUID from, UUID to, long amount, TxReason reason) {
    if (!EconomyService.isValidAmount(amount) || from.equals(to)) {
      return CompletableFuture.completedFuture(false);
    }
    Optional<PlayerProfile> payee = players.profile(to);
    if (payee.isPresent()) {
      return CompletableFuture.completedFuture(transfer(from, to, amount, reason));
    }
    Optional<PlayerProfile> payer = players.profile(from);
    if (payer.isEmpty() || payer.get().balance() < amount) {
      return CompletableFuture.completedFuture(false);
    }
    // Debit now so the payer cannot spend the same money twice while the write is in flight.
    apply(payer.get(), -amount, reason, to.toString());
    return adjustOffline(to, amount, reason, from.toString())
        .thenApply(
            credited -> {
              if (!credited) {
                refund(from, amount, reason, to.toString());
              }
              return credited;
            })
        .exceptionally(
            error -> {
              log.error("Transfer to offline player {} failed; refunding", to, error);
              refund(from, amount, reason, to.toString());
              return false;
            });
  }

  /** Changes a live balance and queues the audit row. Main thread. */
  private void apply(PlayerProfile profile, long delta, TxReason reason, @Nullable String ref) {
    long after = profile.balance() + delta;
    profile.setBalance(after);
    Instant now = clock.instant();
    UUID uuid = profile.uuid();
    Futures.logFailure(
        db.run(c -> transactions.record(c, uuid, null, delta, after, reason, ref, now)),
        log,
        "recording " + reason + " of " + delta + " for " + profile.name());
  }

  /** Changes a stored balance and writes the audit row, in one transaction. */
  private CompletableFuture<Boolean> adjustOffline(
      UUID player, long delta, TxReason reason, @Nullable String ref) {
    Instant now = clock.instant();
    return db.transaction(
        c -> {
          if (playerRepo.find(c, player).isEmpty()) {
            return false;
          }
          long after = playerRepo.adjustBalance(c, player, delta);
          transactions.record(c, player, null, delta, after, reason, ref, now);
          return true;
        });
  }

  private void refund(UUID player, long amount, TxReason reason, String ref) {
    Optional<PlayerProfile> live = players.profile(player);
    if (live.isPresent()) {
      apply(live.get(), amount, reason, "refund:" + ref);
    } else {
      // The payer left while the write was in flight; put it back in the database instead.
      Futures.logFailure(
          adjustOffline(player, amount, reason, "refund:" + ref), log, "refunding " + player);
    }
  }

  private static void requireValid(long amount) {
    if (!EconomyService.isValidAmount(amount)) {
      throw new IllegalArgumentException(
          "amount must be 1.." + EconomyService.MAX_AMOUNT + ", got " + amount);
    }
  }
}
