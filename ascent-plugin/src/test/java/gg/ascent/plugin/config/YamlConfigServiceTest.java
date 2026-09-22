package gg.ascent.plugin.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ReloadReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** The loader against a real temp directory, using the jar's own bundled defaults. */
class YamlConfigServiceTest {

  @TempDir Path dataDir;

  private YamlConfigService service;

  @BeforeEach
  void setUp() {
    service =
        new YamlConfigService(
            dataDir,
            name -> getClass().getClassLoader().getResourceAsStream(name),
            LoggerFactory.getLogger(YamlConfigServiceTest.class));
  }

  @Test
  void firstLoadSeedsEveryFileFromBundledDefaults() {
    ReloadReport report = service.load();

    assertTrue(report.allOk(), report.failures().toString());
    assertEquals(11, report.files().size());
    for (String file :
        new String[] {
          "config.yml",
          "ranks.yml",
          "enchants.yml",
          "mines.yml",
          "spawners.yml",
          "factions.yml",
          "contracts.yml",
          "events.yml",
          "combat.yml",
          "kits.yml",
          "prices.yml"
        }) {
      assertTrue(Files.exists(dataDir.resolve(file)), file + " should be seeded");
    }
    assertNotNull(service.ranks());
    assertEquals(400, service.ranks().xpCurve().base());
  }

  @Test
  void missingKeysAreAddedFromTheBundledDefaultAndWrittenBack() throws IOException {
    // A config.yml written by the E1-S1 build: no database, redis or players sections.
    Files.createDirectories(dataDir);
    Files.writeString(dataDir.resolve("config.yml"), "debug: true\ntime-zone: UTC\n");

    ReloadReport report = service.load();

    assertTrue(report.allOk(), report.failures().toString());
    assertTrue(service.core().debug(), "the operator's own value survives");
    assertEquals(10, service.core().database().maxPoolSize(), "filled from the bundled default");
    String written = Files.readString(dataDir.resolve("config.yml"));
    assertTrue(written.contains("max-pool-size: 10"), written);
    assertTrue(written.contains("debug: true"), written);
    assertTrue(written.contains("# MariaDB"), "the section's comments come along: " + written);
  }

  @Test
  void anUpToDateFileIsNotRewritten() throws IOException {
    service.load();
    Path config = dataDir.resolve("config.yml");
    byte[] before = Files.readAllBytes(config);

    service.reload();

    assertArrayEquals(before, Files.readAllBytes(config));
  }

  @Test
  void reloadPicksUpEditedValues() throws IOException {
    service.load();
    Path ranks = dataDir.resolve("ranks.yml");
    Files.writeString(ranks, Files.readString(ranks).replace("base: 400", "base: 500"));

    ReloadReport report = service.reload();

    assertTrue(report.allOk());
    assertEquals(500, service.ranks().xpCurve().base());
  }

  @Test
  void invalidYamlOnReloadKeepsPreviousValuesAndReportsIt() throws IOException {
    service.load();
    Files.writeString(dataDir.resolve("ranks.yml"), "xp-curve: [unclosed\n  base: 400\n");

    ReloadReport report = service.reload();

    assertFalse(report.allOk());
    assertEquals(1, report.failures().size());
    ReloadReport.FileResult failure = report.failures().get(0);
    assertEquals("ranks.yml", failure.file());
    assertTrue(failure.error().startsWith("ranks.yml: invalid YAML"), failure.error());
    assertEquals(400, service.ranks().xpCurve().base(), "previous values must survive");
  }

  @Test
  void validationFailureOnReloadKeepsPreviousValuesAndNamesThePath() throws IOException {
    service.load();
    Path combat = dataDir.resolve("combat.yml");
    Files.writeString(
        combat, Files.readString(combat).replace("tag-seconds: 15", "tag-seconds: 0"));

    ReloadReport report = service.reload();

    assertFalse(report.allOk());
    String error = report.failures().get(0).error();
    assertTrue(error.startsWith("combat.yml: tag-seconds:"), error);
    assertEquals(15, service.combat().tagSeconds());
  }

  @Test
  void brokenFileOnFirstLoadFallsBackToBundledDefaults() throws IOException {
    // Missing keys are filled in from the bundled default, so a file is only broken when a value
    // it does carry is wrong: here a member cap below the minimum of 1.
    Files.writeString(dataDir.resolve("factions.yml"), "member-cap: 0\n");

    ReloadReport report = service.load();

    assertFalse(report.allOk());
    assertEquals("factions.yml", report.failures().get(0).file());
    assertNotNull(service.factions(), "plugin must still have usable settings");
    assertEquals(10000, service.factions().createCost());
    for (ReloadReport.FileResult result : report.files()) {
      if (!result.file().equals("factions.yml")) {
        assertTrue(result.ok(), result.toString());
      }
    }
  }

  @Test
  void registeredExtraFileIsLoadedAndReloaded() {
    String[] holder = new String[1];
    service.register(
        "combat.yml", node -> node.string("tag-seconds"), v -> holder[0] = v, () -> holder[0]);

    ReloadReport report = service.load();

    assertTrue(report.allOk());
    assertEquals(12, report.files().size());
    assertEquals("15", holder[0]);
  }
}
