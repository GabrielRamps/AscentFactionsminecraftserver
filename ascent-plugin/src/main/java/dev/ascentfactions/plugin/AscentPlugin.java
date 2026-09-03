package dev.ascentfactions.plugin;

import org.bukkit.plugin.java.JavaPlugin;

/** Main plugin class. Epic 0 skeleton; modules are wired in from E1-S1 onward. */
public final class AscentPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    String version = getPluginMeta().getVersion();
    String minecraft = getServer().getMinecraftVersion();
    int java = Runtime.version().feature();
    getSLF4JLogger().info("Ascent {} enabled on Paper {} (Java {})", version, minecraft, java);
  }

  @Override
  public void onDisable() {
    getSLF4JLogger().info("Ascent disabled");
  }
}
