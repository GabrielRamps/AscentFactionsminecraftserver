package gg.ascent.api.player;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One {@code players} row, immutable. What the repository reads and writes, and what an offline
 * lookup returns.
 *
 * @param uuid the player's Mojang UUID
 * @param name last known name
 * @param rank personal rank, 1 to the configured maximum
 * @param xp progress toward the next rank
 * @param xpTotal lifetime XP
 * @param xpBanked XP earned past the top rank, for prestige
 * @param balance whole dollars
 * @param power faction power contribution
 * @param powerUpdated when power was last recalculated
 * @param factionId the faction the player is in, or null
 * @param starterKitClaimed whether the one-time starter kit has been claimed
 * @param firstSeen first join
 * @param lastSeen last join or quit
 * @param playSeconds total time online
 */
public record PlayerSnapshot(
    UUID uuid,
    String name,
    int rank,
    long xp,
    long xpTotal,
    long xpBanked,
    long balance,
    double power,
    Instant powerUpdated,
    @Nullable Integer factionId,
    boolean starterKitClaimed,
    Instant firstSeen,
    Instant lastSeen,
    long playSeconds) {

  public PlayerSnapshot {
    if (uuid == null || name == null || powerUpdated == null || firstSeen == null) {
      throw new IllegalArgumentException("uuid, name, powerUpdated and firstSeen are required");
    }
    if (lastSeen == null) {
      lastSeen = firstSeen;
    }
    if (rank < 1) {
      throw new IllegalArgumentException("rank must be at least 1, got " + rank);
    }
    if (balance < 0) {
      throw new IllegalArgumentException("balance cannot be negative, got " + balance);
    }
  }

  /** A brand-new player's row (PRD E1-S3: rank 1, no XP, the starting balance, kit unclaimed). */
  public static PlayerSnapshot fresh(UUID uuid, String name, long startingBalance, Instant now) {
    return new PlayerSnapshot(
        uuid, name, 1, 0, 0, 0, startingBalance, 0.0, now, null, false, now, now, 0);
  }
}
