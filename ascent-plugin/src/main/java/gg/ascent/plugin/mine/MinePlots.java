package gg.ascent.plugin.mine;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.plugin.mine.MinePlotRepository.Row;
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
 * Who holds which plot, at what tier, and how mined it is (PRD E4-S1), without Bukkit. Rows are
 * cached in memory and written through; {@link MineWorld} does the building and teleporting.
 */
public final class MinePlots {

  /** A plot in memory. {@code lastReset} is not persisted: a restart refills every active plot. */
  public static final class Plot {
    final int index;
    UUID owner;
    int tier;
    Instant allocatedAt;
    Instant lastUsed;
    int mined;
    Instant lastReset;

    Plot(int index) {
      this.index = index;
    }

    public int index() {
      return index;
    }

    public UUID owner() {
      return owner;
    }

    public int tier() {
      return tier;
    }

    public int mined() {
      return mined;
    }

    public Instant lastReset() {
      return lastReset;
    }

    Row row() {
      return new Row(index, owner, tier, allocatedAt, lastUsed, mined);
    }
  }

  /** What {@link #claim} decided. */
  public enum Change {
    /** The player already held this plot at this tier. */
    UNCHANGED,
    /** The plot was newly allocated (fresh or reclaimed): build it all. */
    ALLOCATED,
    /** The player's rank now allows a higher tier: refill the pit. */
    UPGRADED
  }

  public record Claim(Plot plot, Change change) {}

  private final ConfigService config;
  private final DbExecutor db;
  private final MinePlotRepository repo;
  private final Clock clock;
  private final Logger log;
  private final Map<Integer, Plot> plots = new HashMap<>();
  private final Map<UUID, Plot> byOwner = new HashMap<>();

  public MinePlots(
      ConfigService config, DbExecutor db, MinePlotRepository repo, Clock clock, Logger log) {
    this.config = config;
    this.db = db;
    this.repo = repo;
    this.clock = clock;
    this.log = log;
  }

  /** Loads every row. Completes on the main thread; call once at startup. */
  public CompletableFuture<Void> load() {
    return Futures.logFailure(
        db.supply(repo::all)
            .thenAccept(
                rows -> {
                  plots.clear();
                  byOwner.clear();
                  for (Row row : rows) {
                    Plot plot = new Plot(row.index());
                    plot.owner = row.owner();
                    plot.tier = row.tier();
                    plot.allocatedAt = row.allocatedAt();
                    plot.lastUsed = row.lastUsed();
                    plot.mined = row.minedBlocks();
                    plots.put(plot.index, plot);
                    if (plot.owner != null) {
                      byOwner.put(plot.owner, plot);
                    }
                  }
                  log.info("Loaded {} mine plot(s).", plots.size());
                }),
        log,
        "loading mine plots");
  }

  /**
   * Gives {@code player} their plot at {@code tier}: the one they hold, or the lowest free or
   * expired plot. Marks it used now.
   */
  public Claim claim(UUID player, int tier) {
    Instant now = clock.instant();
    Plot mine = byOwner.get(player);
    if (mine != null) {
      mine.lastUsed = now;
      Change change = Change.UNCHANGED;
      if (tier > mine.tier) {
        mine.tier = tier;
        mine.mined = 0;
        mine.lastReset = now;
        change = Change.UPGRADED;
      }
      save(mine);
      return new Claim(mine, change);
    }
    Duration inactivity = config.mines().plotInactivity();
    for (int index = 0; ; index++) {
      Plot plot = plots.get(index);
      if (plot == null) {
        plot = new Plot(index);
        plots.put(index, plot);
      }
      boolean free =
          plot.owner == null
              || plot.lastUsed == null
              || Duration.between(plot.lastUsed, now).compareTo(inactivity) >= 0;
      if (!free) {
        continue;
      }
      if (plot.owner != null) {
        log.info("Releasing mine plot {} from {} after inactivity.", index, plot.owner);
        byOwner.remove(plot.owner);
      }
      plot.owner = player;
      plot.tier = tier;
      plot.allocatedAt = now;
      plot.lastUsed = now;
      plot.mined = 0;
      plot.lastReset = now;
      byOwner.put(player, plot);
      save(plot);
      return new Claim(plot, Change.ALLOCATED);
    }
  }

  public Optional<Plot> plotOf(UUID player) {
    return Optional.ofNullable(byOwner.get(player));
  }

  public Optional<Plot> plot(int index) {
    return Optional.ofNullable(plots.get(index));
  }

  /** Counts a mined block; the caller decides whether that triggers a reset. */
  public void mined(Plot plot) {
    plot.mined++;
    plot.lastUsed = clock.instant();
    if (plot.mined % 64 == 0) {
      save(plot); // every stack, not every block
    }
  }

  /** Whether the pit should be refilled: the timer ran out or enough was mined. */
  public boolean dueForReset(Plot plot) {
    Instant now = clock.instant();
    boolean timer =
        plot.lastReset == null
            || Duration.between(plot.lastReset, now).compareTo(config.mines().resetInterval()) >= 0;
    boolean mined =
        plot.mined >= Math.ceil(config.mines().pitVolume() * config.mines().resetMinedFraction());
    return timer || mined;
  }

  /** Records that the pit was refilled now. */
  public void wasReset(Plot plot) {
    plot.mined = 0;
    plot.lastReset = clock.instant();
    save(plot);
  }

  /** Plots with an owner, for the reset sweep. */
  public List<Plot> allocated() {
    return new ArrayList<>(byOwner.values());
  }

  private void save(Plot plot) {
    Row row = plot.row();
    Futures.logFailure(db.run(c -> repo.save(c, row)), log, "saving mine plot " + plot.index);
  }
}
