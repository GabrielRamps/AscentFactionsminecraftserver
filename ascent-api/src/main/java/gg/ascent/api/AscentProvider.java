package gg.ascent.api;

/**
 * Static access point to the running {@link AscentApi} instance.
 *
 * <p>The plugin registers itself here during {@code onEnable} and clears the reference during
 * {@code onDisable}. Callers must not cache the returned instance across a plugin reload.
 */
public final class AscentProvider {

  private static volatile AscentApi instance;

  private AscentProvider() {
    throw new AssertionError("No instances.");
  }

  /**
   * Returns the running API instance.
   *
   * @throws IllegalStateException if the plugin is not enabled
   */
  public static AscentApi get() {
    AscentApi api = instance;
    if (api == null) {
      throw new IllegalStateException(
          "Ascent is not enabled. Declare Ascent as a depend/softdepend and access the API from"
              + " onEnable or later.");
    }
    return api;
  }

  /** Returns true when an API instance is registered. */
  public static boolean isPresent() {
    return instance != null;
  }

  /**
   * Registers the API instance. Called by the plugin only.
   *
   * @throws IllegalStateException if an instance is already registered
   */
  public static void register(AscentApi api) {
    if (api == null) {
      throw new IllegalArgumentException("api must not be null");
    }
    synchronized (AscentProvider.class) {
      if (instance != null) {
        throw new IllegalStateException("An Ascent API instance is already registered.");
      }
      instance = api;
    }
  }

  /** Clears the API instance. Called by the plugin only. */
  public static void unregister() {
    synchronized (AscentProvider.class) {
      instance = null;
    }
  }
}
