package gg.ascent.plugin.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigException;
import gg.ascent.plugin.config.Node;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class YamlMessagesTest {

  private static final String YAML =
      """
      prefix: "<gold>[A]</gold> "
      greet: "<prefix><green>Hello <player>!"
      plain: "no placeholders"
      nested:
        deep: "<red>deep"
      """;

  private static String plain(Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  private static Node node(String yaml) throws Exception {
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(yaml);
    return new Node("messages.yml", config);
  }

  private static YamlMessages messages(String yaml) throws Exception {
    YamlMessages m = new YamlMessages(LoggerFactory.getLogger(YamlMessagesTest.class));
    m.accept(YamlMessages.parse(node(yaml)));
    return m;
  }

  @Test
  void parseFlattensNestedKeys() throws Exception {
    Map<String, String> parsed = YamlMessages.parse(node(YAML));
    assertEquals("<red>deep", parsed.get("nested.deep"));
    assertTrue(parsed.containsKey("greet"));
  }

  @Test
  void parseRejectsNonStringValues() throws Exception {
    ConfigException e =
        assertThrows(ConfigException.class, () -> YamlMessages.parse(node("count: 5\n")));
    assertTrue(e.getMessage().startsWith("messages.yml: count:"), e.getMessage());
  }

  @Test
  void rendersPlaceholdersAndPrefix() throws Exception {
    YamlMessages m = messages(YAML);
    Component c = m.render("greet", Placeholder.unparsed("player", "Kai"));
    assertEquals("[A] Hello Kai!", plain(c));
  }

  @Test
  void unparsedPlaceholderDoesNotInterpretTags() throws Exception {
    YamlMessages m = messages(YAML);
    Component c = m.render("greet", Placeholder.unparsed("player", "<red>x"));
    assertEquals("[A] Hello <red>x!", plain(c));
  }

  @Test
  void missingKeyRendersVisibleMarkerInsteadOfThrowing() throws Exception {
    YamlMessages m = messages(YAML);
    assertFalse(m.has("absent"));
    assertEquals("<missing message: absent>", plain(m.render("absent")));
  }

  @Test
  void worksWithoutPrefixKey() throws Exception {
    YamlMessages m = messages("only: \"<prefix>text\"\n");
    assertEquals("text", plain(m.render("only")));
  }
}
