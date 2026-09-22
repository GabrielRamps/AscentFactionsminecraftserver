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
