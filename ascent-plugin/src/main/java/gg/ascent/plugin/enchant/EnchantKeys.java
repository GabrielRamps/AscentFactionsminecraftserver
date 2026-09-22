package gg.ascent.plugin.enchant;

import org.bukkit.NamespacedKey;

/** Persistent-data keys for the lottery (PRD E3-S2, E3-S3, E3-S5). */
public final class EnchantKeys {

  /** Unopened book: the tier name. */
  public static final NamespacedKey BOOK_TIER = new NamespacedKey("ascent", "book_tier");

  /** Opened book: the enchant id. */
  public static final NamespacedKey ENCHANT_ID = new NamespacedKey("ascent", "enchant_id");

  /** Opened book: the level. */
  public static final NamespacedKey LEVEL = new NamespacedKey("ascent", "level");

  /** Opened book: success percent. */
  public static final NamespacedKey SUCCESS = new NamespacedKey("ascent", "success");

  /** Opened book: destroy percent. */
  public static final NamespacedKey DESTROY = new NamespacedKey("ascent", "destroy");

  /** Gear: the applied enchants as a JSON object of id to level. */
  public static final NamespacedKey ENCHANTS = new NamespacedKey("ascent", "enchants");

  /** Gear: 1 when a White Scroll protects the item. */
  public static final NamespacedKey WHITE_SCROLL = new NamespacedKey("ascent", "white_scroll");

  /** The White Scroll item itself: 1. */
  public static final NamespacedKey SCROLL_ITEM = new NamespacedKey("ascent", "scroll_item");

  /** Magic Dust: the tier it works on. */
  public static final NamespacedKey DUST_TIER = new NamespacedKey("ascent", "dust_tier");

  /** Magic Dust: the success points it adds. */
  public static final NamespacedKey DUST_PERCENT = new NamespacedKey("ascent", "dust_percent");

  /** XP bottle: the vanilla XP levels it grants. */
  public static final NamespacedKey XP_LEVELS = new NamespacedKey("ascent", "xp_levels");

  private EnchantKeys() {}
}
