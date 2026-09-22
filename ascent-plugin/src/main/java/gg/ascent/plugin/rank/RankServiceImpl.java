package gg.ascent.plugin.rank;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.XpSource;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * {@link RankService} over live profiles (PRD E2-S1).
 *
 * <p>The rank and its progress live on the {@link PlayerProfile}, so the autosave persists them for
 * free. Each grant also lands in {@code xp_log}, aggregated per minute and source, without
 * blocking.
 */
public final class RankServiceImpl implements RankService {

  private final PlayerService players;
  private final ConfigService config;
  private final DbExecutor db;
  private final XpLogRepository xpLog;
  private final RankUpNotifier notifier;
  private final Clock clock;
  private final Logger log;

  public RankServiceImpl(
      PlayerService players,
      ConfigService config,
      DbExecutor db,
      XpLogRepository xpLog,
      RankUpNotifier notifier,
      Clock clock,
      Logger log) {
    this.players = players;
    this.config = config;
    this.db = db;
    this.xpLog = xpLog;
    this.notifier = notifier;
    this.clock = clock;
    this.log = log;
  }

  private RanksSettings.XpCurve curve() {
    return config.ranks().xpCurve();
  }

  @Override
  public int getRank(UUID player) {
    return players.require(player).rank();
  }

  @Override
  public long getXp(UUID player) {
    return players.require(player).xp();
  }

  @Override
  public long xpToNext(int rank) {
    return rank >= maxRank() ? 0 : curve().xpToNext(rank);
  }

  @Override
  public int maxRank() {
    return curve().maxRank();
  }

  @Override
  public void addXp(UUID player, XpSource source, long amount) {
    if (amount <= 0) {
      return;
    }
    Optional<PlayerProfile> found = players.profile(player);
    if (found.isEmpty()) {
      log.debug("Ignoring {} XP from {} for offline player {}", amount, source, player);
      return;
    }
    PlayerProfile profile = found.get();
    int max = maxRank();
    int before = profile.rank();
    profile.setXpTotal(profile.xpTotal() + amount);

    if (before >= max) {
      profile.setXpBanked(profile.xpBanked() + amount);
    } else {
      long xp = profile.xp() + amount;
      int rank = before;
      while (rank < max && xp >= curve().xpToNext(rank)) {
        xp -= curve().xpToNext(rank);
        rank++;
      }
      if (rank >= max) {
        // PRD E2-S1: rank 100 caps; anything past the last threshold is banked for prestige.
        profile.setXpBanked(profile.xpBanked() + xp);
        xp = 0;
      }
      profile.setXp(xp);
      if (rank != before) {
        profile.setRank(rank);
        notifier.rankedUp(profile, before, rank);
      }
    }

    Instant now = clock.instant();
    Futures.logFailure(
        db.run(c -> xpLog.add(c, player, source, amount, now)),
        log,
        "logging " + amount + " XP from " + source);
  }

  @Override
  public Progress progress(UUID player) {
    PlayerProfile profile = players.require(player);
    return new Progress(profile.rank(), profile.xp(), xpToNext(profile.rank()), profile.xpBanked());
  }
}
