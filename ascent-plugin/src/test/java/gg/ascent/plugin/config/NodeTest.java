package gg.ascent.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.IntRange;
import gg.ascent.api.enchant.Tier;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every failure must name the file and the path, so an operator can find the line. */
class NodeTest {

  private static final String YAML =
      """
      count: 5
      big: 3000000000
      ratio: 0.25
      flag: true
      name: hello
      empty: ""
      tier: legendary
      range: [20, 70]
      bad-range: [70, 20]
      wide-range: [0, 150]
      words: [a, b]
      mixed: [a, 3]
      nested:
        inner: 1
      short: 30s
      minutes: 15m
      hours: 6h
      days: 1d
      plain: 90
      """;

  private final Node node = TestYaml.node("test.yml", YAML);

  @Test
  void typedGettersReadValues() {
    assertEquals(5, node.integer("count"));
    assertEquals(3000000000L, node.longValue("big"));
    assertEquals(0.25, node.decimal("ratio"));
    assertTrue(node.bool("flag"));
    assertEquals("hello", node.string("name"));
    assertEquals(Tier.LEGENDARY, node.enumValue("tier", Tier.class));
    assertEquals(new IntRange(20, 70), node.intRange("range", 0, 100));
    assertEquals(List.of("a", "b"), node.stringList("words"));
    assertEquals(1, node.section("nested").integer("inner"));
  }

  @Test
  void durationsAcceptUnitsAndBareSeconds() {
    assertEquals(Duration.ofSeconds(30), node.duration("short"));
    assertEquals(Duration.ofMinutes(15), node.duration("minutes"));
    assertEquals(Duration.ofHours(6), node.duration("hours"));
    assertEquals(Duration.ofDays(1), node.duration("days"));
    assertEquals(Duration.ofSeconds(90), node.duration("plain"));
  }

  @Test
  void missingKeyNamesFileAndPath() {
    ConfigException e = assertThrows(ConfigException.class, () -> node.integer("nope"));
    assertEquals("test.yml: nope: missing; expected a value", e.getMessage());
  }

  @Test
  void nestedPathIsFullyQualified() {
    ConfigException e =
        assertThrows(ConfigException.class, () -> node.section("nested").string("gone"));
    assertTrue(e.getMessage().startsWith("test.yml: nested.gone:"), e.getMessage());
  }

  @Test
  void wrongTypeSaysWhatWasExpectedAndFound() {
    ConfigException e = assertThrows(ConfigException.class, () -> node.integer("name"));
    assertTrue(e.getMessage().contains("expected a whole number"), e.getMessage());
    assertTrue(e.getMessage().contains("\"hello\""), e.getMessage());
  }

  @Test
  void emptyStringIsRejected() {
    assertThrows(ConfigException.class, () -> node.string("empty"));
  }

  @Test
  void boundsAreEnforced() {
    assertThrows(ConfigException.class, () -> node.integerAtLeast("count", 6));
    assertThrows(ConfigException.class, () -> node.decimalBetween("ratio", 0.5, 1.0));
    assertEquals(5, node.integerAtLeast("count", 5));
  }

  @Test
  void rangesMustBeOrderedAndWithinBounds() {
    assertThrows(ConfigException.class, () -> node.intRange("bad-range", 0, 100));
    assertThrows(ConfigException.class, () -> node.intRange("wide-range", 0, 100));
    assertThrows(ConfigException.class, () -> node.intRange("count", 0, 100));
  }

  @Test
  void enumErrorListsValidValues() {
    ConfigException e =
        assertThrows(ConfigException.class, () -> node.enumValue("name", Tier.class));
    assertTrue(e.getMessage().contains("LEGENDARY"), e.getMessage());
  }

  @Test
  void stringListRejectsNonStringsWithIndex() {
    ConfigException e = assertThrows(ConfigException.class, () -> node.stringList("mixed"));
    assertTrue(e.getMessage().contains("mixed[1]"), e.getMessage());
  }

  @Test
  void sectionOrEmptyDoesNotThrowOrMutate() {
    assertTrue(node.sectionOrEmpty("absent").keys().isEmpty());
    assertTrue(!node.has("absent"));
  }
}
