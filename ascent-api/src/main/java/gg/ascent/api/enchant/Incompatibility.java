package gg.ascent.api.enchant;

/** Why a book cannot go on an item (PRD E3-S3), for the red message. */
public enum Incompatibility {
  /** Not a book, or not gear. */
  NOT_GEAR,
  /** The enchant does not apply to this kind of gear. */
  WRONG_ITEM,
  /** The item already carries as many custom enchants as the player's rank allows. */
  SLOTS_FULL,
  /** The item already has this enchant at this level or higher. */
  ALREADY_HAS
}
