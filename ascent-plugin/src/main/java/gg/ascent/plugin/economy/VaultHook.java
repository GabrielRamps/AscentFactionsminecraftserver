package gg.ascent.plugin.economy;

import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.player.PlayerService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers {@link VaultEconomy} with Vault. Kept in its own class so the Vault types are only
 * loaded when the Vault plugin is present; the caller checks that first.
 */
public final class VaultHook {

  private VaultHook() {}

  public static void register(Plugin plugin, EconomyService economy, PlayerService players) {
    plugin
        .getServer()
        .getServicesManager()
        .register(
            Economy.class, new VaultEconomy(economy, players), plugin, ServicePriority.Highest);
    plugin.getSLF4JLogger().info("Registered the Ascent economy with Vault.");
  }
}
