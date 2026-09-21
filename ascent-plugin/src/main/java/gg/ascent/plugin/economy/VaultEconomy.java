package gg.ascent.plugin.economy;

import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.player.PlayerService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Vault's {@link Economy} over {@link EconomyService}, so third-party plugins see the same balance
 * (PRD E1-S4).
 *
 * <p>Vault deals in doubles and offline players; Ascent in whole dollars and live profiles. Amounts
 * are rounded down to whole dollars. Reads and deposits for offline players go to the database,
 * blocking the caller, because Vault's contract is synchronous; withdrawals from offline players
 * are refused, since nothing in Ascent needs them and they invite races. Banks are not supported.
 */
public final class VaultEconomy implements Economy {

  private static final String NOT_ONLINE = "player is not online";

  private final EconomyService economy;
  private final PlayerService players;

  public VaultEconomy(EconomyService economy, PlayerService players) {
    this.economy = economy;
    this.players = players;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public String getName() {
    return "Ascent";
  }

  @Override
  public boolean hasBankSupport() {
    return false;
  }

  @Override
  public int fractionalDigits() {
    return 0;
  }

  @Override
  public String format(double amount) {
    return Money.format((long) Math.floor(amount));
  }

  @Override
  public String currencyNamePlural() {
    return "dollars";
  }

  @Override
  public String currencyNameSingular() {
    return "dollar";
  }

  // --- accounts --------------------------------------------------------------------------------

  @Override
  public boolean hasAccount(OfflinePlayer player) {
    return balanceOf(player.getUniqueId()).isPresent();
  }

  @Override
  public boolean hasAccount(OfflinePlayer player, String world) {
    return hasAccount(player);
  }

  @Override
  @Deprecated
  public boolean hasAccount(String name) {
    return hasAccount(byName(name));
  }

  @Override
  @Deprecated
  public boolean hasAccount(String name, String world) {
    return hasAccount(name);
  }

  /** Accounts are created on first join (E1-S3); there is nothing to create here. */
  @Override
  public boolean createPlayerAccount(OfflinePlayer player) {
    return hasAccount(player);
  }

  @Override
  public boolean createPlayerAccount(OfflinePlayer player, String world) {
    return createPlayerAccount(player);
  }

  @Override
  @Deprecated
  public boolean createPlayerAccount(String name) {
    return createPlayerAccount(byName(name));
  }

  @Override
  @Deprecated
  public boolean createPlayerAccount(String name, String world) {
    return createPlayerAccount(name);
  }

  // --- balances --------------------------------------------------------------------------------

  @Override
  public double getBalance(OfflinePlayer player) {
    return balanceOf(player.getUniqueId()).orElse(0L);
  }

  @Override
  public double getBalance(OfflinePlayer player, String world) {
    return getBalance(player);
  }

  @Override
  @Deprecated
  public double getBalance(String name) {
    return getBalance(byName(name));
  }

  @Override
  @Deprecated
  public double getBalance(String name, String world) {
    return getBalance(name);
  }

  @Override
  public boolean has(OfflinePlayer player, double amount) {
    return getBalance(player) >= amount;
  }

  @Override
  public boolean has(OfflinePlayer player, String world, double amount) {
    return has(player, amount);
  }

  @Override
  @Deprecated
  public boolean has(String name, double amount) {
    return has(byName(name), amount);
  }

  @Override
  @Deprecated
  public boolean has(String name, String world, double amount) {
    return has(name, amount);
  }

  // --- mutations -------------------------------------------------------------------------------

  @Override
  public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
    long whole = whole(amount);
    if (whole < 0) {
      return failure(amount, 0, "amount must be positive");
    }
    UUID uuid = player.getUniqueId();
    if (players.profile(uuid).isEmpty()) {
      return failure(amount, getBalance(player), NOT_ONLINE);
    }
    if (whole == 0) {
      return success(0, economy.getBalance(uuid));
    }
    if (!economy.withdraw(uuid, whole, TxReason.VAULT_WITHDRAW, null)) {
      return failure(amount, economy.getBalance(uuid), "insufficient funds");
    }
    return success(whole, economy.getBalance(uuid));
  }

