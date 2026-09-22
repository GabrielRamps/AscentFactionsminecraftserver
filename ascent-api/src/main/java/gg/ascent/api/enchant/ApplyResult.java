package gg.ascent.api.enchant;

/** How dragging a book onto gear ended (PRD E3-S3). */
public enum ApplyResult {
  /** The roll succeeded: the enchant is on the item. */
  SUCCESS,
  /** The roll failed and the destroy roll hit: the item is gone. */
  DESTROYED,
  /** The roll failed and the destroy roll hit, but a White Scroll took the blow. */
  SCROLL_SAVED,
  /** The roll failed and the destroy roll missed: the book is gone, the item unchanged. */
  FAILED,
  /** The book does not fit this item; nothing was consumed. */
  INCOMPATIBLE;

  /** Whether the book was consumed. */
  public boolean consumedBook() {
    return this != INCOMPATIBLE;
  }
}
