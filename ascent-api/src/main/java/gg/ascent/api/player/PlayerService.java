package gg.ascent.api.player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Player data: loaded before the player joins, cached while online, saved reliably (PRD E1-S3).
 *
 * <p>Online players have a live {@link PlayerProfile}; every module reads and writes through it.
 * Offline players are reachable only as detached {@link PlayerSnapshot}s, which are a read-only
 * view: to change an offline player's row, a module goes through the service that owns that field
 * (the economy service for money, and so on).
 */
public interface PlayerService {

  /** The live profile of an online player, or empty if the player is not online. */
  Optional<PlayerProfile> profile(UUID player);

  /**
   * The live profile of an online player.
   *
   * @throws IllegalStateException if the player is not online
   */
  PlayerProfile require(UUID player);

  /** Every online player's profile. */
  Collection<PlayerProfile> online();

  /**
   * The current state of any known player, online or not. Online players answer from memory; others
   * from the database. The future completes on the main thread.
   */
  CompletableFuture<Optional<PlayerSnapshot>> lookup(UUID player);

  /** As {@link #lookup(UUID)} by last known name, matched case-insensitively. */
  CompletableFuture<Optional<PlayerSnapshot>> lookupByName(String name);

  /** Writes every dirty profile now, off the main thread. Returns when they are all written. */
  CompletableFuture<Void> saveDirty();
}
