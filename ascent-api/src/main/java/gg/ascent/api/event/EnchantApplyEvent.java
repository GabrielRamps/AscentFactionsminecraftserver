package gg.ascent.api.event;

import gg.ascent.api.enchant.ApplyResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A book is about to be rolled onto an item (PRD §6.4.3). Cancellable before the roll; after the
 * roll the same instance carries the {@link #result()} for listeners that kept a reference, and
 * {@link EnchantAppliedEvent} fires with it.
 */
public final class EnchantApplyEvent extends PlayerEvent implements Cancellable {

  private static final HandlerList HANDLERS = new HandlerList();

  private final ItemStack book;
  private final ItemStack target;
  private final String enchantId;
  private final int level;
  private boolean cancelled;
  private @Nullable ApplyResult result;

  public EnchantApplyEvent(
      Player player, ItemStack book, ItemStack target, String enchantId, int level) {
    super(player);
    this.book = book;
    this.target = target;
    this.enchantId = enchantId;
    this.level = level;
  }

  public ItemStack book() {
    return book;
  }

  public ItemStack target() {
    return target;
  }

  public String enchantId() {
    return enchantId;
  }

  public int level() {
    return level;
  }

  /** Null until the roll has happened. */
  public @Nullable ApplyResult result() {
    return result;
  }

  public void setResult(ApplyResult result) {
    this.result = result;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
