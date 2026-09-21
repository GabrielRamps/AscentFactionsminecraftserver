package gg.ascent.plugin.admin;

import com.google.gson.Gson;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Writes one {@code admin_actions} row per staff command, asynchronously, so the audit trail never
 * costs the command a tick (PRD E1-S6).
 */
public final class AdminActionLog {

  /** The actor recorded for the console, which has no UUID of its own. */
  public static final UUID CONSOLE = new UUID(0L, 0L);

  private static final Gson GSON = new Gson();

  private final DbExecutor db;
  private final AdminActionRepository repo;
  private final Clock clock;
  private final Logger log;

  public AdminActionLog(DbExecutor db, AdminActionRepository repo, Clock clock, Logger log) {
    this.db = db;
    this.repo = repo;
    this.clock = clock;
    this.log = log;
  }

  /**
   * Records an action.
   *
   * @param actor the staff member's UUID, or {@link #CONSOLE}
   * @param action a short verb path such as {@code give.money} or {@code rank.set}
   * @param target what it was done to, usually a player name
   * @param args the command's arguments, stored as JSON
   */
  public void record(
      UUID actor, String action, @Nullable String target, @Nullable Map<String, ?> args) {
    Instant now = clock.instant();
    String json = args == null || args.isEmpty() ? null : GSON.toJson(args);
    log.info(
        "[admin] {} {} {} {}",
        actor.equals(CONSOLE) ? "console" : actor,
        action,
        target == null ? "" : target,
        json == null ? "" : json);
    Futures.logFailure(
        db.run(c -> repo.record(c, actor, action, target, json, now)),
        log,
        "recording admin action " + action);
  }
}
