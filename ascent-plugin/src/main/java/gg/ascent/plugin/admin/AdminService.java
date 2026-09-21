package gg.ascent.plugin.admin;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.player.PlayerSnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the staff commands do, with Bukkit kept out so it can be unit tested (PRD E1-S6). The
 * command layer parses, calls in here, and turns the {@link Outcome} into a message.
 */
public final class AdminService {

  /** How an action ended. */
  public enum Outcome {
    OK,
    UNKNOWN_PLAYER,
    NOT_ONLINE,
    BAD_AMOUNT,
    BAD_RANK,
    NOT_AVAILABLE_YET
  }

  /** The result and, on success, the value the target now has. */
  public record Result(Outcome outcome, long value) {
    static Result of(Outcome outcome) {
      return new Result(outcome, 0);
    }
  }

  private final PlayerService players;
  private final EconomyService economy;
  private final ConfigService config;
  private final AdminActionLog log;

  public AdminService(
      PlayerService players, EconomyService economy, ConfigService config, AdminActionLog log) {
    this.players = players;
    this.economy = economy;
    this.config = config;
    this.log = log;
  }

  /** {@code /ascent give <player> money <amount>}: works for offline players too. */
  public CompletableFuture<Result> giveMoney(UUID actor, String targetName, long amount) {
    if (!EconomyService.isValidAmount(amount)) {
      return CompletableFuture.completedFuture(Result.of(Outcome.BAD_AMOUNT));
    }
    return players
        .lookupByName(targetName)
        .thenCompose(
            found -> {
              if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Result.of(Outcome.UNKNOWN_PLAYER));
              }
              PlayerSnapshot target = found.get();
              return economy
                  .depositOffline(target.uuid(), amount, TxReason.ADMIN, "give:" + actor)
                  .thenCompose(
                      ok -> {
                        if (!ok) {
                          return CompletableFuture.completedFuture(
                              Result.of(Outcome.UNKNOWN_PLAYER));
                        }
                        log.record(actor, "give.money", target.name(), Map.of("amount", amount));
                        return economy
                            .getBalanceOffline(target.uuid())
                            .thenApply(b -> new Result(Outcome.OK, b.orElse(0L)));
                      });
            });
  }

  /**
   * {@code /ascent give <player> xp <amount>}: adds rank XP to an online player's profile. Until
   * E2-S1 lands a rank service, this adds to the counters without rank-ups.
   */
  public Result giveXp(UUID actor, String targetName, long amount) {
    if (amount < 1 || amount > 1_000_000_000L) {
      return Result.of(Outcome.BAD_AMOUNT);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    PlayerProfile profile = target.get();
    profile.setXp(profile.xp() + amount);
    profile.setXpTotal(profile.xpTotal() + amount);
    log.record(actor, "give.xp", profile.name(), Map.of("amount", amount));
    return new Result(Outcome.OK, profile.xp());
  }

  /** {@code /ascent rank set <player> <rank>}: online players only; clamped to the ladder. */
  public Result rankSet(UUID actor, String targetName, int rank) {
    int max = config.ranks().xpCurve().maxRank();
    if (rank < 1 || rank > max) {
      return Result.of(Outcome.BAD_RANK);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    PlayerProfile profile = target.get();
    int before = profile.rank();
    profile.setRank(rank);
    profile.setXp(0);
    log.record(actor, "rank.set", profile.name(), Map.of("from", before, "to", rank));
    return new Result(Outcome.OK, rank);
  }

  /** {@code /ascent rank add <player> <delta>}: may be negative; clamped to the ladder. */
  public Result rankAdd(UUID actor, String targetName, int delta) {
    if (delta == 0) {
      return Result.of(Outcome.BAD_RANK);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    PlayerProfile profile = target.get();
    int max = config.ranks().xpCurve().maxRank();
    int before = profile.rank();
    int after = Math.max(1, Math.min(max, before + delta));
    profile.setRank(after);
    profile.setXp(0);
    log.record(actor, "rank.add", profile.name(), Map.of("delta", delta, "to", after));
    return new Result(Outcome.OK, after);
  }

  private Optional<PlayerProfile> onlineByName(String name) {
    for (PlayerProfile profile : players.online()) {
      if (profile.name().equalsIgnoreCase(name)) {
        return Optional.of(profile);
      }
    }
    return Optional.empty();
  }
}
