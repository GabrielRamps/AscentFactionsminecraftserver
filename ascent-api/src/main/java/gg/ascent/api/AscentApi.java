package gg.ascent.api;

/**
 * Entry point for the public Ascent API.
 *
 * <p>Other plugins obtain an instance via {@link #get()} once Ascent has enabled. Services
 * (economy, ranks, factions, and so on) are added here by later stories; Epic 0 only establishes
 * the module.
 */
public final class AscentApi {

  private static volatile AscentApi instance;

  private final String version;

  private AscentApi(String version) {
    this.version = version;
  }

  /**
   * Returns the active API instance.
   *
   * @throws IllegalStateException if Ascent has not been enabled yet
   */
  public static AscentApi get() {
    AscentApi api = instance;
    if (api == null) {
      throw new IllegalStateException("Ascent has not been enabled yet");
    }
    return api;
  }

  /** Called by the plugin on enable. Not part of the public contract. */
  public static void register(String version) {
    instance = new AscentApi(version);
  }

  /** Called by the plugin on disable. Not part of the public contract. */
  public static void unregister() {
    instance = null;
  }

  /** The Ascent plugin version, as declared in its plugin descriptor. */
  public String version() {
    return version;
  }
}
