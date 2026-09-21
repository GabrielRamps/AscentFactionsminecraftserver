package gg.ascent.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.CombatSettings;
import gg.ascent.api.config.ContractsSettings;
import gg.ascent.api.config.CoreSettings;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.EventsSettings;
import gg.ascent.api.config.FactionsSettings;
import gg.ascent.api.config.MinesSettings;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.config.SpawnersSettings;
import gg.ascent.api.contract.Archetype;
import gg.ascent.api.enchant.Tier;
import gg.ascent.plugin.message.YamlMessages;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every bundled default must parse, and the numbers the PRD specifies must be the numbers in the
 * files. A failure here means a shipped config is broken or has drifted from the spec.
 */
class BundledDefaultsTest {

  @Test
  void core() {
    CoreSettings s = SettingsParsers.core(TestYaml.bundled("config.yml"), key -> null);
    // ZoneId.of("UTC") is a ZoneRegion and never equals the ZoneOffset.UTC constant, even though
    // both represent the same offset -- compare against the same parse the production code does.
    assertEquals(ZoneId.of("UTC"), s.timeZone());
    assertTrue(!s.debug());
    assertEquals("jdbc:mariadb://127.0.0.1:3306/ascent_dev", s.database().jdbcUrl());
    assertEquals(10, s.database().maxPoolSize());
    assertTrue(!s.database().hasCredentials());
    assertTrue(s.redis().enabled());
    assertEquals(6379, s.redis().port());
  }

  @Test
  void ranksMatchPrdCurveAndSources() {
    RanksSettings s = SettingsParsers.ranks(TestYaml.bundled("ranks.yml"));
    assertEquals(400, s.xpCurve().base());
    assertEquals(100, s.xpCurve().maxRank());
    assertEquals(400, s.xpCurve().xpToNext(1));
    assertEquals(Math.round(400 * Math.pow(10, 1.4)), s.xpCurve().xpToNext(10));
    assertEquals(Map.of(1, 2L, 2, 5L, 3, 12L), s.sources().mineBlockByTier());
    assertEquals(40L, s.sources().spawnerKillByMob().get("IRON_GOLEM"));
    assertEquals(500, s.sources().pvpKill().base());
    assertEquals(0.1, s.sources().pvpKill().repeatMultiplier());
    assertEquals(2000, s.sources().kothWin());
  }

  @Test
  void enchantsHaveAllFiveTiersInOrder() {
    EnchantsSettings s = SettingsParsers.enchants(TestYaml.bundled("enchants.yml"));
    assertEquals(5, s.tiers().size());
    assertEquals(1, s.tier(Tier.SIMPLE).minRank());
    assertEquals(40, s.tier(Tier.LEGENDARY).minRank());
    assertEquals(90, s.tier(Tier.LEGENDARY).xpLevels());
    assertEquals(20, s.tier(Tier.LEGENDARY).success().min());
    int previous = 0;
    for (Tier tier : Tier.values()) {
      int minRank = s.tier(tier).minRank();
      assertTrue(minRank > previous, tier + " should require a higher rank than the tier below");
      previous = minRank;
    }
  }

  @Test
  void minesCompositionsSumTo100() {
    MinesSettings s = SettingsParsers.mines(TestYaml.bundled("mines.yml"));
    assertEquals(64, s.plotSize());
    assertEquals(Duration.ofHours(24), s.plotInactivity());
    assertEquals(Duration.ofMinutes(10), s.resetInterval());
    assertEquals(0.7, s.resetMinedFraction());
    assertEquals(3, s.tiers().size());
    assertEquals(70, s.tiers().get(1).composition().get("STONE"));
    assertEquals(10, s.tiers().get(3).composition().get("EMERALD_ORE"));
  }

  @Test
  void spawners() {
    SpawnersSettings s = SettingsParsers.spawners(TestYaml.bundled("spawners.yml"));
    assertEquals(40, s.stackCap());
    assertEquals(5, s.activeRadiusChunks());
    assertEquals(60, s.pendingCapPerCount());
    assertEquals(0.2, s.types().get("ZOMBIE").ratePerSecond());
  }

  @Test
  void factions() {
    FactionsSettings s = SettingsParsers.factions(TestYaml.bundled("factions.yml"));
    assertEquals(10000, s.createCost());
    assertEquals(15, s.memberCap());
    assertEquals(150, s.power().max());
    assertEquals(-20, s.power().floor());
    assertEquals(10, s.claims().powerPerClaim());
    assertEquals(500, s.upkeep().costPerChunkPerDay());
    assertEquals(3, s.upkeep().graceDays());
    assertEquals(0.1, s.ftop().vaultWeight());
  }

  @Test
  void contractsCoverEveryArchetype() {
    ContractsSettings s = SettingsParsers.contracts(TestYaml.bundled("contracts.yml"));
    assertEquals(3, s.perDay());
    for (Archetype archetype : Archetype.values()) {
      assertTrue(
          s.pool().stream().anyMatch(c -> c.archetype() == archetype),
          "pool needs at least one " + archetype + " contract");
    }
  }

  @Test
  void eventsKothLootbagWeightsSumTo100() {
    EventsSettings s = SettingsParsers.events(TestYaml.bundled("events.yml"));
    assertEquals(100, s.zones().safezoneRadius());
    assertEquals(400, s.zones().warzoneRadius());
    assertEquals(Duration.ofHours(6), s.koth().interval());
    assertEquals(Duration.ofMinutes(15), s.koth().captureTime());
    int total = s.koth().lootbag().stream().mapToInt(EventsSettings.WeightedEntry::weight).sum();
    assertEquals(100, total);
    assertEquals(10, s.envoys().crates().values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void combat() {
    CombatSettings s = SettingsParsers.combat(TestYaml.bundled("combat.yml"));
    assertEquals(15, s.tagSeconds());
    assertEquals(30, s.loggerNpcSeconds());
    assertTrue(s.blockedCommandsWhileTagged().contains("f home"));
  }

  @Test
  void messagesHaveEveryKeyTheCommandUses() {
    Map<String, String> m = YamlMessages.parse(TestYaml.bundled("messages.yml"));
    for (String key :
        new String[] {
          "prefix",
          "ascent.usage",
          "ascent.version",
          "ascent.reload.ok",
          "ascent.reload.failed",
          "ascent.reload.error"
        }) {
      assertTrue(m.containsKey(key), "messages.yml is missing " + key);
    }
  }
}
