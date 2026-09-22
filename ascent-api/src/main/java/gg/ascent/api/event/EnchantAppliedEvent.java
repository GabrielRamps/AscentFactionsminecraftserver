package gg.ascent.api.event;

import gg.ascent.api.enchant.ApplyResult;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/** A book was rolled onto an item, whatever the outcome. Fired after the roll; not cancellable. */
public final class EnchantAppliedEvent extends PlayerEvent {

  private static final HandlerList HANDLERS = new HandlerList();

  private final ItemStack target;
  private final String enchantId;
  private final int level;
  private final ApplyResult result;

  public EnchantAppliedEvent(
      Player player, ItemStack target, String enchantId, int level, ApplyResult result) {
    super(player);
    this.target = target;
    this.enchantId = enchantId;
    this.level = level;
    this.result = result;
  }

  /** The item as it is after the roll; empty (air) when destroyed. */
  public ItemStack target() {
    return target;
  }

  public String enchantId() {
    return enchantId;
  }

  public int level() {
    return level;
  }

  public ApplyResult result() {
    return result;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
