package gg.ascent.plugin;

import gg.ascent.api.AscentApi;

/** Default {@link AscentApi} implementation, backed by the running plugin instance. */
final class AscentApiImpl implements AscentApi {

  private final AscentPlugin plugin;
  private volatile boolean ready;

  AscentApiImpl(AscentPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String version() {
    return plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean ready() {
    return ready;
  }

  void markReady() {
    this.ready = true;
  }

  void markNotReady() {
    this.ready = false;
  }
}
