package gg.ascent.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.CoreSettings;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The database and Redis sections of {@code config.yml}, and how the environment overrides. */
class CoreSettingsParserTest {

  private static final String YAML =
      """
      debug: false
      time-zone: UTC
      database:
        host: db.internal
        port: 3307
        name: ascent_beta
        max-pool-size: 10
        connect-timeout: 5s
      redis:
        enabled: true
        host: cache.internal
        port: 6380
      """;

  @Test
  void fileValuesApplyWhenTheEnvironmentIsEmpty() {
    CoreSettings s = SettingsParsers.core(TestYaml.node("config.yml", YAML), key -> null);

    assertEquals("jdbc:mariadb://db.internal:3307/ascent_beta", s.database().jdbcUrl());
    assertEquals(10, s.database().maxPoolSize());
    assertEquals(Duration.ofSeconds(5), s.database().connectTimeout());
    assertFalse(s.database().hasCredentials());
    assertEquals("cache.internal", s.redis().host());
    assertEquals(6380, s.redis().port());
    assertNull(s.redis().password());
  }

  @Test
  void environmentSuppliesCredentialsAndOverridesConnection() {
    Map<String, String> env =
        Map.of(
            "MARIADB_HOST", "127.0.0.1",
            "MARIADB_PORT", "3306",
            "MARIADB_DATABASE", "ascent_dev",
            "MARIADB_USER", "ascent",
            "MARIADB_PASSWORD", "s3cret",
            "REDIS_PASSWORD", "r3dis");

    CoreSettings s = SettingsParsers.core(TestYaml.node("config.yml", YAML), env::get);

    assertEquals("jdbc:mariadb://127.0.0.1:3306/ascent_dev", s.database().jdbcUrl());
    assertEquals("ascent", s.database().user());
    assertEquals("s3cret", s.database().password());
    assertTrue(s.database().hasCredentials());
    assertEquals("r3dis", s.redis().password());
  }

  @Test
  void blankEnvironmentValuesAreIgnored() {
    Map<String, String> env = Map.of("MARIADB_HOST", "  ", "REDIS_PASSWORD", "");

    CoreSettings s = SettingsParsers.core(TestYaml.node("config.yml", YAML), env::get);

    assertEquals("db.internal", s.database().host());
    assertNull(s.redis().password());
  }

  @Test
  void nonNumericPortInEnvironmentIsAConfigError() {
    Map<String, String> env = Map.of("MARIADB_PORT", "three-three-oh-six");

    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> SettingsParsers.core(TestYaml.node("config.yml", YAML), env::get));
    assertTrue(e.getMessage().contains("MARIADB_PORT"), e.getMessage());
  }

  @Test
  void poolSizeBelowOneIsAConfigError() {
    String yaml = YAML.replace("max-pool-size: 10", "max-pool-size: 0");

    ConfigException e =
        assertThrows(
            ConfigException.class,
            () -> SettingsParsers.core(TestYaml.node("config.yml", yaml), key -> null));
    assertTrue(e.getMessage().contains("max-pool-size"), e.getMessage());
  }

  @Test
  void passwordsNeverAppearInToString() {
    Map<String, String> env =
        Map.of(
            "MARIADB_USER", "ascent", "MARIADB_PASSWORD", "hunter2", "REDIS_PASSWORD", "hunter3");

    CoreSettings s = SettingsParsers.core(TestYaml.node("config.yml", YAML), env::get);

    assertFalse(s.toString().contains("hunter2"), s.toString());
    assertFalse(s.toString().contains("hunter3"), s.toString());
  }
}
