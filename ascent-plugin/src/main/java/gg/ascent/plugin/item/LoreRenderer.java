package gg.ascent.plugin.item;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.message.Messages;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * The one place custom item lore is written (PRD E11-S3).
 *
 * <p>Lore is: one line per enchant, {@code <tier_color>Name Level}, highest tier first and then by
 * name; then {@code PROTECTED} when a white scroll is applied; then the item-kind footer. With one
 * or more enchants the display name gets a {@code [n]} count. The renderer owns the whole lore of a
 * tagged item and keeps the item's original name in its persistent data, so re-rendering never
 * duplicates a line or stacks a count.
 *
 * <p>{@link #lines} and {@link #displayName} are pure so they can be tested without a server;
 * {@link #apply} is the Bukkit edge.
 */
public final class LoreRenderer {

  /** Where the item's name before any {@code [n]} suffix is kept, as serialized JSON. */
  public static final NamespacedKey BASE_NAME = new NamespacedKey("ascent", "base_name");

  /** One enchant on an item. */
  public record EnchantLine(String id, String display, Tier tier, int level) {}

  /**
   * Everything the renderer needs to know about an item.
   *
   * @param enchants the enchants on it, in any order
   * @param protectedByScroll whether a white scroll protects it
   * @param footer the item-kind footer line, or null for none
   */
  public record LoreSpec(
      List<EnchantLine> enchants, boolean protectedByScroll, @Nullable Component footer) {
    public LoreSpec {
      enchants = List.copyOf(enchants);
    }
  }

  private static final Comparator<EnchantLine> ORDER =
      Comparator.comparing(EnchantLine::tier, Comparator.reverseOrder())
          .thenComparing(line -> line.display().toLowerCase(Locale.ROOT));

  private final MiniMessage mini = MiniMessage.miniMessage();
  private final ConfigService config;
  private final Messages messages;

  public LoreRenderer(ConfigService config, Messages messages) {
    this.config = config;
    this.messages = messages;
  }

  /** The complete lore for {@code spec}, never italic. */
  public List<Component> lines(LoreSpec spec) {
    List<Component> out = new ArrayList<>();
    for (EnchantLine line : sorted(spec.enchants())) {
      String color = config.enchants().tier(line.tier()).color();
      out.add(
          plain(
              mini.deserialize(
                  color + "<name> <level>",
                  Placeholder.unparsed("name", line.display()),
                  Placeholder.unparsed("level", roman(line.level())))));
    }
    if (spec.protectedByScroll()) {
      out.add(plain(messages.render("lore.protected")));
    }
    if (spec.footer() != null) {
      out.add(plain(spec.footer()));
    }
    return out;
  }

  /** {@code base} with a {@code [n]} suffix when {@code enchantCount} is at least 1. */
  public Component displayName(Component base, int enchantCount) {
    if (enchantCount < 1) {
      return plain(base);
    }
    return plain(
        base.append(Component.space())
            .append(
                messages.render(
                    "lore.name-count",
                    Placeholder.unparsed("count", String.valueOf(enchantCount)))));
  }

  /** Writes the lore and name onto {@code item}. Re-applying with the same spec is a no-op. */
  public void apply(ItemStack item, LoreSpec spec) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    Component base = baseName(item, meta);
    meta.displayName(displayName(base, spec.enchants().size()));
    meta.lore(lines(spec));
    item.setItemMeta(meta);
  }

  /** The item's own name: remembered on first render, read back on every later one. */
  private static Component baseName(ItemStack item, ItemMeta meta) {
    String stored = meta.getPersistentDataContainer().get(BASE_NAME, PersistentDataType.STRING);
    if (stored != null) {
      return GsonComponentSerializer.gson().deserialize(stored);
    }
    Component current = meta.displayName();
    Component base = current != null ? current : Component.translatable(item.getType());
    meta.getPersistentDataContainer()
        .set(BASE_NAME, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(base));
    return base;
  }

  /** Highest tier first, then by display name. */
  public static List<EnchantLine> sorted(List<EnchantLine> enchants) {
    List<EnchantLine> out = new ArrayList<>(enchants);
    out.sort(ORDER);
    return out;
  }

  /** Levels 1 to 20 as Roman numerals, as every enchant plugin players know does; digits above. */
  public static String roman(int level) {
    if (level < 1 || level > 20) {
      return String.valueOf(level);
    }
    String[] tens = {"", "X", "XX"};
    String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
    return tens[level / 10] + ones[level % 10];
  }

  /** Vanilla renders custom names and lore in italics unless told otherwise. */
  private static Component plain(Component component) {
    return component.decoration(TextDecoration.ITALIC, false);
  }
}
