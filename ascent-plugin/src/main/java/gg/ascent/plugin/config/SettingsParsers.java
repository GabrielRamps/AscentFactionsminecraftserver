package gg.ascent.plugin.config;

import gg.ascent.api.config.CombatSettings;
import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.ContractsSettings;
import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.EventsSettings;
import gg.ascent.api.config.FactionsSettings;
import gg.ascent.api.config.MinesSettings;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.config.SpawnersSettings;
import gg.ascent.api.contract.Archetype;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.progress.ObjectiveType;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Turns each configuration file into its settings record.
 *
 * <p>These are the only places YAML is read. Validation that spans several keys lives in the
 * records' compact constructors; the {@link IllegalArgumentException}s they throw are wrapped here
 * so the operator still sees the file name.
 */
public final class SettingsParsers {

  private SettingsParsers() {}

  /** Parses {@code config.yml} with the process environment supplying the credentials. */
  public static CoreSettings core(Node root) {
    return core(root, System::getenv);
  }

  /**
   * Parses {@code config.yml}.
   *
   * @param env resolves an environment variable to its value, or null when unset. Credentials come
   *     only from here; host, port and database name may be overridden from here.
   */
  public static CoreSettings core(Node root, Function<String, @Nullable String> env) {
    String zone = root.string("time-zone");
    ZoneId zoneId;
    try {
      zoneId = ZoneId.of(zone);
    } catch (DateTimeException e) {
      throw new ConfigException(root.at("time-zone") + ": unknown time zone '" + zone + "'");
    }
    Node db = root.section("database");
    CoreSettings.Database database =
        wrap(
            root,
            "database",
            () ->
                new CoreSettings.Database(
                    envOr(env, "MARIADB_HOST", db.string("host")),
                    envIntOr(env, "MARIADB_PORT", db.integerAtLeast("port", 1)),
                    envOr(env, "MARIADB_DATABASE", db.string("name")),
                    envOr(env, "MARIADB_USER", ""),
                    envOr(env, "MARIADB_PASSWORD", ""),
                    db.integerAtLeast("max-pool-size", 1),
                    db.duration("connect-timeout")));
    Node redis = root.section("redis");
    CoreSettings.Redis redisSettings =
        wrap(
            root,
            "redis",
            () ->
                new CoreSettings.Redis(
                    redis.bool("enabled"),
                    envOr(env, "REDIS_HOST", redis.string("host")),
                    envIntOr(env, "REDIS_PORT", redis.integerAtLeast("port", 1)),
                    envOr(env, "REDIS_PASSWORD", null)));
    Node players = root.section("players");
    CoreSettings.Players playerSettings =
        wrap(
            root,
            "players",
            () ->
                new CoreSettings.Players(
                    players.longAtLeast("starting-balance", 0),
                    players.duration("autosave-interval")));
    Node items = root.section("items");
    CoreSettings.Items itemSettings =
        wrap(root, "items", () -> new CoreSettings.Items(items.duration("dupe-scan-interval")));
    CoreSettings.Alerts alertSettings =
        new CoreSettings.Alerts(envOr(env, "DISCORD_WEBHOOK_URL", null));
    return new CoreSettings(
        root.bool("debug"),
        zoneId,
        database,
        redisSettings,
        playerSettings,
        itemSettings,
        alertSettings);
  }

