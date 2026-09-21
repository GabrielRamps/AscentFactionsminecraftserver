package gg.ascent.plugin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import gg.ascent.plugin.item.LoreRenderer.EnchantLine;
import gg.ascent.plugin.item.LoreRenderer.LoreSpec;
import gg.ascent.plugin.message.YamlMessages;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/** PRD E11-S3: order, format, PROTECTED, footer, the [n] count, and idempotence. */
class LoreRendererTest {

  private LoreRenderer renderer;

  @BeforeEach
  void setUp() {
    EnchantsSettings enchants = SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml"));
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.enchants()).thenReturn(enchants);
    YamlMessages messages = new YamlMessages(LoggerFactory.getLogger(LoreRendererTest.class));
    messages.accept(
        Map.of("lore.protected", "<white>PROTECTED", "lore.name-count", "<gray>[<count>]"));
    renderer = new LoreRenderer(config, messages);
  }

  private static String plain(Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  /** MiniMessage may put the color on the root or on the first child; either counts. */
  private static TextColor colorOf(Component c) {
    if (c.color() != null) {
      return c.color();
    }
    for (Component child : c.children()) {
      TextColor color = colorOf(child);
      if (color != null) {
        return color;
      }
    }
    return null;
  }

  @Test
  void enchantsSortByTierThenNameWithTierColorAndRomanLevel() {
    LoreSpec spec =
        new LoreSpec(
            List.of(
                new EnchantLine("sharp", "Sharpness", Tier.SIMPLE, 4),
                new EnchantLine("lifesteal", "Lifesteal", Tier.LEGENDARY, 2),
                new EnchantLine("aqua", "Aquatic", Tier.SIMPLE, 1)),
            false,
            null);

    List<Component> lines = renderer.lines(spec);

    assertEquals(
        List.of("Lifesteal II", "Aquatic I", "Sharpness IV"),
        lines.stream().map(LoreRendererTest::plain).toList());
    assertEquals(NamedTextColor.GOLD, colorOf(lines.get(0)), "legendary is gold in enchants.yml");
    assertEquals(NamedTextColor.WHITE, colorOf(lines.get(1)));
    assertTrue(
        lines.stream()
            .allMatch(l -> l.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE));
  }

  @Test
  void protectedLineThenFooterFollowTheEnchants() {
    LoreSpec spec =
        new LoreSpec(
            List.of(new EnchantLine("sharp", "Sharpness", Tier.SIMPLE, 1)),
            true,
            Component.text("Gear"));

    List<String> lines = renderer.lines(spec).stream().map(LoreRendererTest::plain).toList();

    assertEquals(List.of("Sharpness I", "PROTECTED", "Gear"), lines);
  }

  @Test
  void noEnchantsMeansNoEnchantLinesAndNoCount() {
    LoreSpec spec = new LoreSpec(List.of(), false, Component.text("Dust"));

    assertEquals(
        List.of("Dust"), renderer.lines(spec).stream().map(LoreRendererTest::plain).toList());
    assertEquals("Diamond Sword", plain(renderer.displayName(Component.text("Diamond Sword"), 0)));
  }

  @Test
  void displayNameCountIsBuiltFromTheBaseSoItNeverStacks() {
    Component base = Component.text("Diamond Sword");
    Component once = renderer.displayName(base, 3);
    Component again = renderer.displayName(base, 3);

    assertEquals("Diamond Sword [3]", plain(once));
    assertEquals(once, again);
    assertFalse(plain(renderer.displayName(base, 4)).contains("[3]"));
  }

  @Test
  void renderingTwiceGivesIdenticalLore() {
    LoreSpec spec =
        new LoreSpec(
            List.of(
                new EnchantLine("a", "Alpha", Tier.ELITE, 3),
                new EnchantLine("b", "Beta", Tier.ELITE, 20)),
            true,
            Component.text("Gear"));

    assertEquals(renderer.lines(spec), renderer.lines(spec));
    assertEquals("Beta XX", plain(renderer.lines(spec).get(1)));
  }

  @Test
  void romanNumerals() {
    assertEquals("I", LoreRenderer.roman(1));
    assertEquals("IV", LoreRenderer.roman(4));
    assertEquals("IX", LoreRenderer.roman(9));
    assertEquals("X", LoreRenderer.roman(10));
    assertEquals("XIV", LoreRenderer.roman(14));
    assertEquals("XX", LoreRenderer.roman(20));
    assertEquals("21", LoreRenderer.roman(21));
    assertEquals("0", LoreRenderer.roman(0));
  }
}
