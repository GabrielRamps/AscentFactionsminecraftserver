package gg.ascent.plugin.command;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.db.DbStats;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.AscentPlugin;
import gg.ascent.plugin.admin.AdminActionLog;
import gg.ascent.plugin.admin.AdminService;
import gg.ascent.plugin.admin.AdminService.Result;
import gg.ascent.plugin.economy.Money;
import gg.ascent.plugin.item.DupeScanTask;
import gg.ascent.plugin.item.ItemAudit;
import gg.ascent.plugin.util.Futures;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The permissioned {@code /ascent} tree (PRD E1-S6, §6.4.1):
 *
 * <pre>
 * /ascent version
 * /ascent reload
 * /ascent give &lt;player&gt; &lt;money|xp|books|dust|scrolls|spawners&gt; [args]
 * /ascent rank &lt;set|add&gt; &lt;player&gt; &lt;value&gt;
 * /ascent debug &lt;tps|db|items&gt;
 * </pre>
 *
 * <p>{@code ascent.admin} guards the whole tree (plugin.yml; LuckPerms grants it). Every action is
 * written to {@code admin_actions}. Every string comes from {@code messages.yml}. Later stories add
 * {@code item lookup} (E1-S5) and {@code rollback} here.
 */
public final class AscentCommand implements CommandExecutor, TabCompleter {

  private static final List<String> SUBCOMMANDS =
      List.of("version", "reload", "give", "rank", "debug", "item");
  private static final List<String> GIVE_KINDS =
      List.of("money", "xp", "books", "dust", "scrolls", "spawners");
  private static final List<String> RANK_OPS = List.of("set", "add");
  private static final List<String> DEBUG_TOPICS = List.of("tps", "db", "items");
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final AscentPlugin plugin;
  private final ConfigService config;
  private final Messages messages;
  private final AdminService admin;
  private final AdminActionLog actionLog;
  private final Logger log;