  private static @Nullable String envOr(
      Function<String, @Nullable String> env, String key, @Nullable String fallback) {
    String value = env.apply(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static int envIntOr(Function<String, @Nullable String> env, String key, int fallback) {
    String value = env.apply(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new ConfigException(
          "environment variable " + key + ": expected a whole number, got \"" + value + "\"");
    }
  }

  public static RanksSettings ranks(Node root) {
    Node curve = root.section("xp-curve");
    RanksSettings.XpCurve xpCurve =
        new RanksSettings.XpCurve(
            curve.longAtLeast("base", 1),
            curve.decimalBetween("exponent", 0.1, 5.0),
            curve.integerAtLeast("max-rank", 1));

    Node sources = root.section("sources");
    Node mine = sources.section("mine-block");
    Map<Integer, Long> mineByTier = new HashMap<>();
    for (String key : mine.keys()) {
      mineByTier.put(tierNumber(mine, key), mine.longAtLeast(key, 0));
    }
    Node spawner = sources.section("spawner-kill");
    Map<String, Long> spawnerByMob = new HashMap<>();
    for (String key : spawner.keys()) {
      spawnerByMob.put(key.toUpperCase(Locale.ROOT), spawner.longAtLeast(key, 0));
    }
    Node pvp = sources.section("pvp-kill");
    RanksSettings.PvpKill pvpKill =
        new RanksSettings.PvpKill(
            pvp.longAtLeast("base", 0),
            pvp.integerAtLeast("repeat-victim-window-minutes", 0),
            pvp.decimalBetween("repeat-multiplier", 0.0, 1.0));
    return new RanksSettings(
        xpCurve,
        new RanksSettings.XpSources(
            mineByTier,
            spawnerByMob,
            pvpKill,
            sources.longAtLeast("koth-win", 0),
            sources.longAtLeast("envoy-crate", 0)));
  }

  public static EnchantsSettings enchants(Node root) {
    Node tiers = root.section("tiers");
    Map<Tier, EnchantsSettings.TierSettings> out = new EnumMap<>(Tier.class);
    for (String key : tiers.keys()) {
      Tier tier = enumKey(tiers, key, Tier.class);
      Node t = tiers.section(key);
      out.put(
          tier,
          new EnchantsSettings.TierSettings(
              t.string("color"),
              t.integerAtLeast("xp-levels", 1),
              t.intRange("success", 0, 100),
              t.intRange("destroy", 0, 100),
              t.integerAtLeast("min-rank", 1)));
    }
    return wrap(root, "tiers", () -> new EnchantsSettings(out));
  }

  public static MinesSettings mines(Node root) {
    Node reset = root.section("reset");
    Node tiers = root.section("tiers");
    Map<Integer, MinesSettings.MineTier> out = new HashMap<>();
    for (String key : tiers.keys()) {
      int number = tierNumber(tiers, key);
      Node t = tiers.section(key);
      Node blocks = t.section("blocks");
      Map<String, Integer> composition = new LinkedHashMap<>();
      for (String block : blocks.keys()) {
        composition.put(block.toUpperCase(Locale.ROOT), blocks.integerAtLeast(block, 0));
      }
      String schematic = t.string("schematic");
      out.put(number, wrap(t, "blocks", () -> new MinesSettings.MineTier(schematic, composition)));
    }
    return new MinesSettings(
        root.string("world"),
        root.integerAtLeast("plot-size", 8),
        root.duration("plot-inactivity"),
        reset.duration("interval"),
        reset.decimalBetween("mined-fraction", 0.0, 1.0),
        out);
  }

  public static SpawnersSettings spawners(Node root) {
    Node types = root.section("types");
    Map<String, SpawnersSettings.SpawnerType> out = new HashMap<>();
    for (String key : types.keys()) {
      Node t = types.section(key);
      out.put(
          key.toUpperCase(Locale.ROOT),
          new SpawnersSettings.SpawnerType(t.decimalBetween("rate-per-second", 0.0, 1000.0)));
    }
    return new SpawnersSettings(
        root.integerAtLeast("stack-cap", 1),
        root.integerAtLeast("active-radius-chunks", 0),
        root.integerAtLeast("pending-cap-per-count", 1),
        out);
  }

  public static FactionsSettings factions(Node root) {
    Node name = root.section("name");
    Node power = root.section("power");
    Node claims = root.section("claims");
    Node upkeep = root.section("upkeep");
    Node home = root.section("home");
    Node fly = root.section("fly");
    Node ftop = root.section("ftop");
    int nameMin = name.integerAtLeast("min-length", 1);
    int nameMax = name.integerAtLeast("max-length", nameMin);
    return new FactionsSettings(
        new FactionsSettings.Naming(nameMin, nameMax),
        root.longAtLeast("create-cost", 0),
        root.integerAtLeast("member-cap", 1),
        root.integerAtLeast("max-allies", 0),
        new FactionsSettings.Power(
            power.integerAtLeast("max", 1),
            power.integerAtLeast("regen-per-minute", 0),
            power.integerAtLeast("death-loss", 0),
            power.integer("floor")),
        new FactionsSettings.Claims(
            claims.integerAtLeast("power-per-claim", 1),
            claims.integerAtLeast("min-distance-from-spawn", 0)),
        new FactionsSettings.Upkeep(
            upkeep.longAtLeast("cost-per-chunk-per-day", 0),
            upkeep.integerAtLeast("grace-days", 1)),
        new FactionsSettings.Home(home.integerAtLeast("warmup-seconds", 0)),
        new FactionsSettings.Fly(
            fly.integerAtLeast("enemy-radius", 0),
            fly.integerAtLeast("fall-protection-seconds", 0)),
        new FactionsSettings.FTop(
            ftop.decimalBetween("vault-weight", 0.0, 1.0), ftop.duration("recalc-interval")));
  }

  public static ContractsSettings contracts(Node root) {
    Node pool = root.section("pool");
    List<ContractsSettings.Contract> contracts = new ArrayList<>();
    for (String id : pool.keys()) {
      Node c = pool.section(id);
      Node rewards = c.section("rewards");
      Tier bookTier = rewards.has("book-tier") ? rewards.enumValue("book-tier", Tier.class) : null;
      contracts.add(
          new ContractsSettings.Contract(
              id,
              c.enumValue("archetype", Archetype.class),
              c.enumValue("objective", ObjectiveType.class),
              c.longAtLeast("target", 1),
              c.integerAtLeast("min-rank", 1),
              new ContractsSettings.Rewards(
                  rewards.longAtLeast("rank-xp", 0), rewards.longAtLeast("money", 0), bookTier)));
    }
    int perDay = root.integerAtLeast("per-day", 1);
    return wrap(root, "pool", () -> new ContractsSettings(perDay, contracts));
  }

  public static EventsSettings events(Node root) {
    Node zones = root.section("zones");
    Node koth = root.section("koth");
    Node envoys = root.section("envoys");
    EventsSettings.Zones zoneSettings =
        wrap(
            root,
            "zones",
            () ->
                new EventsSettings.Zones(
                    zones.integerAtLeast("safezone-radius", 0),
                    zones.integerAtLeast("warzone-radius", 0)));
    EventsSettings.Koth kothSettings =
        new EventsSettings.Koth(
            koth.duration("interval"),
            koth.duration("warning"),
            koth.duration("capture-time"),
            koth.integerAtLeast("winner-tag-seconds", 0),
            weightedTable(koth, "lootbag"));
    Node crates = envoys.section("crates");
    Map<String, Integer> crateCounts = new LinkedHashMap<>();
    for (String tier : crates.keys()) {
      crateCounts.put(tier.toUpperCase(Locale.ROOT), crates.integerAtLeast(tier, 0));
    }
    Node loot = envoys.sectionOrEmpty("loot");
    Map<String, List<EventsSettings.WeightedEntry>> lootTables = new LinkedHashMap<>();
    for (String tier : loot.keys()) {
      lootTables.put(tier.toUpperCase(Locale.ROOT), weightedTable(loot, tier));
    }
    EventsSettings.Envoys envoySettings =
        new EventsSettings.Envoys(
            envoys.duration("interval"),
            envoys.duration("countdown"),
            envoys.duration("despawn"),
            crateCounts,
            lootTables);
    return new EventsSettings(zoneSettings, kothSettings, envoySettings);
  }

  public static CombatSettings combat(Node root) {
    return new CombatSettings(
        root.integerAtLeast("tag-seconds", 1),
        root.integerAtLeast("logger-npc-seconds", 1),
        root.stringList("blocked-commands-while-tagged"));
  }

  /** A weighted table written as a map of item name to weight. */
  private static List<EventsSettings.WeightedEntry> weightedTable(Node parent, String path) {
    Node table = parent.section(path);
    List<EventsSettings.WeightedEntry> out = new ArrayList<>();
    for (String item : table.keys()) {
      int weight = table.integerAtLeast(item, 1);
      out.add(new EventsSettings.WeightedEntry(item.toUpperCase(Locale.ROOT), weight));
    }
    if (out.isEmpty()) {
      throw new ConfigException(parent.at(path) + ": table has no entries");
    }
    return out;
  }

  /** A map key that must be a tier number like {@code tier1}, {@code t1} or {@code 1}. */
  private static int tierNumber(Node parent, String key) {
    String digits = key.replaceAll("[^0-9]", "");
    if (digits.isEmpty()) {
      throw new ConfigException(parent.at(key) + ": key must name a tier number, like tier1");
    }
    return Integer.parseInt(digits);
  }

  private static <E extends Enum<E>> E enumKey(Node parent, String key, Class<E> type) {
    try {
      return Enum.valueOf(type, key.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ConfigException(
          parent.at(key) + ": '" + key + "' is not one of " + List.of(type.getEnumConstants()));
    }
  }

  /** Runs a record constructor, turning its validation failure into a file-and-path error. */
  private static <T> T wrap(Node node, String path, Supplier<T> build) {
    try {
      return build.get();
    } catch (IllegalArgumentException e) {
      throw new ConfigException(node.at(path) + ": " + e.getMessage());
    }
  }
}
