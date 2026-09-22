package gg.ascent.api.event;

import gg.ascent.api.item.ItemKind;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fired on the main thread right after an item is tagged (PRD §6.4.3). Not cancellable. */
public final class ItemRegisteredEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final UUID itemId;
  private final ItemKind kind;
  private final ItemStack item;
  private final @Nullable UUID creator;
  private final String reason;

  public ItemRegisteredEvent(
      UUID itemId, ItemKind kind, ItemStack item, @Nullable UUID creator, String reason) {
    this.itemId = itemId;
    this.kind = kind;
    this.item = item;
    this.creator = creator;
    this.reason = reason;
  }

  public UUID itemId() {
    return itemId;
  }

  public ItemKind kind() {
    return kind;
  }

  /** The tagged stack itself; listeners must not change its id. */
  public ItemStack item() {
    return item;
  }

  public @Nullable UUID creator() {
    return creator;
  }

  public String reason() {
    return reason;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
