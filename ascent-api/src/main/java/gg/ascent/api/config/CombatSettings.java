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
    int tagSeconds, int loggerNpcSeconds, List<String> blockedCommandsWhileTagged) {

  public CombatSettings {
    blockedCommandsWhileTagged = List.copyOf(blockedCommandsWhileTagged);
  }
}
