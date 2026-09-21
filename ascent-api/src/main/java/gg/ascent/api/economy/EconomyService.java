package gg.ascent.api.economy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Player money (PRD E1-S4). Balances are whole dollars.
 *
 * <p>Online players are served from their live profile, on the main thread, and the answer is
 * immediate. Every mutation writes a {@code money_transactions} row with its reason. Offline
 * players are reachable through the {@code ...Offline} methods, which go to the database and
 * complete on the main thread.
 *
 * <p>The largest amount any call accepts is {@link #MAX_AMOUNT}, so a balance can never exceed what
 * a double (and therefore Vault) represents exactly.
 */
public interface EconomyService {

  /** The largest single amount: 2^53, the last integer a double holds exactly. */
  long MAX_AMOUNT = 1L << 53;

  /**
   * The balance of an online player.
   *
   * @throws IllegalStateException if the player is not online
   */
  long getBalance(UUID player);

  /**
   * Takes {@code amount} from an online player.
   *
   * @return false if the player is not online, has less than {@code amount}, or {@code amount} is
   *     out of range; nothing changes in that case
   */
  boolean withdraw(UUID player, long amount, TxReason reason, @Nullable String ref);

  /**
   * Gives {@code amount} to an online player.
   *
   * @throws IllegalStateException if the player is not online
   * @throws IllegalArgumentException if {@code amount} is out of range
   */
  void deposit(UUID player, long amount, TxReason reason, @Nullable String ref);

  /**
   * Moves {@code amount} from one online player to another online player, atomically.
   *
   * @return false if either player is offline or the payer cannot afford it; nothing changes then
   */
  boolean transfer(UUID from, UUID to, long amount, TxReason reason);

  /** The balance of any known player, or empty if the player has never joined. */
  CompletableFuture<Optional<Long>> getBalanceOffline(UUID player);

  /**
   * Gives {@code amount} to a player who may be offline. An online player is credited in memory at
   * once; an offline one in the database. The future tells whether the player was known.
   */
  CompletableFuture<Boolean> depositOffline(
      UUID player, long amount, TxReason reason, @Nullable String ref);

  /**
   * Moves {@code amount} from an online payer to a player who may be offline. The payer is debited
   * at once; if the recipient turns out to be unknown or the write fails, the payer is refunded and
   * the future answers false.
   *
   * @return a future answering whether the transfer happened; it fails fast (already completed,
   *     false) if the payer is offline or cannot afford it
   */
  CompletableFuture<Boolean> transferOffline(UUID from, UUID to, long amount, TxReason reason);

  /**
   * Whether {@code amount} is a legal single amount: at least 1 and at most {@link #MAX_AMOUNT}.
   */
  static boolean isValidAmount(long amount) {
    return amount >= 1 && amount <= MAX_AMOUNT;
  }
}
