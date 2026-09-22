package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.AscentProvider;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.db.DbException;
import gg.ascent.plugin.admin.AdminActionLog;
import gg.ascent.plugin.admin.AdminService;
import gg.ascent.plugin.admin.SqlAdminActionRepository;
import gg.ascent.plugin.alert.DiscordWebhook;
import gg.ascent.plugin.alert.StaffAlerts;
import gg.ascent.plugin.command.AscentCommand;
import gg.ascent.plugin.config.YamlConfigService;
import gg.ascent.plugin.db.BukkitMainThread;
import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.economy.ConstantSellMultiplier;
import gg.ascent.plugin.economy.EconomyCommands;
import gg.ascent.plugin.economy.EconomyServiceImpl;
import gg.ascent.plugin.economy.SellCommand;
import gg.ascent.plugin.economy.SqlTransactionRepository;
import gg.ascent.plugin.economy.VaultHook;
import gg.ascent.plugin.enchant.ApplyListener;
import gg.ascent.plugin.enchant.BookListener;
import gg.ascent.plugin.enchant.EnchantServiceImpl;
import gg.ascent.plugin.enchant.EnchanterCommand;
import gg.ascent.plugin.enchant.SqlEnchantRollRepository;
import gg.ascent.plugin.enchant.runtime.CombatResolver;
import gg.ascent.plugin.enchant.runtime.Cooldowns;
import gg.ascent.plugin.enchant.runtime.EffectApplier;
import gg.ascent.plugin.enchant.runtime.EffectListener;
import gg.ascent.plugin.enchant.runtime.FighterViews;
import gg.ascent.plugin.enchant.runtime.PassiveTask;
import gg.ascent.plugin.enchant.runtime.SafeZones;
import gg.ascent.plugin.enchant.runtime.Silences;
import gg.ascent.plugin.enchant.station.AlchemistCommand;
import gg.ascent.plugin.enchant.station.TinkererCommand;
import gg.ascent.plugin.enchant.station.XpBottleListener;
import gg.ascent.plugin.item.DupeScanTask;
import gg.ascent.plugin.item.ItemAudit;
import gg.ascent.plugin.item.ItemListener;
import gg.ascent.plugin.item.ItemRegistryImpl;
import gg.ascent.plugin.item.LoreRenderer;
import gg.ascent.plugin.item.SqlItemRepository;
import gg.ascent.plugin.kit.BukkitKitGiver;
import gg.ascent.plugin.kit.KitCommand;
import gg.ascent.plugin.kit.KitListener;
import gg.ascent.plugin.kit.KitManager;
import gg.ascent.plugin.kit.SqlKitCooldownRepository;
import gg.ascent.plugin.leaderboard.Leaderboard;
import gg.ascent.plugin.leaderboard.Leaderboard.Entry;
import gg.ascent.plugin.message.YamlMessages;
import gg.ascent.plugin.mine.BukkitMineBuilder;
import gg.ascent.plugin.mine.FaweMineBuilder;
import gg.ascent.plugin.mine.MineBuilder;
import gg.ascent.plugin.mine.MineCommand;
import gg.ascent.plugin.mine.MineListener;
import gg.ascent.plugin.mine.MinePlots;
import gg.ascent.plugin.mine.MineWorld;
import gg.ascent.plugin.mine.SqlMinePlotRepository;
import gg.ascent.plugin.player.PlayerListener;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.player.SqlPlayerRepository;
import gg.ascent.plugin.progress.SimpleProgressBus;
import gg.ascent.plugin.rank.BukkitRankUpNotifier;
import gg.ascent.plugin.rank.PvpKillTracker;
import gg.ascent.plugin.rank.PvpListener;
import gg.ascent.plugin.rank.RankCommand;
import gg.ascent.plugin.rank.RankServiceImpl;
import gg.ascent.plugin.rank.SqlXpLogRepository;
import gg.ascent.plugin.rank.UnlockServiceImpl;
import gg.ascent.plugin.redis.RedisConnector;
import java.time.Clock;
import java.time.Duration;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Owns the lifecycle: configuration and messages first, then the database (fatal if it cannot
 * connect), then Redis (never fatal), then the API, then commands. Later epics add the player cache
 * and each module here, in dependency order. {@link #onDisable} tears down in reverse.
 */
public final class AscentPlugin extends JavaPlugin {

  private AscentApiImpl api;
  private YamlConfigService config;
  private YamlMessages messages;
  private Database database;
  private RedisConnector redis;
  private PlayerManager players;
  private EconomyServiceImpl economy;
  private Leaderboard baltop;
  private Leaderboard rankTop;
  private RankServiceImpl ranks;
  private UnlockServiceImpl unlocks;
  private KitManager kits;
  private MineWorld mines;
  private SimpleProgressBus progress;
  private EnchantServiceImpl enchants;
  private ItemRegistryImpl items;
  private DupeScanTask dupeScan;
  private StaffAlerts alerts;

  @Override
  public void onEnable() {
    messages = new YamlMessages(getSLF4JLogger());
    config = new YamlConfigService(getDataFolder().toPath(), this::getResource, getSLF4JLogger());
    config.register(YamlMessages.FILE, YamlMessages::parse, messages::accept, messages::current);

    ReloadReport report;
    try {
      report = config.load();
    } catch (IllegalStateException e) {
      // A bundled default is broken: a packaging bug, not something an operator can fix live.
      getSLF4JLogger().error("Configuration cannot load; disabling.", e);
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    if (!report.allOk()) {
      getSLF4JLogger()
          .warn(
              "{} configuration file(s) failed to load and are running on fallback values;"
                  + " fix them and run /ascent reload.",
              report.failures().size());
      // PRD E3-S1: a malformed enchant catalog is not something to run past. The error above
      // names the enchant and the field.
      for (ReloadReport.FileResult failure : report.failures()) {
        if (failure.file().equals("enchants.yml")) {
          getSLF4JLogger()
              .error("enchants.yml is malformed; refusing to enable: {}", failure.error());
          getServer().getPluginManager().disablePlugin(this);
          return;
        }
      }
    }

    try {
      database =
          Database.connect(
              config.core().database(),
              getClass().getClassLoader(),
              new BukkitMainThread(this),
              getSLF4JLogger());
    } catch (DbException e) {
      // PRD E1-S2: a database we cannot reach at startup disables the plugin, loudly.
      getSLF4JLogger().error("DATABASE UNAVAILABLE: {}", e.getMessage());
      getSLF4JLogger().error("Ascent cannot run without its database; disabling.");
      if (config.core().debug()) {
        getSLF4JLogger().error("Cause:", e);
      }
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    redis = RedisConnector.connect(config.core().redis(), getSLF4JLogger());

    SqlPlayerRepository playerRepo = new SqlPlayerRepository();
    players =
        new PlayerManager(
            database.executor(),
            playerRepo,
            config.core().players().startingBalance(),
            Clock.systemUTC(),
            getSLF4JLogger());
    getServer()
        .getPluginManager()
        .registerEvents(new PlayerListener(players, messages, getSLF4JLogger()), this);

    economy =
        new EconomyServiceImpl(
            players,
            playerRepo,
            new SqlTransactionRepository(),
            database.executor(),
            Clock.systemUTC(),
            getSLF4JLogger());
    baltop =
        new Leaderboard(
            "ascent:baltop",
            database.executor(),
            c ->
                playerRepo.topBalances(c, Leaderboard.SIZE).stream()
                    .map(e -> new Entry(e.uuid(), e.name(), e.balance()))
                    .toList(),
            redis,
            getSLF4JLogger());
    rankTop =
        new Leaderboard(
            "ascent:ranktop",
            database.executor(),
            c ->
                playerRepo.topRanks(c, Leaderboard.SIZE).stream()
                    .map(
                        e ->
                            new Entry(
                                e.uuid(),
                                e.name(),
                                e.rank() * RankCommand.RANK_SCORE_SCALE + e.xp()))
                    .toList(),
            redis,
            getSLF4JLogger());
    baltop.refresh();
    rankTop.refresh();
    // PRD E1-S3/E1-S4/E2-S1: autosave every interval, then rebuild the boards from what was
    // written.
    long autosaveTicks = Math.max(20, config.core().players().autosaveInterval().toSeconds() * 20);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this,
            () ->
                players
                    .autosave()
                    .thenRun(
                        () -> {
                          baltop.refresh();
                          rankTop.refresh();
                        }),
            autosaveTicks,
            autosaveTicks);

    unlocks = new UnlockServiceImpl(config);
    ranks =
        new RankServiceImpl(
            players,
            config,
            database.executor(),
            new SqlXpLogRepository(),
            new BukkitRankUpNotifier(getServer(), messages, config, getSLF4JLogger()),
            Clock.systemUTC(),
            getSLF4JLogger());
    getServer()
        .getPluginManager()
        .registerEvents(
            new PvpListener(ranks, config, new PvpKillTracker(Clock.systemUTC())), this);
    if (getServer().getPluginManager().getPlugin("Vault") != null) {
      VaultHook.register(this, economy, players);
    } else {
      getSLF4JLogger().info("Vault is not installed; third-party plugins cannot see balances.");
    }

    alerts =
        new StaffAlerts(
            getServer(),
            messages,
            new DiscordWebhook(config.core().alerts().discordWebhookUrl(), getSLF4JLogger()));
    if (!alerts.discordEnabled()) {
      getSLF4JLogger().info("No DISCORD_WEBHOOK_URL in the environment; alerts stay in game.");
    }
    SqlItemRepository itemRepo = new SqlItemRepository();
    items =
        new ItemRegistryImpl(
            this,
            new ItemAudit(database.executor(), itemRepo, Clock.systemUTC(), getSLF4JLogger()));
    getServer().getPluginManager().registerEvents(new ItemListener(items), this);
    dupeScan =
        new DupeScanTask(
            getServer(),
            database.executor(),
            itemRepo,
            alerts,
            Clock.systemUTC(),
            getSLF4JLogger());
    long scanTicks = Math.max(20, config.core().items().dupeScanInterval().toSeconds() * 20);
    getServer().getScheduler().runTaskTimer(this, dupeScan, scanTicks, scanTicks);

    enchants =
        new EnchantServiceImpl(
            getServer(),
            config,
            items,
            players,
            unlocks,
            new LoreRenderer(config, messages),
            messages,
            database.executor(),
            new SqlEnchantRollRepository(),
            new java.security.SecureRandom(),
            Clock.systemUTC(),
            getSLF4JLogger());
    getServer().getPluginManager().registerEvents(new BookListener(enchants, messages), this);
    getServer()
        .getPluginManager()
        .registerEvents(new ApplyListener(enchants, players, messages), this);
    EnchanterCommand enchanterCommand =
        new EnchanterCommand(enchants, players, unlocks, config, messages);
    getServer().getPluginManager().registerEvents(enchanterCommand, this);

    // PRD E3-S7, E3-S8: the Tinkerer and the Alchemist, and the XP bottles the Tinkerer pays in.
    TinkererCommand tinkererCommand =
        new TinkererCommand(
            this, enchants, config, items, messages, new java.security.SecureRandom());
    AlchemistCommand alchemistCommand =
        new AlchemistCommand(this, enchants, config, items, messages);
    getServer().getPluginManager().registerEvents(tinkererCommand, this);
    getServer().getPluginManager().registerEvents(alchemistCommand, this);
    getServer()
        .getPluginManager()
        .registerEvents(new XpBottleListener(enchants, items, messages), this);

    // PRD E3-S4: the effect runtime. Combat math is pure; these are its Bukkit hooks.
    java.security.SecureRandom effectRandom = new java.security.SecureRandom();
    Silences silences = new Silences();
    Cooldowns cooldowns = new Cooldowns();
    FighterViews views = new FighterViews(enchants, config, silences);
    EffectApplier applier = new EffectApplier(this, silences, getSLF4JLogger());
    getServer()
        .getPluginManager()
        .registerEvents(
            new EffectListener(
                views,
                new CombatResolver(effectRandom, cooldowns),
                applier,
                cooldowns,
                silences,
                SafeZones.NONE,
                items,
                effectRandom),
            this);
    getServer()
        .getScheduler()
        .runTaskTimer(this, new PassiveTask(getServer(), views, applier, SafeZones.NONE), 20L, 20L);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this, () -> cooldowns.prune(getServer().getCurrentTick()), 20L * 60, 20L * 60);

    kits =
        new KitManager(
            config,
            players,
            unlocks,
            database.executor(),
            new SqlKitCooldownRepository(),
            new BukkitKitGiver(getServer(), items, enchants, messages, getSLF4JLogger()),
            Clock.systemUTC(),
            getSLF4JLogger());
    KitCommand kitCommand = new KitCommand(kits, config, messages);
    getServer()
        .getPluginManager()
        .registerEvents(new KitListener(kits, kitCommand, config, messages), this);

    progress = new SimpleProgressBus(getSLF4JLogger());
    ConstantSellMultiplier sellMultiplier = new ConstantSellMultiplier(1.0);
    MinePlots plots =
        new MinePlots(
            config,
            database.executor(),
            new SqlMinePlotRepository(),
            Clock.systemUTC(),
            getSLF4JLogger());
    MineBuilder mineBuilder =
        getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null
            ? new FaweMineBuilder(this, config.mines(), getDataFolder().toPath(), getSLF4JLogger())
            : new BukkitMineBuilder(this, config.mines(), getSLF4JLogger());
    mines =
        new MineWorld(
            getServer(), config, players, unlocks, plots, mineBuilder, messages, getSLF4JLogger());
    try {
      mines.loadWorld();
    } catch (RuntimeException e) {
      getSLF4JLogger().error("Could not load the mines world; /mine is unavailable.", e);
    }
    plots.load();
    getServer()
        .getPluginManager()
        .registerEvents(new MineListener(mines, ranks, enchants, progress, config, messages), this);
    getServer().getScheduler().runTaskTimer(this, mines::sweep, 20L * 30, 20L * 30);

    api =
        new AscentApiImpl(
            this,
            config,
            messages,
            database.executor(),
            players,
            economy,
            items,
            ranks,
            unlocks,
            kits,
            mines,
            progress,
            sellMultiplier,
            enchants);
    AscentProvider.register(api);
    getServer().getServicesManager().register(AscentApi.class, api, this, ServicePriority.Normal);

    AdminActionLog actionLog =
        new AdminActionLog(
            database.executor(),
            new SqlAdminActionRepository(),
            Clock.systemUTC(),
            getSLF4JLogger());
    AscentCommand admin =
        new AscentCommand(
            this,
            config,
            messages,
            new AdminService(
                players,
                economy,
                ranks,
                enchants,
                (playerId, stack) -> {
                  org.bukkit.entity.Player target = getServer().getPlayer(playerId);
                  if (target != null) {
                    for (org.bukkit.inventory.ItemStack rest :
                        target.getInventory().addItem(stack).values()) {
                      target.getWorld().dropItemNaturally(target.getLocation(), rest);
                    }
                  }
                },
                config,
                actionLog),
            actionLog,
            getSLF4JLogger());
    RankCommand rank = new RankCommand(this, ranks, unlocks, rankTop, messages);
    MineCommand mineCommand = new MineCommand(mines, messages);
    SellCommand sellCommand =
        new SellCommand(economy, sellMultiplier, items, progress, config, messages);
    EconomyCommands money =
        new EconomyCommands(this, economy, players, baltop, messages, getSLF4JLogger());
    if (!bind("ascent", admin, admin)
        || !bind("bal", money, money)
        || !bind("pay", money, money)
        || !bind("baltop", money, money)
        || !bind("rank", rank, rank)
        || !bind("kit", kitCommand, kitCommand)
        || !bind("mine", mineCommand, mineCommand)
        || !bind("sell", sellCommand, sellCommand)
        || !bind("enchanter", enchanterCommand, enchanterCommand)
        || !bind("tinkerer", tinkererCommand, tinkererCommand)
        || !bind("alchemist", alchemistCommand, alchemistCommand)) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    api.markReady();
    getSLF4JLogger().info("Ascent {} enabled.", getPluginMeta().getVersion());
  }

  @Override
  public void onDisable() {
    if (api != null) {
      api.markNotReady();
    }
    getServer().getServicesManager().unregisterAll(this);
    AscentProvider.unregister();
    api = null;
    getServer().getScheduler().cancelTasks(this);
    if (players != null && database != null) {
      // PRD E1-S3: shutdown flushes every profile. Blocking is right here: no more ticks run.
      database.beginShutdown();
      players.flushAll();
      players = null;
    }
    economy = null;
    baltop = null;
    rankTop = null;
    ranks = null;
    unlocks = null;
    kits = null;
    mines = null;
    progress = null;
    enchants = null;
    items = null;
    dupeScan = null;
    alerts = null;
    if (redis != null) {
      redis.close();
      redis = null;
    }
    if (database != null) {
      database.beginShutdown();
      database.close(Duration.ofSeconds(15));
      database = null;
    }
    getSLF4JLogger().info("Ascent disabled.");
  }

  private boolean bind(String name, CommandExecutor executor, TabCompleter completer) {
    PluginCommand command = getCommand(name);
    if (command == null) {
      getSLF4JLogger().error("Command '{}' is missing from plugin.yml; disabling.", name);
      return false;
    }
    command.setExecutor(executor);
    command.setTabCompleter(completer);
    return true;
  }

  /** The database, for commands that report on it. Null only while disabled. */
  public Database database() {
    return database;
  }

  /** The Redis connector; answers empty results while Redis is unavailable. */
  public RedisConnector redis() {
    return redis;
  }

  /** The player cache. Null only while disabled. */
  public PlayerManager players() {
    return players;
  }

  /** Money. Null only while disabled. */
  public EconomyServiceImpl economy() {
    return economy;
  }

  /** The item registry. Null only while disabled. */
  public ItemRegistryImpl items() {
    return items;
  }

  /** The dupe scan, for the debug command. Null only while disabled. */
  public DupeScanTask dupeScan() {
    return dupeScan;
  }

  /** Staff alert routing. Null only while disabled. */
  public StaffAlerts alerts() {
    return alerts;
  }
}
