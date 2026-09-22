package gg.ascent.api.event;

import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.Tier;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** An unopened book was revealed (PRD §6.4.3). Not cancellable. */
public final class BookRevealedEvent extends PlayerEvent {

  private static final HandlerList HANDLERS = new HandlerList();

  private final Tier tier;
  private final OpenedBook book;

  public BookRevealedEvent(Player player, Tier tier, OpenedBook book) {
    super(player);
    this.tier = tier;
    this.book = book;
  }

  public Tier tier() {
    return tier;
  }

  public OpenedBook book() {
    return book;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
