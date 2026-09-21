package gg.ascent.plugin.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Test helpers for building {@link Node}s from strings and bundled defaults. */
final class TestYaml {

  private TestYaml() {}

  static Node node(String file, String yaml) {
    YamlConfiguration config = new YamlConfiguration();
    try {
      config.loadFromString(yaml);
    } catch (InvalidConfigurationException e) {
      throw new IllegalArgumentException("test YAML is invalid", e);
    }
    return new Node(file, config);
  }

  /** The default file shipped in the jar, as it will be seeded on first run. */
  static Node bundled(String file) {
    return node(file, bundledText(file));
  }

  static String bundledText(String file) {
    try (InputStream in = TestYaml.class.getClassLoader().getResourceAsStream(file)) {
      if (in == null) {
        throw new IllegalStateException("no bundled resource " + file);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
