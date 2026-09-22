package gg.ascent.plugin.enchant.station;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The click rules shared by station menus that hold the player's items: only input slots accept
 * items, the confirm slot is a button, everything else is decoration, and whatever is left in an
 * input slot goes back to the player when the menu closes. After every change the menu is refreshed
 * on the next tick, once Bukkit has moved the items.
 */
public abstract class StationListener<M extends InventoryHolder> implements Listener {

  private final Plugin plugin;
  private final Class<M> type;

  StationListener(Plugin plugin, Class<M> type) {
    this.plugin = plugin;
    this.type = type;
  }

  protected abstract boolean isInputSlot(int slot);

  protected abstract boolean isConfirmSlot(int slot);

  protected abstract Collection<Integer> inputSlots();

  protected abstract void refresh(M menu, Player player);

  protected abstract void confirm(M menu, Player player);

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();
    if (!type.isInstance(holder) || !(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    M menu = type.cast(holder);
    Inventory clicked = event.getClickedInventory();
    InventoryAction action = event.getAction();
    if (clicked != null && clicked.getHolder() == holder) {
      int slot = event.getSlot();
      if (isConfirmSlot(slot)) {
        event.setCancelled(true);
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
          confirm(menu, player);
        }
        return;
      }
      if (!isInputSlot(slot)
          || action == InventoryAction.COLLECT_TO_CURSOR
          || action == InventoryAction.UNKNOWN) {
        event.setCancelled(true);
        return;
      }
    } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      // Shift-click from the player's inventory: vanilla would drop the stack into the first
      // free slot of the menu, decoration included. Route it into the input slots ourselves.
      event.setCancelled(true);
      ItemStack moving = event.getCurrentItem();
      if (moving != null && !moving.getType().isAir()) {
        event.setCurrentItem(moveIntoInputs(event.getInventory(), moving));
      }
    } else if (action == InventoryAction.COLLECT_TO_CURSOR) {
      event.setCancelled(true); // would gather from the decoration
      return;
    }
    scheduleRefresh(menu, player);
  }

  /**
   * Puts as much of {@code stack} as fits into the input slots, topping up matching stacks first
   * and then filling empty slots in order.
   *
   * @return what did not fit, or null when everything moved
   */
  private ItemStack moveIntoInputs(Inventory menu, ItemStack stack) {
    ItemStack moving = stack.clone();
    for (int slot : inputSlots()) {
      ItemStack present = menu.getItem(slot);
      if (present == null || present.getType().isAir() || !present.isSimilar(moving)) {
        continue;
      }
      int room = present.getMaxStackSize() - present.getAmount();
      if (room <= 0) {
        continue;
      }
      int take = Math.min(room, moving.getAmount());
      present.setAmount(present.getAmount() + take);
      menu.setItem(slot, present);
      moving.setAmount(moving.getAmount() - take);
      if (moving.getAmount() <= 0) {
        return null;
      }
    }
    for (int slot : inputSlots()) {
      ItemStack present = menu.getItem(slot);
      if (present == null || present.getType().isAir()) {
        menu.setItem(slot, moving);
        return null;
      }
    }
    return moving;
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();
    if (!type.isInstance(holder) || !(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    int size = event.getInventory().getSize();
    for (int raw : event.getRawSlots()) {
      if (raw < size && !isInputSlot(raw)) {
        event.setCancelled(true);
        return;
      }
    }
    scheduleRefresh(type.cast(holder), player);
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();
    if (!type.isInstance(holder) || !(event.getPlayer() instanceof Player player)) {
      return;
    }
    Inventory inventory = event.getInventory();
    for (int slot : inputSlots()) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && !item.getType().isAir()) {
        inventory.setItem(slot, null);
        give(player, item);
      }
    }
  }

  /** Puts {@code item} in the player's inventory, dropping what does not fit. */
  static void give(Player player, ItemStack item) {
    for (ItemStack rest : player.getInventory().addItem(item).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }
  }

  private void scheduleRefresh(M menu, Player player) {
    plugin
        .getServer()
        .getScheduler()
        .runTask(
            plugin,
            () -> {
              if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
                refresh(menu, player);
              }
            });
  }
}
