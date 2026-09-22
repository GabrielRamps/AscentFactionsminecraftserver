package gg.ascent.plugin.item;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/** The persistent-data keys Ascent stamps on items. */
public final class ItemKeys {

  /** The registry id, as a 36-character UUID string (PRD E1-S5). */
  public static final NamespacedKey ITEM_ID = new NamespacedKey("ascent", "item_id");

  private ItemKeys() {}

  /** The registry id on {@code item}, or empty for air, untagged or unreadable items. */
  public static Optional<UUID> idOf(@Nullable ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return Optional.empty();
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return Optional.empty();
    }
    String raw = meta.getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
    if (raw == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
