package gg.ascent.plugin.config;

/** Lets tests in other packages read the bundled YAML through {@link TestYaml}. */
public final class TestYamlAccess {

  private TestYamlAccess() {}

  public static Node bundled(String file) {
    return TestYaml.bundled(file);
  }
}
