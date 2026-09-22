package gg.ascent.api.item;

/** Something that happened to a tagged item. Mirrors the {@code item_events.event} enum. */
public enum ItemEvent {
  CREATED,
  APPLIED,
  CONSUMED,
  DESTROYED,
  TRADED,
  DROPPED,
  PICKED_UP,
  SOLD,
  ADMIN_GIVE,
  ROLLBACK
}
