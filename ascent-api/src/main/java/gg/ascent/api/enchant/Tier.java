package gg.ascent.api.enchant;

/** Enchant book tiers, lowest to highest. Ordinal order is the tier order. */
public enum Tier {
  SIMPLE,
  UNIQUE,
  ELITE,
  ULTIMATE,
  LEGENDARY;

  public boolean isAtLeast(Tier other) {
    return ordinal() >= other.ordinal();
  }
}
