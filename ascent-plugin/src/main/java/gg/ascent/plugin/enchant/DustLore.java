package gg.ascent.plugin.enchant;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.Tier;
import java.util.List;

/** Text and math for White Scrolls and Magic Dust (PRD E3-S5). Pure. */
public final class DustLore {

  /** Dust at or above this many points gets the loud name color. */
  public static final int STRONG_DUST = 10;

  private DustLore() {}

  public static String scrollName() {
    return "<white><bold>White Scroll</bold>";
  }

  public static List<String> scrollLore() {
    return List.of(
        "<gray>Drag onto gear to protect it.",
        "<gray>If a book would destroy the item, the scroll is",
        "<gray>consumed instead and the item survives.",
        "<dark_gray>One scroll per item.");
  }

  /** Strong dust shouts; ordinary dust takes the tier color. */
  public static String dustName(EnchantsSettings settings, Tier tier, int percent) {
    String color = percent >= STRONG_DUST ? "<light_purple>" : settings.tier(tier).color();
    return color
        + "<bold>"
        + BookLore.tierName(tier)
        + " Magic Dust</bold> <gray>+"
        + percent
        + "%";
  }

  public static List<String> dustLore(EnchantsSettings settings, Tier tier, int percent) {
    return List.of(
        "<gray>Adds <green>+" + percent + "%</green> success to an opened",
        settings.tier(tier).color() + BookLore.tierName(tier) + "</gray> book.",
        "<dark_gray>Drag onto the book to use.");
  }

  /** {@code min(100, success + percent)}. */
  public static int boostedSuccess(int success, int percent) {
    return Math.min(100, success + Math.max(0, percent));
  }
}
