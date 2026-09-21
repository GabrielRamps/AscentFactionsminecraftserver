package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.AscentProvider;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.plugin.command.AscentCommand;
import gg.ascent.plugin.config.YamlConfigService;
import gg.ascent.plugin.message.YamlMessages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Owns the lifecycle: configuration and messages first, then the API, then commands. Later epics
 * add the database, the player cache and each module here, in dependency order.
 */
public final class AscentPlugin extends JavaPlugin {

  private AscentApiImpl api;
  private YamlConfigService config;
  private YamlMessages messages;

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

    api = new AscentApiImpl(this, config, messages);
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
    getSLF4JLogger().info("Ascent disabled.");
  }
}
