package gg.ascent.plugin.config;

import gg.ascent.api.config.CombatSettings;
import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.ContractsSettings;
import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.EventsSettings;
import gg.ascent.api.config.FactionsSettings;
import gg.ascent.api.config.KitsSettings;
import gg.ascent.api.config.MinesSettings;
import gg.ascent.api.config.PricesSettings;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.config.ReloadReport.FileResult;
import gg.ascent.api.config.SpawnersSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * Loads every YAML file into its settings record and swaps snapshots atomically.
 *
 * <p>Rules, per PRD E1-S1:
 *
 * <ul>
 *   <li>A file missing from the data folder is seeded from the copy bundled in the jar.
 *   <li>A file that fails to parse is reported with file and path, and its previous snapshot stays
 *       in effect.
 *   <li>On the first load there is no previous snapshot, so a broken file falls back to the bundled
 *       defaults, loudly. The plugin still starts; the operator sees exactly what to fix.
 * </ul>
 *
 * <p>Independent of Bukkit's plugin lifecycle so it can be tested against a temp directory.
 */
public final class YamlConfigService implements ConfigService {

  private final Path dataDir;
  private final Function<String, InputStream> bundled;
  private final Logger log;
  private final List<Slot<?>> slots = new ArrayList<>();

  private volatile CoreSettings core;
  private volatile RanksSettings ranks;
  private volatile EnchantsSettings enchants;
  private volatile MinesSettings mines;
  private volatile SpawnersSettings spawners;
  private volatile FactionsSettings factions;
  private volatile ContractsSettings contracts;
  private volatile EventsSettings events;
  private volatile CombatSettings combat;
  private volatile KitsSettings kits;
  private volatile PricesSettings prices;

  /**
   * @param dataDir the plugin data folder
   * @param bundled resolves a file name to the copy inside the jar, or null if there is none
   * @param log where load problems are reported
   */
  public YamlConfigService(Path dataDir, Function<String, InputStream> bundled, Logger log) {
    this.dataDir = dataDir;
    this.bundled = bundled;
    this.log = log;
    slot("config.yml", SettingsParsers::core, v -> core = v, () -> core);
    slot("ranks.yml", SettingsParsers::ranks, v -> ranks = v, () -> ranks);
    slot("enchants.yml", SettingsParsers::enchants, v -> enchants = v, () -> enchants);
    slot("mines.yml", SettingsParsers::mines, v -> mines = v, () -> mines);
    slot("spawners.yml", SettingsParsers::spawners, v -> spawners = v, () -> spawners);
    slot("factions.yml", SettingsParsers::factions, v -> factions = v, () -> factions);
    slot("contracts.yml", SettingsParsers::contracts, v -> contracts = v, () -> contracts);
    slot("events.yml", SettingsParsers::events, v -> events = v, () -> events);
    slot("combat.yml", SettingsParsers::combat, v -> combat = v, () -> combat);
    slot("kits.yml", SettingsParsers::kits, v -> kits = v, () -> kits);
    slot("prices.yml", SettingsParsers::prices, v -> prices = v, () -> prices);
  }

  /**
   * Registers another file to load and reload alongside the settings files, such as {@code
   * messages.yml}. Must be called before {@link #load()}.
   */
  public <T> void register(
      String file, Function<Node, T> parser, Consumer<T> store, Supplier<T> current) {
    slot(file, parser, store, current);
  }

  private <T> void slot(
      String file, Function<Node, T> parser, Consumer<T> store, Supplier<T> current) {
    slots.add(new Slot<>(file, parser, store, current));
  }

  /**
   * First load. Never throws for a bad file: it falls back to bundled defaults and reports.
   *
   * @throws IllegalStateException if a bundled default itself is missing or invalid, which is a
   *     packaging bug rather than an operator error
   */
  public ReloadReport load() {
    return loadAll(true);
  }

  @Override
  public ReloadReport reload() {
    return loadAll(false);
  }

  private ReloadReport loadAll(boolean initial) {
    List<FileResult> results = new ArrayList<>(slots.size());
    for (Slot<?> slot : slots) {
      results.add(loadSlot(slot, initial));
    }
    return new ReloadReport(results);
  }

  private <T> FileResult loadSlot(Slot<T> slot, boolean initial) {
    Path path = dataDir.resolve(slot.file);
    try {
      seedIfMissing(slot.file, path);
      YamlConfiguration yaml = readYaml(slot.file, path);
      if (addMissingKeys(slot.file, yaml)) {
        yaml.save(path.toFile());
      }
      T value = slot.parser.apply(new Node(slot.file, yaml));
      slot.store.accept(value);
      return new FileResult(slot.file, null);
    } catch (ConfigException e) {
      return failed(slot, initial, e.getMessage());
    } catch (IOException e) {
      return failed(slot, initial, slot.file + ": cannot read: " + e.getMessage());
    }
  }

