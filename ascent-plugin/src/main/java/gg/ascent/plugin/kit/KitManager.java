package gg.ascent.plugin.kit;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.KitsSettings;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.kit.KitService;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.rank.UnlockService;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/**
 * {@link KitService}: rank gates from {@link UnlockService}, cooldowns cached per online player and
 * written through to {@code kit_cooldowns} (PRD E2-S3). Bukkit-free so the rules can be unit
 * tested; {@link KitGiver} hands out the items.
 */
public final class KitManager implements KitService {

  private final ConfigService config;
  private final PlayerService players;
  private final UnlockService unlocks;
  private final DbExecutor db;
  private final KitCooldownRepository repo;
  private final KitGiver giver;
  private final Clock clock;
  private final Logger log;

  /** Online players' cooldowns. Main thread only. Absent until the join-time load completes. */
  private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();

  public KitManager(
      ConfigService config,
      PlayerService players,
      UnlockService unlocks,
      DbExecutor db,
      KitCooldownRepository repo,
      KitGiver giver,
      Clock clock,
      Logger log) {
    this.config = config;
    this.players = players;
    this.unlocks = unlocks;
    this.db = db;
    this.repo = repo;
    this.giver = giver;
    this.clock = clock;
    this.log = log;
  }

  /** Loads the player's cooldowns; call on join. Completes on the main thread. */
  public CompletableFuture<Void> playerJoined(UUID player) {
    return Futures.logFailure(
        db.supply(c -> repo.load(c, player))
            .thenAccept(
                loaded -> {
                  if (players.profile(player).isPresent()) {
                    cooldowns.put(player, new HashMap<>(loaded));
                  }
                }),
        log,
        "loading kit cooldowns for " + player);
  }

  /** Forgets the player's cooldowns; call on quit. */
  public void playerQuit(UUID player) {
    cooldowns.remove(player);
  }

  @Override
  public List<KitStatus> kits(UUID player) {
    int rank = players.require(player).rank();
    Map<String, Instant> mine = cooldowns.getOrDefault(player, Map.of());
    Instant now = clock.instant();
    List<KitStatus> out = new ArrayList<>();
    for (KitsSettings.Kit kit : config.kits().byRank()) {
      out.add(
          new KitStatus(
              kit.id(),
              kit.display(),
              kit.minRank(),
              kit.minRank() <= rank,
              remaining(mine.get(kit.id()), now)));
    }
    return out;
  }

  @Override
  public ClaimResult claim(UUID player, String kitId) {
    Optional<PlayerProfile> profile = players.profile(player);
    KitsSettings.Kit kit = config.kits().kits().get(kitId);
    if (kit == null || profile.isEmpty()) {
      return ClaimResult.UNKNOWN_KIT;
    }
    if (!unlocks.availableKits(profile.get().rank()).contains(kitId)) {
      return ClaimResult.LOCKED;
    }
    Map<String, Instant> mine = cooldowns.get(player);
    if (mine == null) {
      return ClaimResult.NOT_READY;
    }
    Instant now = clock.instant();
    if (!remaining(mine.get(kitId), now).isZero()) {
      return ClaimResult.ON_COOLDOWN;
    }
    Instant nextAt = now.plus(kit.cooldown());
    mine.put(kitId, nextAt);
    if (kitId.equals("starter") && !profile.get().starterKitClaimed()) {
      profile.get().setStarterKitClaimed(true);
    }
    giver.give(player, kit);
    Futures.logFailure(
        db.run(c -> repo.set(c, player, kitId, nextAt)),
        log,
        "saving kit cooldown " + kitId + " for " + profile.get().name());
    return ClaimResult.OK;
  }

  /** Time left until {@code nextAt}, or zero when it has passed or was never set. */
  static Duration remaining(Instant nextAt, Instant now) {
    if (nextAt == null || !nextAt.isAfter(now)) {
      return Duration.ZERO;
    }
    return Duration.between(now, nextAt);
  }

  /** Whether the player's cooldowns have loaded. For diagnostics. */
  public boolean isLoaded(UUID player) {
    return cooldowns.containsKey(player);
  }
}
