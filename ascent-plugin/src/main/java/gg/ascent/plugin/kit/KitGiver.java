package gg.ascent.plugin.kit;

import gg.ascent.api.config.KitsSettings;
import java.util.UUID;

/** Hands a kit's items to a player; the Bukkit edge of {@link KitManager}. */
public interface KitGiver {

  /** Puts the items in the player's inventory, dropping what does not fit. Main thread. */
  void give(UUID player, KitsSettings.Kit kit);
}
