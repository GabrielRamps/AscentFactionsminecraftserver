package dev.ascentfactions.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Checks the processed plugin.yml so a broken descriptor fails in CI rather than on boot. */
class PluginDescriptorTest {

  private Map<String, Object> load() {
    InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml");
    assertNotNull(in, "plugin.yml missing from resources");
    return new Yaml().load(in);
  }

  @Test
  void mainClassExistsAndMatchesDescriptor() throws ClassNotFoundException {
    Map<String, Object> yml = load();
    assertEquals("Ascent", yml.get("name"));
    String main = (String) yml.get("main");
    assertEquals(AscentPlugin.class.getName(), main);
    Class.forName(main);
  }

  @Test
  void versionPlaceholderWasExpanded() {
    String version = String.valueOf(load().get("version"));
    assertFalse(version.contains("${"), "version placeholder not expanded: " + version);
    assertFalse(version.isBlank());
  }

  @Test
  void declaresSoftDependencies() {
    Object softdepend = load().get("softdepend");
    assertTrue(softdepend instanceof List<?>);
    List<?> deps = (List<?>) softdepend;
    List<String> required = List.of("Vault", "PlaceholderAPI", "LuckPerms");
    assertTrue(deps.containsAll(required), deps.toString());
  }
}
