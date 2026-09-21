package gg.ascent.api.config;

/**
 * Typed access to every configuration file.
 *
 * <p>Each accessor returns an immutable snapshot. A snapshot never changes underneath a caller;
 * {@link #reload()} swaps in new snapshots atomically per file, and a file that fails to parse
 * keeps its previous snapshot. Callers that need current values should re-read the accessor rather
 * than cache the result.
 *
 * <p>Modules read their settings from here and never touch YAML directly (PRD E1-S1).
 */
public interface ConfigService {

  CoreSettings core();

  RanksSettings ranks();

  EnchantsSettings enchants();

  MinesSettings mines();

  SpawnersSettings spawners();

  FactionsSettings factions();

  ContractsSettings contracts();

  EventsSettings events();

  CombatSettings combat();

  /**
   * Re-reads every file, including {@code messages.yml}.
   *
   * <p>Never throws: a file that fails to parse is reported and its previous values stay in effect.
   * The report says which files loaded and, for the rest, why not.
   */
  ReloadReport reload();
}
