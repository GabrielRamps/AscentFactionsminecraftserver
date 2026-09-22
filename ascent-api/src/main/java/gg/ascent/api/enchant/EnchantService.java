package gg.ascent.api.enchant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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

  /**
   * An opened book carrying exactly {@code book}, registry-tagged: what the Alchemist hands out
   * (PRD E3-S8) and what staff give.
   *
   * @throws IllegalArgumentException if the enchant id is not in the catalog
   */
  ItemStack createOpenedBook(OpenedBook book, @Nullable UUID creator, String reason);

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

  /** Extra rank XP percent a tool grants for this block, after its chance roll; 0 for none. */
  double bonusXpPercent(@Nullable ItemStack tool);

  // --- White Scrolls and Magic Dust (PRD E3-S5) --------------------------------------------

  /** A registry-tagged White Scroll stack. */
  ItemStack createWhiteScroll(int amount, @Nullable UUID creator, String reason);

  /**
   * A registry-tagged Magic Dust stack of {@code tier} adding {@code percent} (1 to 15) success.
   */
  ItemStack createMagicDust(
      Tier tier, int percent, int amount, @Nullable UUID creator, String reason);

  boolean isWhiteScroll(@Nullable ItemStack item);

  Optional<MagicDust> magicDust(@Nullable ItemStack item);

  /** Whether a White Scroll protects the gear. */
  boolean isProtected(@Nullable ItemStack gear);

  /**
   * Protects {@code gear} with one scroll from {@code scroll}. Nothing happens, and false is
   * answered, when the item is not gear or is already protected.
   */
  boolean applyScroll(ItemStack scroll, ItemStack gear, @Nullable UUID actor);

  /**
   * Adds one dust's percent to an opened book's success chance, capped at 100. Nothing happens, and
   * false is answered, unless the dust and the book share a tier and the book is below 100.
   */
  boolean applyDust(ItemStack dust, ItemStack book, @Nullable UUID actor);

  /**
   * @param tier the book tier the dust works on
   * @param percent success points it adds, 1 to 15
   */
  record MagicDust(Tier tier, int percent) {}

  // --- XP bottles (PRD E3-S7) ---------------------------------------------------------------

  /**
   * A registry-tagged XP bottle: a custom item that grants {@code levels} vanilla XP levels when
   * right-clicked, never a thrown vanilla bottle. What the Tinkerer pays in.
   */
  ItemStack createXpBottle(int levels, int amount, @Nullable UUID creator, String reason);

  /** The levels an XP bottle grants, or empty for anything else. */
  OptionalInt xpBottleLevels(@Nullable ItemStack item);

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
