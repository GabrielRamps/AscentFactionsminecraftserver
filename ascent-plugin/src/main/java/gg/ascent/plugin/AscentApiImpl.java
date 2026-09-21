package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerService;

/** Default {@link AscentApi} implementation, backed by the running plugin instance. */
final class AscentApiImpl implements AscentApi {

  private final AscentPlugin plugin;
  private final ConfigService config;
  private final Messages messages;
  private final DbExecutor db;
  private final PlayerService players;
  private volatile boolean ready;

  AscentApiImpl(
      AscentPlugin plugin,
      ConfigService config,
      Messages messages,
      DbExecutor db,
      PlayerService players) {
    this.plugin = plugin;
    this.config = config;
    this.messages = messages;
    this.db = db;
    this.players = players;
  }

  @Override
  public String version() {
    return plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean ready() {
    return ready;
  }

  @Override
  public ConfigService config() {
    return config;
  }

  @Override
  public Messages messages() {
    return messages;
  }

  @Override
  public DbExecutor db() {
    return db;
  }

  @Override
  public PlayerService players() {
    return players;
  }

  void markReady() {
    this.ready = true;
  }

  void markNotReady() {
    this.ready = false;
  }
}
