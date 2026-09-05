package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import org.bukkit.plugin.java.JavaPlugin;

/** Main plugin class. Epic 0 only proves the build, load, and reload loop works. */
public final class AscentPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    String version = getPluginMeta().getVersion();
    AscentApi.register(version);
    getSLF4JLogger().info("Ascent {} enabled on {}", version, getServer().getVersion());
  }

  @Override
  public void onDisable() {
    AscentApi.unregister();
    getSLF4JLogger().info("Ascent disabled");
  }
}
