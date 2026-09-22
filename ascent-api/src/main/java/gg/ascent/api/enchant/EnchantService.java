package gg.ascent.api.enchant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The book lottery (PRD Epic 3): unopened tier books, the reveal, and reading what is on an item.
 * Applying a book arrives with E3-S3. Main thread.
 */
public interface EnchantService {

  /** A registry-tagged unopened book of {@code tier}, one per stack; {@code amount} copies. */
  ItemStack createUnopenedBook(Tier tier, int amount, @Nullable UUID creator, String reason);

  /**
   * Rolls one unopened book into an opened one: a random enchant of its tier, a level weighted
   * toward the low end, and success and destroy chances from the tier's ranges. The unopened stack
   * passed in is not modified; the caller removes one from it.
   *
   * @return the opened book
   * @throws IllegalArgumentException if {@code unopened} is not an unopened book
   */
  ItemStack reveal(ItemStack unopened, @Nullable UUID player);

  /** The tier of an unopened book, or empty for anything else. */
  Optional<Tier> unopenedTier(@Nullable ItemStack item);

  /** What an opened book carries, or empty for anything else. */
  Optional<OpenedBook> openedBook(@Nullable ItemStack item);

  /** Custom enchants on a piece of gear, id to level; empty for none. */
  Map<String, Integer> getEnchants(@Nullable ItemStack item);

  /**
   * Whether {@code book} may be rolled onto {@code target} by a player of {@code rank}: the gear
   * type matches, a slot is free, and the item does not already carry this enchant at this level or
   * higher. Empty when compatible.
   */
  Optional<Incompatibility> checkCompatible(ItemStack book, ItemStack target, int rank);

  /**
   * Drags {@code book} onto {@code target} for one honest roll (PRD E3-S3). The book stack and the
   * target are modified in place: on any outcome but INCOMPATIBLE one book is consumed; on
   * DESTROYED the target's amount becomes 0. Messages and sounds are the caller's job; the roll,
   * the lore, the registry events and the {@code enchant_rolls} row are done here.
   */
  ApplyResult apply(ItemStack book, ItemStack target, org.bukkit.entity.Player player);

  /**
   * Puts an enchant on gear without a roll, for kits and staff. Tags the item and rebuilds its
   * lore. Ignores unknown ids and out-of-range levels.
   *
   * @return whether anything changed
   */
  boolean setEnchant(
      ItemStack target, String enchantId, int level, @Nullable UUID actor, String reason);

  /** Rebuilds the item's lore from what it carries. The one place gear lore is written. */
  void refreshLore(ItemStack gear);

  Optional<EnchantDefinition> definition(String enchantId);

  /** The catalog for one tier, in file order. */
  List<EnchantDefinition> pool(Tier tier);

  /**
   * @param enchantId the catalog id
   * @param level the level it applies
   * @param success percent chance the apply roll succeeds
   * @param destroy percent chance a failed roll destroys the item
   */
  record OpenedBook(String enchantId, int level, int success, int destroy) {}
}
