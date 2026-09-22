package gg.ascent.api.item;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Every valuable item carries a unique id and every important event on it is logged, so dupes can
 * be detected and rolled back (PRD E1-S5).
 *
 * <p>Tagged kinds: enchant books, scrolls, dust, spawners, any gear with a custom enchant, kit
 * items above Simple tier. A stackable item shares one id per stack creation; splitting a stack
 * keeps the id, and the dupe scan only alerts when the total across inventories exceeds what was
 * created.
 *
 * <p>Main thread only. Rows are written asynchronously; nothing here waits on the database.
 */
public interface ItemRegistry {

  /**
   * Stamps a fresh UUIDv7 onto {@code item}, records the {@code items} row with a CREATED event,
   * and fires {@code ItemRegisteredEvent}.
   *
   * @param item the stack to tag; it is modified in place and also returned
   * @param kind what it is
   * @param data kind-specific details, stored as JSON; may be empty
   * @param creator who caused it to exist, or null for the server
   * @param reason a short reason such as {@code enchanter}, {@code kit:pvp} or {@code admin}
   * @return the same stack, tagged
   * @throws IllegalArgumentException if the item is empty (air) or already tagged
   */
  ItemStack tag(
      ItemStack item,
      ItemKind kind,
      Map<String, Object> data,
      @Nullable UUID creator,
      String reason);

  /** The id on {@code item}, or empty if it is not tagged. */
  Optional<UUID> idOf(@Nullable ItemStack item);

  /** Logs an event on a tagged item. Never blocks. */
  void record(
      UUID itemId,
      ItemEvent event,
      @Nullable UUID actor,
      @Nullable UUID counterparty,
      @Nullable Map<String, Object> context);

  /** The item's row, or empty if the id is unknown. Completes on the main thread. */
  CompletableFuture<Optional<ItemRecord>> lookup(UUID itemId);

  /** The item's full history, oldest first. Completes on the main thread. */
  CompletableFuture<List<ItemEventRecord>> history(UUID itemId);
}
