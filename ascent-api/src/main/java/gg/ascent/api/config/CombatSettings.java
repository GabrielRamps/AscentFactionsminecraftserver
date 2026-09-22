package gg.ascent.api.config;

import java.util.List;

/**
 * {@code combat.yml}: combat tag and the logger NPC (PRD E9-S1). Combat mechanics themselves are
 * OldCombatMechanics' job; see {@code server/PLUGINS.md}.
 *
 * @param tagSeconds how long a player stays tagged after PvP damage; refreshed on hit
 * @param loggerNpcSeconds how long the stand-in NPC persists after a tagged logout
 * @param blockedCommandsWhileTagged root commands refused while tagged, without the slash
 */
public record CombatSettings(
    int tagSeconds, int loggerNpcSeconds, List<String> blockedCommandsWhileTagged, Gear gear) {

  public CombatSettings {
    blockedCommandsWhileTagged = List.copyOf(blockedCommandsWhileTagged);
  }

  /** Settings with the gear rules at their defaults. */
  public CombatSettings(
      int tagSeconds, int loggerNpcSeconds, List<String> blockedCommandsWhileTagged) {
    this(tagSeconds, loggerNpcSeconds, blockedCommandsWhileTagged, Gear.DEFAULT);
  }

  /**
   * Gear gating (PRD E9-S2): what breaks the 1.8 meta and is kept out.
   *
   * @param bannedItems material names that cannot be crafted, picked up or kept
   * @param bannedPrefixes material-name prefixes banned the same way, e.g. {@code NETHERITE_}
   * @param godAppleCooldownSeconds seconds between god apples
   * @param enderpearlCooldownSeconds seconds between ender pearls
   */
  public record Gear(
      List<String> bannedItems,
      List<String> bannedPrefixes,
      int godAppleCooldownSeconds,
      int enderpearlCooldownSeconds) {
    public static final Gear DEFAULT =
        new Gear(
            List.of("SHIELD", "ELYTRA", "TRIDENT", "CROSSBOW", "TOTEM_OF_UNDYING", "END_CRYSTAL"),
            List.of("NETHERITE_"),
            60,
            16);

    public Gear {
      bannedItems = List.copyOf(bannedItems);
      bannedPrefixes = List.copyOf(bannedPrefixes);
    }
  }
}
