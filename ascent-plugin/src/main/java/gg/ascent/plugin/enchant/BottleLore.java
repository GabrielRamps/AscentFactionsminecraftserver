package gg.ascent.plugin.enchant;

import java.util.List;

/** Text for XP bottles (PRD E3-S7). Pure. */
public final class BottleLore {

  private BottleLore() {}

  public static String name(int levels) {
    return "<green><bold>XP Bottle</bold> <gray>" + levels + " levels";
  }

  public static List<String> lore(int levels) {
    return List.of(
        "<gray>Right-click to gain <green>" + levels + "</green> XP levels.",
        "<dark_gray>Sneak to drink the whole stack.");
  }
}
