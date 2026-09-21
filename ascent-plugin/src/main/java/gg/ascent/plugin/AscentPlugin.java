package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.AscentProvider;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.db.DbException;
import gg.ascent.plugin.admin.AdminActionLog;
import gg.ascent.plugin.admin.AdminService;
import gg.ascent.plugin.admin.SqlAdminActionRepository;
import gg.ascent.plugin.command.AscentCommand;
import gg.ascent.plugin.config.YamlConfigService;
import gg.ascent.plugin.db.BukkitMainThread;
import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.economy.BaltopCache;
import gg.ascent.plugin.economy.EconomyCommands;
import gg.ascent.plugin.economy.EconomyServiceImpl;
import gg.ascent.plugin.economy.SqlTransactionRepository;
import gg.ascent.plugin.economy.VaultHook;
import gg.ascent.plugin.message.YamlMessages;
import gg.ascent.plugin.player.PlayerListener;
import gg.ascent.plugin.player.PlayerManager;
import gg.ascent.plugin.player.SqlPlayerRepository;
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
  private BaltopCache baltop;

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
    baltop = new BaltopCache(database.executor(), playerRepo, redis, getSLF4JLogger());
    baltop.refresh();
    // PRD E1-S3/E1-S4: autosave every interval, then rebuild the leaderboard from what was written.
    long autosaveTicks = Math.max(20, config.core().players().autosaveInterval().toSeconds() * 20);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this, () -> players.autosave().thenRun(baltop::refresh), autosaveTicks, autosaveTicks);
    if (getServer().getPluginManager().getPlugin("Vault") != null) {
      VaultHook.register(this, economy, players);
    } else {
      getSLF4JLogger().info("Vault is not installed; third-party plugins cannot see balances.");
    }

    api = new AscentApiImpl(this, config, messages, database.executor(), players, economy);
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
            new AdminService(players, economy, config, actionLog),
            actionLog,
            getSLF4JLogger());
    EconomyCommands money =
        new EconomyCommands(this, economy, players, baltop, messages, getSLF4JLogger());
    if (!bind("ascent", admin, admin)
        || !bind("bal", money, money)
        || !bind("pay", money, money)
        || !bind("baltop", money, money)) {
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
}
