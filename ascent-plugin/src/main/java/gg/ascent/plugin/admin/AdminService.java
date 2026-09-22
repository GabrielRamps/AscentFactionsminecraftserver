package gg.ascent.plugin.admin;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.XpSource;
import java.util.LinkedHashMap;
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
  private final RankService ranks;
  private final EnchantService enchants;
  private final ItemGiver giver;
  private final ConfigService config;
  private final AdminActionLog log;

  /** Puts an item stack in an online player's inventory, dropping what does not fit. */
  @FunctionalInterface
  public interface ItemGiver {
    void give(UUID player, org.bukkit.inventory.ItemStack stack);
  }

  public AdminService(
      PlayerService players,
      EconomyService economy,
      RankService ranks,
      EnchantService enchants,
      ItemGiver giver,
      ConfigService config,
      AdminActionLog log) {
    this.players = players;
    this.economy = economy;
    this.ranks = ranks;
    this.enchants = enchants;
    this.giver = giver;
    this.config = config;
    this.log = log;
  }

  /** {@code /ascent give <player> books <tier> [amount]}: unopened books to an online player. */
  public Result giveBooks(UUID actor, String targetName, Tier tier, int amount) {
    if (amount < 1 || amount > 64) {
      return Result.of(Outcome.BAD_AMOUNT);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    PlayerProfile profile = target.get();
    giver.give(profile.uuid(), enchants.createUnopenedBook(tier, amount, actor, "admin"));
    log.record(actor, "give.books", profile.name(), args("tier", tier.name(), "amount", amount));
    return new Result(Outcome.OK, amount);
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

  /** {@code /ascent give <player> scrolls [amount]}: White Scrolls to an online player. */
  public Result giveScrolls(UUID actor, String targetName, int amount) {
    if (amount < 1 || amount > 64) {
      return Result.of(Outcome.BAD_AMOUNT);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    giver.give(target.get().uuid(), enchants.createWhiteScroll(amount, actor, "admin"));
    log.record(actor, "give.scrolls", target.get().name(), Map.of("amount", amount));
    return new Result(Outcome.OK, amount);
  }

  /**
   * {@code /ascent give <player> dust <tier> <percent> [amount]}: Magic Dust to an online player.
   */
  public Result giveDust(UUID actor, String targetName, Tier tier, int percent, int amount) {
    if (amount < 1 || amount > 64 || percent < 1 || percent > 15) {
      return Result.of(Outcome.BAD_AMOUNT);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    giver.give(
        target.get().uuid(), enchants.createMagicDust(tier, percent, amount, actor, "admin"));
    log.record(
        actor, "give.dust", target.get().name(), args("tier", tier.name(), "percent", percent));
    return new Result(Outcome.OK, amount);
  }

  /** {@code /ascent give <player> xp <amount>}: rank XP through the ladder, rank-ups included. */
  public Result giveXp(UUID actor, String targetName, long amount) {
    if (amount < 1 || amount > 1_000_000_000L) {
      return Result.of(Outcome.BAD_AMOUNT);
    }
    Optional<PlayerProfile> target = onlineByName(targetName);
    if (target.isEmpty()) {
      return Result.of(Outcome.NOT_ONLINE);
    }
    PlayerProfile profile = target.get();
    ranks.addXp(profile.uuid(), XpSource.ADMIN, amount);
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
    log.record(actor, "rank.set", profile.name(), args("from", before, "to", rank));
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
    log.record(actor, "rank.add", profile.name(), args("delta", delta, "to", after));
    return new Result(Outcome.OK, after);
  }

  /** Two arguments in the order given, so the stored JSON reads the same every time. */
  private static Map<String, Object> args(String k1, Object v1, String k2, Object v2) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(k1, v1);
    out.put(k2, v2);
    return out;
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
