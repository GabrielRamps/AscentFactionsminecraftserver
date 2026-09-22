package gg.ascent.plugin.enchant.station;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Icons and buttons for the station menus. */
final class MenuItems {

  private MenuItems() {}

  static ItemStack icon(Material material, Component name, List<Component> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(plain(name));
      List<Component> lines = new ArrayList<>();
      for (Component line : lore) {
        lines.add(plain(line));
      }
      meta.lore(lines);
      meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
      item.setItemMeta(meta);
    }
    return item;
  }

  static ItemStack icon(Material material, Component name) {
    return icon(material, name, List.of());
  }

  static Component plain(Component c) {
    return c.decoration(TextDecoration.ITALIC, false);
  }
}
