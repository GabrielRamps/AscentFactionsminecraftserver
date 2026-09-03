package gg.ascent.api;

/**
 * Root entry point for everything Ascent exposes to other modules and to third-party plugins.
 *
 * <p>Every module publishes its service interface here (for example {@code RankService}, {@code
 * EnchantService}, {@code FactionService}) and modules reach each other only through those
 * interfaces, never through implementation classes. See PRD §6.1.
 *
 * <p>Obtain an instance through {@link AscentProvider#get()} or through the Bukkit services
 * manager.
 */
public interface AscentApi {

  /** Returns the running plugin version, for example {@code 0.1.0-SNAPSHOT}. */
  String version();

  /**
   * Returns whether the plugin finished enabling and its services are safe to call.
   *
   * <p>False while the server is still starting up, and after the plugin has been disabled.
   */
  boolean ready();
}
