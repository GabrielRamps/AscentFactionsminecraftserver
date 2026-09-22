package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.MagicDust;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.enchant.BookLore;
import gg.ascent.plugin.enchant.DustLore;
import gg.ascent.plugin.enchant.station.AlchemistMath.BookFusion;
import gg.ascent.plugin.enchant.station.AlchemistMath.DustFusion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Alchemist (PRD E3-S8): two input slots, a preview of the result, and a Combine button. The
 * preview is a picture, never a real item; the result goes to the player's inventory on confirm.
 */
public final class AlchemistMenu implements InventoryHolder {

  public static final int SIZE = 27;
  public static final int FIRST_SLOT = 10;
  public static final int SECOND_SLOT = 12;
  public static final int OUTPUT_SLOT = 16;
  public static final int CONFIRM_SLOT = 22;
  public static final List<Integer> INPUT_SLOTS = List.of(FIRST_SLOT, SECOND_SLOT);

  private final Inventory inventory;
  private final Messages messages;
  private final MiniMessage mini = MiniMessage.miniMessage();
  private @Nullable BookFusion book;
  private @Nullable DustFusion dust;

  public AlchemistMenu(Messages messages) {
    this.messages = messages;
    this.inventory = Bukkit.createInventory(this, SIZE, messages.render("alchemist.title"));
    paint();
  }

  private void paint() {
    ItemStack filler =
        MenuItems.icon(Material.BLACK_STAINED_GLASS_PANE, messages.render("alchemist.filler"));
    for (int slot = 0; slot < SIZE; slot++) {
      if (!INPUT_SLOTS.contains(slot) && slot != OUTPUT_SLOT && slot != CONFIRM_SLOT) {
        inventory.setItem(slot, filler);
      }
    }
    inventory.setItem(
        FIRST_SLOT + 1,
        MenuItems.icon(Material.GRAY_STAINED_GLASS_PANE, messages.render("alchemist.plus")));
    ItemStack arrow =
        MenuItems.icon(Material.GRAY_STAINED_GLASS_PANE, messages.render("alchemist.arrow"));
    for (int slot = SECOND_SLOT + 1; slot < OUTPUT_SLOT; slot++) {
      inventory.setItem(slot, arrow);
    }
  }

  public static boolean isInputSlot(int slot) {
    return slot == FIRST_SLOT || slot == SECOND_SLOT;
  }

  public Optional<BookFusion> book() {
    return Optional.ofNullable(book);
  }

  public Optional<DustFusion> dust() {
    return Optional.ofNullable(dust);
  }

  public int cost() {
    return book != null ? book.costLevels() : dust != null ? dust.costLevels() : 0;
  }

  /** Recomputes the preview and the button from the two input slots. */
  public void refresh(EnchantService enchants, EnchantsSettings settings, int playerLevels) {
    book = null;
    dust = null;
    ItemStack a = inventory.getItem(FIRST_SLOT);
    ItemStack b = inventory.getItem(SECOND_SLOT);
    boolean empty = a == null || a.getType().isAir() || b == null || b.getType().isAir();
    if (empty) {
      inventory.setItem(
          OUTPUT_SLOT,
          MenuItems.icon(
              Material.GRAY_STAINED_GLASS_PANE, messages.render("alchemist.preview.empty")));
      setConfirm(Material.GRAY_STAINED_GLASS_PANE, "alchemist.confirm.empty", List.of());
      return;
    }
    Optional<OpenedBook> bookA = enchants.openedBook(a);
    Optional<OpenedBook> bookB = enchants.openedBook(b);
    Optional<MagicDust> dustA = enchants.magicDust(a);
    Optional<MagicDust> dustB = enchants.magicDust(b);
    ItemStack preview = null;
    if (bookA.isPresent() && bookB.isPresent()) {
      Optional<BookFusion> fusion = AlchemistMath.fuseBooks(settings, bookA.get(), bookB.get());
      if (fusion.isPresent()) {
        book = fusion.get();
        preview = bookPreview(settings, book);
      }
    } else if (dustA.isPresent() && dustB.isPresent()) {
      Optional<DustFusion> fusion = AlchemistMath.fuseDust(settings, dustA.get(), dustB.get());
      if (fusion.isPresent()) {
        dust = fusion.get();
        preview = dustPreview(settings, dust);
      }
    }
    if (preview == null) {
      inventory.setItem(
          OUTPUT_SLOT,
          MenuItems.icon(
              Material.BARRIER,
              messages.render("alchemist.preview.mismatch"),
              List.of(
                  messages.render("alchemist.preview.mismatch-hint"),
                  messages.render("alchemist.preview.mismatch-hint2"))));
      setConfirm(Material.RED_STAINED_GLASS_PANE, "alchemist.confirm.blocked", List.of());
      return;
    }
    inventory.setItem(OUTPUT_SLOT, preview);
    setConfirm(
        Material.LIME_STAINED_GLASS_PANE,
        "alchemist.confirm.ready",
        List.of(
            messages.render(
                "alchemist.confirm.ready-lore",
                Placeholder.unparsed("cost", String.valueOf(cost())),
                Placeholder.unparsed("levels", String.valueOf(playerLevels)))));
  }

  private ItemStack bookPreview(EnchantsSettings settings, BookFusion fusion) {
    EnchantDefinition def = settings.enchant(fusion.result().enchantId()).orElseThrow();
    List<Component> lore = new ArrayList<>();
    for (String line : BookLore.openedLore(def, fusion.result())) {
      lore.add(mini.deserialize(line));
    }
    lore.add(Component.empty());
    lore.add(costLine(fusion.costLevels()));
    return MenuItems.icon(
        Material.ENCHANTED_BOOK,
        mini.deserialize(BookLore.openedName(settings, def, fusion.result().level())),
        lore);
  }

  private ItemStack dustPreview(EnchantsSettings settings, DustFusion fusion) {
    MagicDust d = fusion.result();
    List<Component> lore = new ArrayList<>();
    for (String line : DustLore.dustLore(settings, d.tier(), d.percent())) {
      lore.add(mini.deserialize(line));
    }
    lore.add(Component.empty());
    lore.add(costLine(fusion.costLevels()));
    return MenuItems.icon(
        Material.GLOWSTONE_DUST,
        mini.deserialize(DustLore.dustName(settings, d.tier(), d.percent())),
        lore);
  }

  private Component costLine(int cost) {
    return messages.render(
        "alchemist.preview.cost", Placeholder.unparsed("cost", String.valueOf(cost)));
  }

  private void setConfirm(Material material, String key, List<Component> lore) {
    inventory.setItem(CONFIRM_SLOT, MenuItems.icon(material, messages.render(key), lore));
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
