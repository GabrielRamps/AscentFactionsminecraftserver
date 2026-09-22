package gg.ascent.plugin.item;

import com.google.gson.Gson;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.plugin.alert.StaffAlerts;
import gg.ascent.plugin.item.DupeScanner.Finding;
import gg.ascent.plugin.item.DupeScanner.Holder;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Every few minutes: walk every online inventory and ender chest on the main thread, then off it
 * compare what was seen with what was created, write {@code dupe_alerts} rows and tell staff.
 */
public final class DupeScanTask implements Runnable {

  private static final Gson GSON = new Gson();

  private final Server server;
  private final DbExecutor db;
  private final ItemRepository repo;
  private final StaffAlerts alerts;
  private final Clock clock;
  private final Logger log;
  private volatile int lastSeen;
  private volatile int lastFindings;

  public DupeScanTask(
      Server server,
      DbExecutor db,
      ItemRepository repo,
      StaffAlerts alerts,
      Clock clock,
      Logger log) {
    this.server = server;
    this.db = db;
    this.repo = repo;
    this.alerts = alerts;
    this.clock = clock;
    this.log = log;
  }

  @Override
  public void run() {
    scan();
  }

  /** Runs one scan. Main thread; the database part completes later, also on the main thread. */
  public CompletableFuture<List<Finding>> scan() {
    Map<UUID, List<Holder>> observed = collect();
    lastSeen = observed.size();
    if (observed.isEmpty()) {
      lastFindings = 0;
      return CompletableFuture.completedFuture(List.of());
    }
    Instant now = clock.instant();
    return Futures.logFailure(
        db.transaction(
                c -> {
                  Map<UUID, Integer> created = repo.quantities(c, observed.keySet());
                  Set<UUID> open = repo.openAlerts(c, observed.keySet());
                  List<Finding> findings = DupeScanner.findDupes(observed, created, open);
                  for (Finding f : findings) {
                    repo.insertAlert(c, f.itemId(), f.observed(), GSON.toJson(f.holders()), now);
                  }
                  return findings;
                })
            .thenApply(
                findings -> {
                  lastFindings = findings.size();
                  for (Finding f : findings) {
                    alert(f);
                  }
                  return findings;
                }),
        log,
        "dupe scan");
  }

  private Map<UUID, List<Holder>> collect() {
    Map<UUID, List<Holder>> observed = new HashMap<>();
    for (Player player : server.getOnlinePlayers()) {
      walk(player, "inventory", player.getInventory(), observed);
      walk(player, "enderchest", player.getEnderChest(), observed);
      Optional<UUID> cursor = ItemKeys.idOf(player.getItemOnCursor());
      cursor.ifPresent(
          id -> add(observed, id, player, "cursor", player.getItemOnCursor().getAmount()));
    }
    return observed;
  }

  private static void walk(
      Player player, String where, Inventory inventory, Map<UUID, List<Holder>> observed) {
    for (ItemStack stack : inventory.getContents()) {
      if (stack == null) {
        continue;
      }
      ItemKeys.idOf(stack).ifPresent(id -> add(observed, id, player, where, stack.getAmount()));
    }
  }

  private static void add(
      Map<UUID, List<Holder>> observed, UUID id, Player player, String where, int count) {
    observed
        .computeIfAbsent(id, k -> new ArrayList<>())
        .add(new Holder(player.getUniqueId(), player.getName(), where, count));
  }

  private void alert(Finding f) {
    List<String> names = new ArrayList<>();
    for (Holder h : f.holders()) {
      names.add(h.name() + " x" + h.count());
    }
    String holders = String.join(", ", names);
    log.warn(
        "DUPE ALERT: item {} seen {} times, created {}; held by {}",
        f.itemId(),
        f.observed(),
        f.created(),
        holders);
    alerts.dupe(f.itemId(), f.observed(), f.created(), holders);
  }

  /** Distinct tagged ids seen in the last scan. */
  public int lastSeen() {
    return lastSeen;
  }

  /** Alerts raised by the last scan. */
  public int lastFindings() {
    return lastFindings;
  }
}
