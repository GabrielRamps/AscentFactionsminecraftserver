package gg.ascent.api.kit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Rank-gated kits on a cooldown (PRD E2-S3). Contents live in {@code kits.yml}; cooldowns in the
 * {@code kit_cooldowns} table, so they survive restarts. Main thread; online players only.
 */
public interface KitService {

  /** Every kit as the player sees it, in rank order. */
  List<KitStatus> kits(UUID player);

  /**
   * Gives the kit if the player may have it now, and starts its cooldown.
   *
   * @return why not, or {@link ClaimResult#OK}
   */
  ClaimResult claim(UUID player, String kitId);

  /**
   * @param id the kit id
   * @param display MiniMessage display name
   * @param minRank the rank that unlocks it
   * @param unlocked whether the player's rank is high enough
   * @param cooldownLeft time until the player may claim it again; zero when ready
   */
  record KitStatus(
      String id, String display, int minRank, boolean unlocked, Duration cooldownLeft) {
    public boolean ready() {
      return unlocked && cooldownLeft.isZero();
    }
  }

  enum ClaimResult {
    OK,
    UNKNOWN_KIT,
    LOCKED,
    ON_COOLDOWN,
    /** The player's cooldowns have not finished loading from the database yet. */
    NOT_READY
  }
}
