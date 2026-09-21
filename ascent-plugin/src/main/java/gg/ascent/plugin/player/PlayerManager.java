package gg.ascent.plugin.player;

import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * The player cache and its persistence rules (PRD E1-S3), independent of Bukkit so the rules can be
 * unit tested. {@link PlayerListener} feeds it the login, join and quit events.
 *
 * <p>Lifecycle of one player: {@link #preLogin} loads or creates the row on the login thread and
 * parks it as pending; {@link #join} promotes it to online on the main thread; {@link #quit} writes
 * it and forgets it. {@link #autosave} runs every interval, {@link #flushAll} at shutdown.
 */
public final class PlayerManager implements PlayerService {

  /** A pending login that never joined is forgotten after this long. */
  static final Duration PENDING_TTL = Duration.ofMinutes(2);

  private final DbExecutor db;
  private final PlayerRepository repo;
  private final long startingBalance;
  private final Clock clock;
  private final Logger log;

  /** Loaded during the login handshake, not yet joined. Written from the login thread. */
  private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

  /** Online players. Main thread only. */
  private final Map<UUID, Online> online = new HashMap<>();

  public PlayerManager(
      DbExecutor db, PlayerRepository repo, long startingBalance, Clock clock, Logger log) {
    this.db = db;
    this.repo = repo;
    this.startingBalance = startingBalance;
    this.clock = clock;
    this.log = log;
  }

  /**
   * Loads the row, creating it for a first join. Runs on the login thread and blocks it, which is
   * what that thread is for.
   *
   * @throws gg.ascent.api.db.DbException when the database fails; the caller kicks the player
   */
  public void preLogin(UUID uuid, String name) {
    Instant now = clock.instant();
    PlayerSnapshot row =
        db.supplyNow(
            c -> {
              Optional<PlayerSnapshot> existing = repo.find(c, uuid);
              if (existing.isPresent()) {
                return existing.get();
              }
              PlayerSnapshot fresh = PlayerSnapshot.fresh(uuid, name, startingBalance, now);
              repo.insert(c, fresh);
              log.info("Created player row for {} ({}).", name, uuid);
              return fresh;
            });
    pending.put(uuid, new Pending(new PlayerProfile(row), now));
  }

  /** Forgets a pending login that was refused after {@link #preLogin} ran. */
  public void loginRefused(UUID uuid) {
    pending.remove(uuid);
  }

  /**
   * Promotes a pending profile to online. Main thread.
   *
   * @return the profile, or empty if no pre-login load happened (the caller kicks the player)
   */
  public Optional<PlayerProfile> join(UUID uuid, String name) {
    Pending loaded = pending.remove(uuid);
    if (loaded == null) {
      return Optional.empty();
    }
    PlayerProfile profile = loaded.profile();
    Instant now = clock.instant();
    profile.setName(name);
    profile.setLastSeen(now);
    Online entry = new Online(profile, now);
    online.put(uuid, entry);
    Futures.logFailure(
        db.supply(c -> repo.openSession(c, uuid, now)).thenAccept(entry::setSessionId),
        log,
        "opening session for " + name);
    return Optional.of(profile);
  }

  /** Writes the profile, closes the session and forgets the player. Main thread. */
  public CompletableFuture<Void> quit(UUID uuid) {
    Online entry = online.remove(uuid);
    if (entry == null) {
      return CompletableFuture.completedFuture(null);
    }
    Instant now = clock.instant();
    PlayerProfile profile = entry.profile();
    profile.addPlaySeconds(Duration.between(entry.joinedAt(), now).toSeconds());
    profile.setLastSeen(now);
    PlayerSnapshot row = profile.snapshot();
    profile.clearDirty();
    Long sessionId = entry.sessionId();
    return Futures.logFailure(
        db.run(
            c -> {
              repo.update(c, row);
              if (sessionId != null) {
                repo.closeSession(c, sessionId, now);
              }
            }),
        log,
        "saving " + row.name() + " on quit");
  }

  /** Writes every dirty profile and forgets pending logins that never joined. Main thread. */
  public CompletableFuture<Void> autosave() {
    Instant cutoff = clock.instant().minus(PENDING_TTL);
    pending.entrySet().removeIf(e -> e.getValue().loadedAt().isBefore(cutoff));
    return saveDirty();
  }

  @Override
  public CompletableFuture<Void> saveDirty() {
    List<PlayerProfile> dirty = new ArrayList<>();
    for (Online entry : online.values()) {
      if (entry.profile().isDirty()) {
        dirty.add(entry.profile());
      }
    }
    if (dirty.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    List<PlayerSnapshot> rows = new ArrayList<>(dirty.size());
    for (PlayerProfile profile : dirty) {
      rows.add(profile.snapshot());
      profile.clearDirty();
    }
    CompletableFuture<Void> saved =
        db.transaction(
            c -> {
              for (PlayerSnapshot row : rows) {
                repo.update(c, row);
              }
              return null;
            });
    saved.whenComplete(
        (v, error) -> {
          if (error != null) {
            // Nothing was written; keep the changes for the next attempt.
            dirty.forEach(PlayerProfile::markDirty);
            log.error(
                "Autosave of {} profile(s) failed; will retry next interval",
                rows.size(),
                Futures.unwrap(error));
          }
        });
    return saved;
  }

  /**
   * Writes every online profile and closes every session, blocking the caller. For shutdown, when
   * no more ticks will run and the executor is draining.
   */
  public void flushAll() {
    if (online.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    List<PlayerSnapshot> rows = new ArrayList<>(online.size());
    List<Long> sessions = new ArrayList<>();
    for (Online entry : online.values()) {
      PlayerProfile profile = entry.profile();
      profile.addPlaySeconds(Duration.between(entry.joinedAt(), now).toSeconds());
      profile.setLastSeen(now);
      rows.add(profile.snapshot());
      profile.clearDirty();
      if (entry.sessionId() != null) {
        sessions.add(entry.sessionId());
      }
    }
    try {
      db.transactionNow(
          c -> {
            for (PlayerSnapshot row : rows) {
              repo.update(c, row);
            }
            for (long id : sessions) {
              repo.closeSession(c, id, now);
            }
            return null;
          });
      log.info("Saved {} player profile(s) at shutdown.", rows.size());
    } catch (RuntimeException e) {
      log.error(
          "Shutdown flush of {} profile(s) FAILED; progress since the last autosave" + " is lost.",
          rows.size(),
          e);
    }
    online.clear();
  }

  @Override
  public Optional<PlayerProfile> profile(UUID player) {
    Online entry = online.get(player);
    return entry == null ? Optional.empty() : Optional.of(entry.profile());
  }

  @Override
  public PlayerProfile require(UUID player) {
    return profile(player)
        .orElseThrow(() -> new IllegalStateException("player " + player + " is not online"));
  }

  @Override
  public Collection<PlayerProfile> online() {
    List<PlayerProfile> out = new ArrayList<>(online.size());
    for (Online entry : online.values()) {
      out.add(entry.profile());
    }
    return Collections.unmodifiableList(out);
  }

  @Override
  public CompletableFuture<Optional<PlayerSnapshot>> lookup(UUID player) {
    Optional<PlayerProfile> live = profile(player);
    if (live.isPresent()) {
      return CompletableFuture.completedFuture(Optional.of(live.get().snapshot()));
    }
    return db.supply(c -> repo.find(c, player));
  }

  @Override
  public CompletableFuture<Optional<PlayerSnapshot>> lookupByName(String name) {
    String wanted = name.toLowerCase(Locale.ROOT);
    for (Online entry : online.values()) {
      if (entry.profile().name().toLowerCase(Locale.ROOT).equals(wanted)) {
        return CompletableFuture.completedFuture(Optional.of(entry.profile().snapshot()));
      }
    }
    return db.supply(c -> repo.findByName(c, name));
  }

  /** How many logins are loaded but not yet joined. For diagnostics. */
  public int pendingCount() {
    return pending.size();
  }

  private record Pending(PlayerProfile profile, Instant loadedAt) {}

  private static final class Online {
    private final PlayerProfile profile;
    private final Instant joinedAt;
    private volatile Long sessionId;

    Online(PlayerProfile profile, Instant joinedAt) {
      this.profile = profile;
      this.joinedAt = joinedAt;
    }

    PlayerProfile profile() {
      return profile;
    }

    Instant joinedAt() {
      return joinedAt;
    }

    Long sessionId() {
      return sessionId;
    }

    void setSessionId(Long id) {
      this.sessionId = id;
    }
  }
}