  public AscentCommand(
      AscentPlugin plugin,
      ConfigService config,
      Messages messages,
      AdminService admin,
      AdminActionLog actionLog,
      Logger log) {
    this.plugin = plugin;
    this.config = config;
    this.messages = messages;
    this.admin = admin;
    this.actionLog = actionLog;
    this.log = log;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "version" -> version(sender);
      case "reload" -> reload(sender);
      case "give" -> give(sender, args);
      case "rank" -> rank(sender, args);
      case "debug" -> debug(sender, args);
      case "item" -> item(sender, args);
      default -> messages.send(sender, "ascent.usage");
    }
    return true;
  }

  private void version(CommandSender sender) {
    messages.send(
        sender,
        "ascent.version",
        Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
  }

  private void reload(CommandSender sender) {
    ReloadReport report = config.reload();
    actionLog.record(actorOf(sender), "reload", null, Map.of("failures", report.failures().size()));
    if (report.allOk()) {
      messages.send(
          sender,
          "ascent.reload.ok",
          Placeholder.unparsed("count", String.valueOf(report.files().size())));
      return;
    }
    messages.send(
        sender,
        "ascent.reload.failed",
        Placeholder.unparsed("count", String.valueOf(report.failures().size())));
    for (ReloadReport.FileResult failure : report.failures()) {
      messages.send(sender, "ascent.reload.error", Placeholder.unparsed("error", failure.error()));
    }
  }

  private void give(CommandSender sender, String[] args) {
    if (args.length < 3) {
      messages.send(sender, "ascent.give.usage");
      return;
    }
    String target = args[1];
    String kind = args[2].toLowerCase(Locale.ROOT);
    UUID actor = actorOf(sender);
    switch (kind) {
      case "money" -> {
        OptionalLong amount = args.length >= 4 ? Money.parse(args[3]) : OptionalLong.empty();
        if (amount.isEmpty()) {
          messages.send(sender, "ascent.give.bad-amount");
          return;
        }
        Futures.logFailure(
            admin
                .giveMoney(actor, target, amount.getAsLong())
                .thenAccept(
                    result ->
                        report(
                            sender,
                            result,
                            "ascent.give.money",
                            player(target),
                            Placeholder.unparsed("amount", Money.format(amount.getAsLong())),
                            Placeholder.unparsed("balance", Money.format(result.value())))),
            log,
            "/ascent give money");
      }
      case "xp" -> {
        long amount = args.length >= 4 ? parseLong(args[3]) : -1;
        if (amount < 1) {
          messages.send(sender, "ascent.give.bad-amount");
          return;
        }
        Result result = admin.giveXp(actor, target, amount);
        report(
            sender,
            result,
            "ascent.give.xp",
            player(target),
            Placeholder.unparsed("amount", String.valueOf(amount)),
            Placeholder.unparsed("xp", String.valueOf(result.value())));
      }
      case "books", "dust", "scrolls", "spawners" ->
          messages.send(sender, "ascent.give.not-yet", Placeholder.unparsed("kind", kind));
      default -> messages.send(sender, "ascent.give.usage");
    }
  }

  private void rank(CommandSender sender, String[] args) {
    if (args.length != 4) {
      messages.send(sender, "ascent.rank.usage");
      return;
    }
    String op = args[1].toLowerCase(Locale.ROOT);
    String target = args[2];
    long value = parseLong(args[3]);
    if (value == Long.MIN_VALUE || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
      messages.send(sender, "ascent.rank.usage");
      return;
    }
    UUID actor = actorOf(sender);
    Result result =
        switch (op) {
          case "set" -> admin.rankSet(actor, target, (int) value);
          case "add" -> admin.rankAdd(actor, target, (int) value);
          default -> null;
        };
    if (result == null) {
      messages.send(sender, "ascent.rank.usage");
      return;
    }
    report(
        sender,
        result,
        "ascent.rank.ok",
        player(target),
        Placeholder.unparsed("rank", String.valueOf(result.value())),
        Placeholder.unparsed("max", String.valueOf(config.ranks().xpCurve().maxRank())));
  }

  private void debug(CommandSender sender, String[] args) {
    String topic = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
    switch (topic) {
      case "tps" -> {
        double[] tps = Bukkit.getTPS();
        messages.send(
            sender,
            "ascent.debug.tps",
            Placeholder.unparsed("tps1", tps(tps, 0)),
            Placeholder.unparsed("tps5", tps(tps, 1)),
            Placeholder.unparsed("tps15", tps(tps, 2)),
            Placeholder.unparsed(
                "mspt", String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime())));
      }
      case "db" -> {
        DbStats stats = plugin.database().executor().stats();
        messages.send(
            sender,
            "ascent.debug.db",
            Placeholder.unparsed("active", String.valueOf(stats.poolActive())),
            Placeholder.unparsed("idle", String.valueOf(stats.poolIdle())),
            Placeholder.unparsed("total", String.valueOf(stats.poolTotal())),
            Placeholder.unparsed("waiting", String.valueOf(stats.poolWaiting())),
            Placeholder.unparsed("queued", String.valueOf(stats.queuedTasks())),
            Placeholder.unparsed("completed", String.valueOf(stats.completedTasks())),
            Placeholder.unparsed("failed", String.valueOf(stats.failedTasks())),
            Placeholder.unparsed(
                "redis", plugin.redis().isAvailable() ? "connected" : "unavailable"),
            Placeholder.unparsed("pending", String.valueOf(plugin.players().pendingCount())),
            Placeholder.unparsed("online", String.valueOf(plugin.players().online().size())));
        Futures.logFailure(
            plugin
                .database()
                .executor()
                .supply(
                    c -> {
                      long start = System.nanoTime();
                      c.isValid(2);
                      return (System.nanoTime() - start) / 1_000_000;
                    })
                .thenAccept(
                    ms ->
                        messages.send(
                            sender,
                            "ascent.debug.db-ping",
                            Placeholder.unparsed("ms", ms.toString()))),
            log,
            "/ascent debug db");
      }
      case "items" -> {
        ItemAudit audit = plugin.items().audit();
        DupeScanTask scan = plugin.dupeScan();
        Futures.logFailure(
            audit
                .openAlertCount()
                .thenAccept(
                    open ->
                        messages.send(
                            sender,
                            "ascent.debug.items",
                            Placeholder.unparsed("tagged", String.valueOf(audit.taggedCount())),
                            Placeholder.unparsed("events", String.valueOf(audit.recordedCount())),
                            Placeholder.unparsed("seen", String.valueOf(scan.lastSeen())),
                            Placeholder.unparsed("findings", String.valueOf(scan.lastFindings())),
                            Placeholder.unparsed("open", open.toString()),
                            Placeholder.unparsed(
                                "discord",
                                plugin.alerts().discordEnabled() ? "configured" : "off"))),
            log,
            "/ascent debug items");
      }
      default -> messages.send(sender, "ascent.debug.usage");
    }
  }

  /** {@code /ascent item lookup <uuid>}: the item's row and its full history (PRD E1-S5). */
  private void item(CommandSender sender, String[] args) {
    if (args.length != 3 || !args[1].equalsIgnoreCase("lookup")) {
      messages.send(sender, "ascent.item.usage");
      return;
    }
    UUID itemId;
    try {
      itemId = UUID.fromString(args[2]);
    } catch (IllegalArgumentException e) {
      messages.send(sender, "ascent.item.bad-id");
      return;
    }
    actionLog.record(actorOf(sender), "item.lookup", itemId.toString(), null);
    ItemRegistry registry = plugin.items();
    Futures.logFailure(
        registry
            .lookup(itemId)
            .thenCompose(
                found -> {
                  if (found.isEmpty()) {
                    messages.send(
                        sender,
                        "ascent.item.unknown",
                        Placeholder.unparsed("item", itemId.toString()));
                    return CompletableFuture.<Void>completedFuture(null);
                  }
                  ItemRecord item = found.get();
                  messages.send(
                      sender,
                      "ascent.item.header",
                      Placeholder.unparsed("item", item.itemId().toString()),
                      Placeholder.unparsed("kind", item.kind().name()),
                      Placeholder.unparsed("quantity", String.valueOf(item.quantityCreated())),
                      Placeholder.unparsed("creator", nameOf(item.createdBy())),
                      Placeholder.unparsed("reason", item.createdReason()),
                      Placeholder.unparsed("created", TIME.format(item.createdAt())),
                      Placeholder.unparsed(
                          "destroyed",
                          item.destroyedAt() == null ? "no" : TIME.format(item.destroyedAt())));
                  if (!"{}".equals(item.data())) {
                    messages.send(
                        sender, "ascent.item.data", Placeholder.unparsed("data", item.data()));
                  }
                  return registry.history(itemId).thenAccept(events -> printEvents(sender, events));
                }),
        log,
        "/ascent item lookup " + itemId);
  }

  private void printEvents(CommandSender sender, List<ItemEventRecord> events) {
    if (events.isEmpty()) {
      messages.send(sender, "ascent.item.none");
      return;
    }
    for (ItemEventRecord e : events) {
      Component with =
          e.counterparty() == null
              ? Component.empty()
              : messages.render(
                  "ascent.item.with",
                  Placeholder.unparsed("counterparty", nameOf(e.counterparty())));
      messages.send(
          sender,
          "ascent.item.event",
          Placeholder.unparsed("time", TIME.format(e.createdAt())),
          Placeholder.unparsed("event", e.event().name()),
          Placeholder.unparsed("actor", nameOf(e.actor())),
          Placeholder.component("counterparty", with),
          Placeholder.unparsed("context", e.context() == null ? "" : e.context()));
    }
  }

  /** A player's last known name, from the server's cache; the UUID when it has none. */
  private static String nameOf(@Nullable UUID uuid) {
    if (uuid == null) {
      return "server";
    }
    if (AdminActionLog.CONSOLE.equals(uuid)) {
      return "console";
    }
    String name = Bukkit.getOfflinePlayer(uuid).getName();
    return name == null ? uuid.toString() : name;
  }

  private void report(CommandSender sender, Result result, String okKey, TagResolver... tags) {
    switch (result.outcome()) {
      case OK -> messages.send(sender, okKey, tags);
      case UNKNOWN_PLAYER -> messages.send(sender, "ascent.unknown-player", tags);
      case NOT_ONLINE -> messages.send(sender, "ascent.not-online", tags);
      case BAD_AMOUNT -> messages.send(sender, "ascent.give.bad-amount", tags);
      case BAD_RANK -> messages.send(sender, "ascent.rank.bad-rank", tags);
      case NOT_AVAILABLE_YET -> messages.send(sender, "ascent.give.not-yet", tags);
    }
  }

  private static TagResolver player(String name) {
    return Placeholder.unparsed("player", name);
  }

  private static String tps(double[] tps, int index) {
    double value = tps.length > index ? Math.min(20.0, tps[index]) : 0;
    return String.format(Locale.ROOT, "%.2f", value);
  }

  /** Long.MIN_VALUE when {@code text} is not a whole number. */
  private static long parseLong(String text) {
    try {
      return Long.parseLong(text.replace(",", "").replace("_", ""));
    } catch (NumberFormatException e) {
      return Long.MIN_VALUE;
    }
  }

  static UUID actorOf(CommandSender sender) {
    return sender instanceof Player p ? p.getUniqueId() : AdminActionLog.CONSOLE;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 1) {
      return filter(SUBCOMMANDS, args[0]);
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    return switch (sub) {
      case "give" ->
          switch (args.length) {
            case 2 -> onlineNames(args[1]);
            case 3 -> filter(GIVE_KINDS, args[2]);
            default -> List.of();
          };
      case "rank" ->
          switch (args.length) {
            case 2 -> filter(RANK_OPS, args[1]);
            case 3 -> onlineNames(args[2]);
            default -> List.of();
          };
      case "debug" -> args.length == 2 ? filter(DEBUG_TOPICS, args[1]) : List.of();
      case "item" -> args.length == 2 ? filter(List.of("lookup"), args[1]) : List.of();
      default -> List.of();
    };
  }

  private static List<String> filter(List<String> options, String typed) {
    String prefix = typed.toLowerCase(Locale.ROOT);
    return options.stream().filter(o -> o.startsWith(prefix)).toList();
  }

  private static List<String> onlineNames(String typed) {
    String prefix = typed.toLowerCase(Locale.ROOT);
    return Bukkit.getOnlinePlayers().stream()
        .map(Player::getName)
        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
        .sorted()
        .toList();
  }
}
