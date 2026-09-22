package gg.ascent.plugin.item;

import gg.ascent.api.event.ItemRegisteredEvent;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemEventRecord;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRecord;
import gg.ascent.api.item.ItemRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** {@link ItemRegistry}: the persistent-data stamp plus {@link ItemAudit} for the rows. */
public final class ItemRegistryImpl implements ItemRegistry {

  private final Plugin plugin;
  private final ItemAudit audit;

  public ItemRegistryImpl(Plugin plugin, ItemAudit audit) {
    this.plugin = plugin;
    this.audit = audit;
  }

  @Override
  public ItemStack tag(
      ItemStack item,
      ItemKind kind,
      Map<String, Object> data,
      @Nullable UUID creator,
      String reason) {
    if (item == null || item.getType().isAir()) {
      throw new IllegalArgumentException("cannot tag an empty item");
    }
    if (ItemKeys.idOf(item).isPresent()) {
      throw new IllegalArgumentException("item is already tagged: " + ItemKeys.idOf(item).get());
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      throw new IllegalArgumentException(item.getType() + " cannot carry item meta");
    }
    UUID id = UuidV7.next();
    meta.getPersistentDataContainer()
        .set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id.toString());
    item.setItemMeta(meta);
    audit.created(id, kind, data, item.getAmount(), creator, reason);
    plugin
        .getServer()
        .getPluginManager()
        .callEvent(new ItemRegisteredEvent(id, kind, item, creator, reason));
    return item;
  }

  @Override
  public Optional<UUID> idOf(@Nullable ItemStack item) {
    return ItemKeys.idOf(item);
  }

  @Override
  public void record(
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable Map<String, Object> context) {
    audit.record(itemId, event, actor, counterparty, context);
  }

  @Override
  public CompletableFuture<Optional<ItemRecord>> lookup(UUID itemId) {
    return audit.lookup(itemId);
  }

  @Override
  public CompletableFuture<List<ItemEventRecord>> history(UUID itemId) {
    return audit.history(itemId);
  }

  /** The audit half, for the dupe scan and the debug command. */
  public ItemAudit audit() {
    return audit;
  }
}
