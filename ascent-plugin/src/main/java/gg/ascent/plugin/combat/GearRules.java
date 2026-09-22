package gg.ascent.plugin.combat;

import gg.ascent.api.config.CombatSettings;
import java.util.Locale;

/** Which materials are kept off the server (PRD E9-S2). Pure. */
public final class GearRules {

  private GearRules() {}

  /** Whether an item of this material name is banned by {@code gear}. */
  public static boolean isBanned(String materialName, CombatSettings.Gear gear) {
    String name = materialName.toUpperCase(Locale.ROOT);
    if (gear.bannedItems().contains(name)) {
      return true;
    }
    for (String prefix : gear.bannedPrefixes()) {
      if (!prefix.isEmpty() && name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
