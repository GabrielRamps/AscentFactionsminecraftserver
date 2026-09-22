package gg.ascent.plugin.rank;

import gg.ascent.api.player.PlayerProfile;

/** How a rank-up reaches the player and the rest of the plugin; the Bukkit edge of the ladder. */
public interface RankUpNotifier {

  /** Fires the event and plays the message, title and sound. Main thread. */
  void rankedUp(PlayerProfile profile, int oldRank, int newRank);
}