  private <T> FileResult failed(Slot<T> slot, boolean initial, String error) {
    if (initial && slot.current.get() == null) {
      log.error("{} -- falling back to the bundled defaults until it is fixed", error);
      slot.store.accept(parseBundled(slot));
    } else {
      log.error("{} -- keeping the previous values", error);
    }
    return new FileResult(slot.file, error);
  }

  private <T> T parseBundled(Slot<T> slot) {
    try (InputStream in = requireBundled(slot.file)) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.loadFromString(text);
      return slot.parser.apply(new Node(slot.file + " (bundled)", yaml));
    } catch (IOException | InvalidConfigurationException | ConfigException e) {
      throw new IllegalStateException(
          "bundled default " + slot.file + " is invalid; this is a packaging bug", e);
    }
  }

  /**
   * Copies every key the bundled default has and the operator's file lacks, with its comments, so a
   * file written by an older build keeps working after an upgrade adds settings. Values the
   * operator changed are never touched. Answers whether anything was added.
   *
   * <p>A key the operator deleted on purpose comes back, and is logged; to switch something off,
   * set it to zero or false rather than deleting it.
   */
  private boolean addMissingKeys(String file, YamlConfiguration yaml) {
    YamlConfiguration defaults;
    try (InputStream in = requireBundled(file)) {
      defaults = new YamlConfiguration();
      defaults.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException | InvalidConfigurationException e) {
      throw new IllegalStateException(
          "bundled default " + file + " is invalid; this is a packaging bug", e);
    }
    List<String> added = new ArrayList<>();
    for (String key : defaults.getKeys(true)) {
      if (defaults.isConfigurationSection(key) || yaml.contains(key, true)) {
        continue;
      }
      yaml.set(key, defaults.get(key));
      added.add(key);
      // Comments sit on the first key that was missing along the path, so the block reads as it
      // does in the bundled file.
      String path = key;
      while (path != null) {
        if (!yaml.getComments(path).isEmpty()) {
          break;
        }
        yaml.setComments(path, defaults.getComments(path));
        int dot = path.lastIndexOf('.');
        path = dot < 0 ? null : path.substring(0, dot);
      }
    }
    if (added.isEmpty()) {
      return false;
    }
    log.info(
        "Added {} setting(s) missing from {} using the bundled defaults: {}",
        added.size(),
        file,
        String.join(", ", added));
    return true;
  }

  private void seedIfMissing(String file, Path path) throws IOException {
    if (Files.exists(path)) {
      return;
    }
    Files.createDirectories(path.getParent());
    try (InputStream in = requireBundled(file)) {
      Files.copy(in, path);
    }
    log.info("Created {} from the bundled default", file);
  }

  private InputStream requireBundled(String file) {
    InputStream in = bundled.apply(file);
    if (in == null) {
      throw new IllegalStateException("no bundled default for " + file + "; packaging bug");
    }
    return in;
  }

  private static YamlConfiguration readYaml(String file, Path path) throws IOException {
    String text = Files.readString(path, StandardCharsets.UTF_8);
    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.loadFromString(text);
    } catch (InvalidConfigurationException e) {
      throw new ConfigException(file + ": invalid YAML: " + firstLine(e.getMessage()));
    }
    return yaml;
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "syntax error";
    }
    int nl = message.indexOf('\n');
    return nl < 0 ? message : message.substring(0, nl);
  }

  private record Slot<T>(
      String file, Function<Node, T> parser, Consumer<T> store, Supplier<T> current) {}

  @Override
  public CoreSettings core() {
    return core;
  }

  @Override
  public RanksSettings ranks() {
    return ranks;
  }

  @Override
  public EnchantsSettings enchants() {
    return enchants;
  }

  @Override
  public MinesSettings mines() {
    return mines;
  }

  @Override
  public SpawnersSettings spawners() {
    return spawners;
  }

  @Override
  public FactionsSettings factions() {
    return factions;
  }

  @Override
  public ContractsSettings contracts() {
    return contracts;
  }

  @Override
  public EventsSettings events() {
    return events;
  }

  @Override
  public CombatSettings combat() {
    return combat;
  }

  @Override
  public KitsSettings kits() {
    return kits;
  }

  @Override
  public PricesSettings prices() {
    return prices;
  }
}
