package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.AscentProvider;
import gg.ascent.plugin.command.AscentCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Epic 0 deliberately keeps this class thin: it registers the API instance and the {@code
 * /ascent} command so the skeleton is verifiable on a live server. Epic 1 adds configuration, the
 * database pool, the player profile cache and the module lifecycle here.
 */
public final class AscentPlugin extends JavaPlugin {

  private AscentApiImpl api;

  @Override
  public void onEnable() {
    api = new AscentApiImpl(this);

    AscentProvider.register(api);
    getServer().getServicesManager().register(AscentApi.class, api, this, ServicePriority.Normal);

    PluginCommand command = getCommand("ascent");
    if (command == null) {
      getSLF4JLogger().error("Command 'ascent' is missing from plugin.yml; disabling.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    command.setExecutor(new AscentCommand(this));

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
