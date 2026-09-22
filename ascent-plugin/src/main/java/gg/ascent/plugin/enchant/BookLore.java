package gg.ascent.plugin.enchant;

import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService.OpenedBook;
import gg.ascent.api.enchant.ItemTarget;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.item.LoreRenderer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The exact text on books (PRD E3-S2), as MiniMessage strings. Pure. */
public final class BookLore {

  private BookLore() {}

  /** {@code <tier_color><bold>Legendary Book</bold>} */
  public static String unopenedName(EnchantsSettings settings, Tier tier) {
    return settings.tier(tier).color() + "<bold>" + tierName(tier) + " Book</bold>";
  }

  public static List<String> unopenedLore(EnchantsSettings settings, Tier tier) {
    return List.of(
        "<gray>Right-click to reveal a random "
            + settings.tier(tier).color()
            + tierName(tier)
            + "</gray> enchant.",
        "<dark_gray>Sneak + right-click to reveal the whole stack.");
  }

  /** {@code <tier_color><bold>Lifesteal III</bold>} */
  public static String openedName(EnchantsSettings settings, EnchantDefinition enchant, int level) {
    return settings.tier(enchant.tier()).color()
        + "<bold>"
        + enchant.display()
        + " "
        + LoreRenderer.roman(level)
        + "</bold>";
  }

  /**
   *
   *
   * <pre>
   * &lt;green&gt;100% Success Rate
   * &lt;red&gt;45% Destroy Rate
   * &lt;gray&gt;Applies to: Swords
   * &lt;gray&gt;Heals 1.5 hearts on hit (12% chance)
   * &lt;dark_gray&gt;Drag and drop onto item to apply.
   * </pre>
   */
  public static List<String> openedLore(EnchantDefinition enchant, OpenedBook book) {
    List<String> out = new ArrayList<>();
    out.add("<green>" + book.success() + "% Success Rate");
    out.add("<red>" + book.destroy() + "% Destroy Rate");
    out.add("<gray>Applies to: " + appliesTo(enchant.appliesTo()));
    out.add("<gray>" + enchant.description(book.level()));
    out.add("<dark_gray>Drag and drop onto item to apply.");
    return out;
  }

  /** {@code Swords}, {@code Swords, Axes}, {@code All Armor}. */
  public static String appliesTo(Set<ItemTarget> targets) {
    List<String> names = new ArrayList<>();
    for (ItemTarget target : EnumSet.copyOf(targets)) {
      names.add(label(target));
    }
    return String.join(", ", names);
  }

  static String label(ItemTarget target) {
    return switch (target) {
      case ALL_ARMOR -> "All Armor";
      case ALL_WEAPONS -> "All Weapons";
      case LEGGINGS -> "Leggings";
      case BOOTS -> "Boots";
      default -> {
        String word = target.name().toLowerCase(Locale.ROOT);
        yield Character.toUpperCase(word.charAt(0))
            + word.substring(1)
            + (word.endsWith("x") ? "es" : "s");
      }
    };
  }

  public static String tierName(Tier tier) {
    String word = tier.name().toLowerCase(Locale.ROOT);
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }
}
