package gg.ascent.plugin.enchant;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.message.Messages;
import gg.ascent.api.rank.UnlockService;
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
 * The Enchanter (PRD E3-S6): one slot per tier the player has unlocked, showing the cost in vanilla
 * XP levels. Left-click buys 1, right-click 8, shift-click 16.
 */
public final class EnchanterMenu implements InventoryHolder {

  private final Inventory inventory;
  private final List<Tier> slotTiers = new ArrayList<>();

  private EnchanterMenu(Component title) {
    this.inventory = Bukkit.createInventory(this, 9, title);
  }

  public static EnchanterMenu build(
      Player player,
      int rank,
      EnchantsSettings settings,
      UnlockService unlocks,
      Messages messages) {
    EnchanterMenu menu = new EnchanterMenu(messages.render("enchanter.title"));
    MiniMessage mini = MiniMessage.miniMessage();
    Tier max = unlocks.maxBookTier(rank);
    int slot = 2;
    for (Tier tier : Tier.values()) {
      boolean unlocked = max.isAtLeast(tier);
      int cost = settings.tier(tier).xpLevels();
      ItemStack icon = new ItemStack(unlocked ? Material.BOOK : Material.BARRIER);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plain(mini.deserialize(BookLore.unopenedName(settings, tier))));
        List<Component> lore = new ArrayList<>();
        var costTag = Placeholder.unparsed("cost", String.valueOf(cost));
        var levels = Placeholder.unparsed("levels", String.valueOf(player.getLevel()));
        if (unlocked) {
          lore.add(plain(messages.render("enchanter.lore.cost", costTag)));
          lore.add(plain(messages.render("enchanter.lore.have", levels)));
          lore.add(plain(messages.render("enchanter.lore.buy")));
        } else {
          lore.add(
              plain(
                  messages.render(
                      "enchanter.lore.locked",
                      Placeholder.unparsed(
                          "rank", String.valueOf(settings.tier(tier).minRank())))));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
      }
      menu.inventory.setItem(slot, icon);
      while (menu.slotTiers.size() < slot) {
        menu.slotTiers.add(null);
      }
      menu.slotTiers.add(unlocked ? tier : null);
      slot++;
    }
    return menu;
  }

  /** The tier bought by clicking {@code slot}, or null for a locked or empty slot. */
  public @Nullable Tier tierAt(int slot) {
    return slot >= 0 && slot < slotTiers.size() ? slotTiers.get(slot) : null;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  private static Component plain(Component c) {
    return c.decoration(TextDecoration.ITALIC, false);
  }
}