  @Override
  public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
    return withdrawPlayer(player, amount);
  }

  @Override
  @Deprecated
  public EconomyResponse withdrawPlayer(String name, double amount) {
    return withdrawPlayer(byName(name), amount);
  }

  @Override
  @Deprecated
  public EconomyResponse withdrawPlayer(String name, String world, double amount) {
    return withdrawPlayer(name, amount);
  }

  @Override
  public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
    long whole = whole(amount);
    if (whole < 0) {
      return failure(amount, 0, "amount must be positive");
    }
    UUID uuid = player.getUniqueId();
    if (players.profile(uuid).isPresent()) {
      if (whole > 0) {
        economy.deposit(uuid, whole, TxReason.VAULT_DEPOSIT, null);
      }
      return success(whole, economy.getBalance(uuid));
    }
    if (whole == 0) {
      return success(0, getBalance(player));
    }
    boolean known = economy.depositOffline(uuid, whole, TxReason.VAULT_DEPOSIT, null).join();
    if (!known) {
      return failure(amount, 0, "unknown player");
    }
    return success(whole, getBalance(player));
  }

  @Override
  public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
    return depositPlayer(player, amount);
  }

  @Override
  @Deprecated
  public EconomyResponse depositPlayer(String name, double amount) {
    return depositPlayer(byName(name), amount);
  }

  @Override
  @Deprecated
  public EconomyResponse depositPlayer(String name, String world, double amount) {
    return depositPlayer(name, amount);
  }

  // --- banks: unsupported ----------------------------------------------------------------------

  @Override
  public EconomyResponse createBank(String name, OfflinePlayer player) {
    return noBanks();
  }

  @Override
  @Deprecated
  public EconomyResponse createBank(String name, String player) {
    return noBanks();
  }

  @Override
  public EconomyResponse deleteBank(String name) {
    return noBanks();
  }

  @Override
  public EconomyResponse bankBalance(String name) {
    return noBanks();
  }

  @Override
  public EconomyResponse bankHas(String name, double amount) {
    return noBanks();
  }

  @Override
  public EconomyResponse bankWithdraw(String name, double amount) {
    return noBanks();
  }

  @Override
  public EconomyResponse bankDeposit(String name, double amount) {
    return noBanks();
  }

  @Override
  public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
    return noBanks();
  }

  @Override
  @Deprecated
  public EconomyResponse isBankOwner(String name, String player) {
    return noBanks();
  }

  @Override
  public EconomyResponse isBankMember(String name, OfflinePlayer player) {
    return noBanks();
  }

  @Override
  @Deprecated
  public EconomyResponse isBankMember(String name, String player) {
    return noBanks();
  }

  @Override
  public List<String> getBanks() {
    return List.of();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private Optional<Long> balanceOf(UUID uuid) {
    // Online players answer at once; offline ones block on the database, as Vault requires.
    return economy.getBalanceOffline(uuid).join();
  }

  /** Whole dollars, rounded down; -1 for a negative or non-finite amount. */
  static long whole(double amount) {
    if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
      return -1;
    }
    return (long) Math.floor(Math.min(amount, EconomyService.MAX_AMOUNT));
  }

  @SuppressWarnings("deprecation")
  private static OfflinePlayer byName(String name) {
    return Bukkit.getOfflinePlayer(name);
  }

  private static EconomyResponse success(long amount, double balance) {
    return new EconomyResponse(amount, balance, ResponseType.SUCCESS, null);
  }

  private static EconomyResponse failure(double amount, double balance, @Nullable String why) {
    return new EconomyResponse(amount, balance, ResponseType.FAILURE, why);
  }

  private static EconomyResponse noBanks() {
    return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Ascent has no banks");
  }
}
