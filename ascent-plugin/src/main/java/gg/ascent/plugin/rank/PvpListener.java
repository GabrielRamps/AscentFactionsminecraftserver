package gg.ascent.plugin.rank;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.XpSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Grants PvP kill XP (PRD E2-S1). The other sources are granted by the modules that own them. */
public final class PvpListener implements Listener {

  private final RankService ranks;
  private final ConfigService config;
  private final PvpKillTracker tracker;

  public PvpListener(RankService ranks, ConfigService config, PvpKillTracker tracker) {
    this.ranks = ranks;
    this.config = config;
    this.tracker = tracker;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();
    if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
      return;
    }
    long xp =
        tracker.xpFor(
            killer.getUniqueId(), victim.getUniqueId(), config.ranks().sources().pvpKill());
    ranks.addXp(killer.getUniqueId(), XpSource.PVP_KILL, xp);
  }
}
