package gg.ascent.plugin.kit;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.KitsSettings;
import gg.ascent.api.kit.KitService;
import gg.ascent.api.kit.KitService.KitStatus;
import gg.ascent.api.message.Messages;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code /kit} GUI (PRD E2-S3): one icon per kit with its rank gate and cooldown. The holder
 * pattern lets {@link KitListener} recognise the menu and map a click back to a kit id.
 */
public final class KitMenu implements InventoryHolder {

  private final Inventory inventory;
  private final List<String> slotKits = new ArrayList<>();

  private KitMenu(int size, Component title) {
    this.inventory = Bukkit.createInventory(this, size, title);
  }

  /** Builds the menu for {@code player} from the current statuses. */
  public static KitMenu build(
      Player player, KitService kits, ConfigService config, Messages messages) {
    List<KitStatus> statuses = kits.kits(player.getUniqueId());
    int rows = Math.max(1, (statuses.size() + 8) / 9);
    KitMenu menu = new KitMenu(rows * 9, messages.render("kit.menu.title"));
    MiniMessage mini = MiniMessage.miniMessage();
    for (KitStatus status : statuses) {
      KitsSettings.Kit kit = config.kits().kits().get(status.id());
      ItemStack icon = new ItemStack(iconFor(kit, status));
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plain(mini.deserialize(status.display())));
        List<Component> lore = new ArrayList<>();
        if (!status.unlocked()) {
          lore.add(
              plain(
                  messages.render(
                      "kit.menu.locked",
                      Placeholder.unparsed("rank", String.valueOf(status.minRank())))));
        } else if (!status.cooldownLeft().isZero()) {
          lore.add(
              plain(
                  messages.render(
                      "kit.menu.cooldown",
                      Placeholder.unparsed("time", Durations.humanize(status.cooldownLeft())))));
        } else {
          lore.add(plain(messages.render("kit.menu.ready")));
        }
        for (KitsSettings.KitItem item : kit.items()) {
          lore.add(
              plain(
                  messages.render(
                      "kit.menu.item",
                      Placeholder.unparsed("amount", String.valueOf(item.amount())),
                      Placeholder.unparsed("item", pretty(item.material())))));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
      }
      menu.inventory.setItem(menu.slotKits.size(), icon);
      menu.slotKits.add(status.id());
    }
    return menu;
  }

  /** The kit id in {@code slot}, or null for an empty slot. */
  public @Nullable String kitAt(int slot) {
    return slot >= 0 && slot < slotKits.size() ? slotKits.get(slot) : null;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  private static Material iconFor(KitsSettings.Kit kit, KitStatus status) {
    if (!status.unlocked()) {
      return Material.BARRIER;
    }
    for (KitsSettings.KitItem item : kit.items()) {
      Material m = Material.matchMaterial(item.material());
      if (m != null && !m.isAir()) {
        return m;
      }
    }
    return Material.CHEST;
  }

  /** {@code DIAMOND_SWORD} reads as {@code Diamond Sword}. */
  static String pretty(String material) {
    StringBuilder out = new StringBuilder();
    for (String word : material.toLowerCase(java.util.Locale.ROOT).split("_")) {
      if (word.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return out.toString();
  }

  private static Component plain(Component c) {
    return c.decoration(TextDecoration.ITALIC, false);
  }
}
