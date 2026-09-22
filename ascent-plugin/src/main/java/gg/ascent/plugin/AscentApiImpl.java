package gg.ascent.plugin;

import gg.ascent.api.AscentApi;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.SellMultiplierService;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.kit.KitService;
import gg.ascent.api.message.Messages;
import gg.ascent.api.mine.MineService;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.progress.ProgressBus;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.UnlockService;

/** Default {@link AscentApi} implementation, backed by the running plugin instance. */
final class AscentApiImpl implements AscentApi {

  private final AscentPlugin plugin;
  private final ConfigService config;
  private final Messages messages;
  private final DbExecutor db;
  private final PlayerService players;
  private final EconomyService economy;
  private final ItemRegistry items;
  private final RankService ranks;
  private final UnlockService unlocks;
  private final KitService kits;
  private final MineService mines;
  private final ProgressBus progress;
  private final SellMultiplierService sellMultiplier;
  private volatile boolean ready;

  AscentApiImpl(
      AscentPlugin plugin,
      ConfigService config,
      Messages messages,
      DbExecutor db,
      PlayerService players,
      EconomyService economy,
      ItemRegistry items,
      RankService ranks,
      UnlockService unlocks,
      KitService kits,
      MineService mines,
      ProgressBus progress,
      SellMultiplierService sellMultiplier) {
    this.plugin = plugin;
    this.config = config;
    this.messages = messages;
    this.db = db;
    this.players = players;
    this.economy = economy;
    this.items = items;
    this.ranks = ranks;
    this.unlocks = unlocks;
    this.kits = kits;
    this.mines = mines;
    this.progress = progress;
    this.sellMultiplier = sellMultiplier;
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

  @Override
  public EconomyService economy() {
    return economy;
  }

  @Override
  public ItemRegistry items() {
    return items;
  }

  @Override
  public RankService ranks() {
    return ranks;
  }

  @Override
  public UnlockService unlocks() {
    return unlocks;
  }

  @Override
  public KitService kits() {
    return kits;
  }

  @Override
  public MineService mines() {
    return mines;
  }

  @Override
  public ProgressBus progress() {
    return progress;
  }

  @Override
  public SellMultiplierService sellMultiplier() {
    return sellMultiplier;
  }

  void markReady() {
    this.ready = true;
  }

  void markNotReady() {
    this.ready = false;
  }
}
