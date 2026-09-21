package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.AscentProvider;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.db.DbException;
import gg.ascent.plugin.command.AscentCommand;
import gg.ascent.plugin.config.YamlConfigService;
import gg.ascent.plugin.db.BukkitMainThread;
import gg.ascent.plugin.db.Database;
import gg.ascent.plugin.message.YamlMessages;
import gg.ascent.plugin.redis.RedisConnector;
import java.time.Duration;
import org.bukkit.command.PluginCommand;
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

    api = new AscentApiImpl(this, config, messages, database.executor());
    AscentProvider.register(api);
    getServer().getServicesManager().register(AscentApi.class, api, this, ServicePriority.Normal);

    PluginCommand command = getCommand("ascent");
    if (command == null) {
      getSLF4JLogger().error("Command 'ascent' is missing from plugin.yml; disabling.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    AscentCommand executor = new AscentCommand(this, config, messages);
    command.setExecutor(executor);
    command.setTabCompleter(executor);

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

  /** The database, for commands that report on it. Null only while disabled. */
  public Database database() {
    return database;
  }

  /** The Redis connector; answers empty results while Redis is unavailable. */
  public RedisConnector redis() {
    return redis;
  }
}
