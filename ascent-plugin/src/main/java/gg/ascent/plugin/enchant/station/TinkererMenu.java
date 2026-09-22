package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.enchant.BookLore;
import gg.ascent.plugin.enchant.BottleLore;
import gg.ascent.plugin.enchant.station.TinkererMath.Offer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The Tinkerer's two panes (PRD E3-S7): the left four columns take the player's items, the right
 * four show what each one returns, and the bottom row holds the confirm button. Rows 0 to 4 are the
 * panes; column 4 divides them.
 */
public final class TinkererMenu implements InventoryHolder {

  public static final int SIZE = 54;
  public static final int CONFIRM_SLOT = 49;
  private static final int PANE_ROWS = 5;
  private static final int OFFER_COLUMNS = 4;

  private final Inventory inventory;
  private final Messages messages;
  private final MiniMessage mini = MiniMessage.miniMessage();
  private boolean ready;
  private int totalLevels;

  public TinkererMenu(Messages messages) {
    this.messages = messages;
    this.inventory = Bukkit.createInventory(this, SIZE, messages.render("tinkerer.title"));
    paint();
  }

  private void paint() {
    ItemStack divider =
        MenuItems.icon(Material.GRAY_STAINED_GLASS_PANE, messages.render("tinkerer.divider"));
    for (int row = 0; row < PANE_ROWS; row++) {
      inventory.setItem(row * 9 + OFFER_COLUMNS, divider);
    }
    ItemStack filler =
        MenuItems.icon(Material.BLACK_STAINED_GLASS_PANE, messages.render("tinkerer.filler"));
    for (int slot = PANE_ROWS * 9; slot < SIZE; slot++) {
      inventory.setItem(slot, filler);
    }
    setConfirm(Material.GRAY_STAINED_GLASS_PANE, "tinkerer.confirm.empty", List.of());
  }

  /** Whether the player may put items into {@code slot}. */
  public static boolean isOfferSlot(int slot) {
    return slot >= 0 && slot < PANE_ROWS * 9 && slot % 9 < OFFER_COLUMNS;
  }

  /** The preview slot facing {@code offerSlot}. */
  public static int previewSlot(int offerSlot) {
    return offerSlot + OFFER_COLUMNS + 1;
  }

  public static List<Integer> offerSlots() {
    List<Integer> slots = new ArrayList<>();
    for (int slot = 0; slot < PANE_ROWS * 9; slot++) {
      if (isOfferSlot(slot)) {
        slots.add(slot);
      }
    }
    return slots;
  }

  /** The items in the left pane, by slot. */
  public Map<Integer, ItemStack> offered() {
    Map<Integer, ItemStack> out = new LinkedHashMap<>();
    for (int slot : offerSlots()) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && !item.getType().isAir()) {
        out.put(slot, item);
      }
    }
    return out;
  }

  /** Recomputes the right pane and the confirm button from the left pane. */
  public void refresh(TinkererValuer valuer, EnchantsSettings settings) {
    int items = 0;
    int levels = 0;
    boolean blocked = false;
    for (int slot : offerSlots()) {
      ItemStack item = inventory.getItem(slot);
      if (item == null || item.getType().isAir()) {
        inventory.setItem(previewSlot(slot), null);
        continue;
      }
      Optional<Offer> offer = valuer.value(item);
      if (offer.isEmpty()) {
        blocked = true;
        inventory.setItem(
            previewSlot(slot),
            MenuItems.icon(
                Material.BARRIER,
                messages.render("tinkerer.preview.rejected"),
                List.of(messages.render("tinkerer.preview.rejected-hint"))));
        continue;
      }
      items++;
      levels += offer.get().totalLevels();
      inventory.setItem(previewSlot(slot), preview(offer.get(), settings));
    }
    this.totalLevels = levels;
    this.ready = items > 0 && !blocked;
    if (items == 0 && !blocked) {
      setConfirm(Material.GRAY_STAINED_GLASS_PANE, "tinkerer.confirm.empty", List.of());
    } else if (blocked) {
      setConfirm(Material.RED_STAINED_GLASS_PANE, "tinkerer.confirm.blocked", List.of());
    } else {
      setConfirm(
          Material.LIME_STAINED_GLASS_PANE,
          "tinkerer.confirm.ready",
          List.of(
              messages.render(
                  "tinkerer.confirm.ready-lore",
                  Placeholder.unparsed("items", String.valueOf(items)),
                  Placeholder.unparsed("levels", String.valueOf(levels)))));
    }
  }

  private ItemStack preview(Offer offer, EnchantsSettings settings) {
    List<Component> lore = new ArrayList<>();
    lore.add(
        messages.render(
            "tinkerer.preview.bottle",
            Placeholder.unparsed("levels", String.valueOf(offer.totalLevels()))));
    if (offer.dustTier() != null) {
      lore.add(
          messages.render(
              "tinkerer.preview.dust",
              Placeholder.unparsed(
                  "chance", String.valueOf(settings.tinkerer().dustChancePercent())),
              Placeholder.unparsed("tier", BookLore.tierName(offer.dustTier()))));
    }
    ItemStack icon =
        MenuItems.icon(
            Material.EXPERIENCE_BOTTLE,
            mini.deserialize(BottleLore.name(offer.levelsEach())),
            lore);
    icon.setAmount(Math.max(1, Math.min(64, offer.amount())));
    return icon;
  }

  private void setConfirm(Material material, String key, List<Component> lore) {
    inventory.setItem(CONFIRM_SLOT, MenuItems.icon(material, messages.render(key), lore));
  }

  /** Whether the left pane holds only accepted items, at least one. */
  public boolean ready() {
    return ready;
  }

  public int totalLevels() {
    return totalLevels;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
