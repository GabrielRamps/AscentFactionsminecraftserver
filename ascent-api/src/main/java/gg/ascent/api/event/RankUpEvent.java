package gg.ascent.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** A player reached a new rank (PRD §6.4.3). Fired on the main thread; not cancellable. */
public final class RankUpEvent extends PlayerEvent {

  private static final HandlerList HANDLERS = new HandlerList();

  private final int oldRank;
  private final int newRank;

  public RankUpEvent(Player player, int oldRank, int newRank) {
    super(player);
    this.oldRank = oldRank;
    this.newRank = newRank;
  }

  public int oldRank() {
    return oldRank;
  }

  public int newRank() {
    return newRank;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
