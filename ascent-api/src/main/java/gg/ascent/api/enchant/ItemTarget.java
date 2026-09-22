package gg.ascent.api.enchant;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** What gear an enchant may sit on (PRD E3-S1 {@code applies_to}). */
public enum ItemTarget {
  HELMET,
  CHESTPLATE,
  LEGGINGS,
  BOOTS,
  SWORD,
  AXE,
  BOW,
  PICKAXE,
  HOE,
  /** Every armor piece. */
  ALL_ARMOR,
  /** Swords, axes and bows. */
  ALL_WEAPONS;

  /** The concrete targets this covers; a concrete target covers only itself. */
  public Set<ItemTarget> expand() {
    return switch (this) {
      case ALL_ARMOR -> EnumSet.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
      case ALL_WEAPONS -> EnumSet.of(SWORD, AXE, BOW);
      default -> EnumSet.of(this);
    };
  }

  /** Whether {@code concrete} is one of the targets this covers. */
  public boolean covers(ItemTarget concrete) {
    return expand().contains(concrete);
  }

  /**
   * The concrete target for a Bukkit material name such as {@code DIAMOND_SWORD}, or empty for
   * anything that cannot carry custom enchants. Name-based so it works without a server.
   */
  public static Optional<ItemTarget> classify(String materialName) {
    String name = materialName.toUpperCase(Locale.ROOT);
    if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) {
      return Optional.of(HELMET);
    }
    if (name.endsWith("_CHESTPLATE")) {
      return Optional.of(CHESTPLATE);
    }
    if (name.endsWith("_LEGGINGS")) {
      return Optional.of(LEGGINGS);
    }
    if (name.endsWith("_BOOTS")) {
      return Optional.of(BOOTS);
    }
    if (name.endsWith("_SWORD")) {
      return Optional.of(SWORD);
    }
    if (name.endsWith("_AXE")) {
      return Optional.of(AXE);
    }
    if (name.equals("BOW") || name.equals("CROSSBOW")) {
      return Optional.of(BOW);
    }
    if (name.endsWith("_PICKAXE")) {
      return Optional.of(PICKAXE);
    }
    if (name.endsWith("_HOE")) {
      return Optional.of(HOE);
    }
    return Optional.empty();
  }
}
