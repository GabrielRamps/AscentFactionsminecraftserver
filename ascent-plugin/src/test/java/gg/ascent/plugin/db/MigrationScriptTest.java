package gg.ascent.plugin.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The V1 script must exist in the jar and create every table PRD §6.3 names. This runs without a
 * database; {@link DatabaseIntegrationTest} checks the script actually applies.
 */
class MigrationScriptTest {

  static final List<String> SPEC_TABLES =
      List.of(
          "players",
          "xp_log",
          "money_transactions",
          "items",
          "item_events",
          "dupe_alerts",
          "enchant_rolls",
          "factions",
          "faction_members",
          "faction_relations",
          "faction_perms",
          "claims",
          "placed_spawners",
          "mine_plots",
          "contracts_active",
          "contracts_history",
          "kit_cooldowns",
          "event_results",
          "ftop_snapshots",
          "ftop_payouts",
          "death_log",
          "inventory_snapshots",
          "admin_actions",
          "sessions");

  static String v1() throws IOException {
    try (InputStream in =
        MigrationScriptTest.class
            .getClassLoader()
            .getResourceAsStream("db/migration/V1__init.sql")) {
      assertNotNull(in, "V1__init.sql must be bundled at db/migration/");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void createsEveryTableInTheSpec() throws IOException {
    String sql = v1();
    Matcher m = Pattern.compile("(?m)^CREATE TABLE (\\w+)").matcher(sql);
    java.util.Set<String> created = new java.util.TreeSet<>();
    while (m.find()) {
      created.add(m.group(1));
    }
    for (String table : SPEC_TABLES) {
      assertTrue(created.contains(table), "missing CREATE TABLE " + table);
    }
    assertEquals(SPEC_TABLES.size(), created.size(), "unexpected extra tables: " + created);
  }

  @Test
  void everyTableIsInnoDbUtf8mb4() throws IOException {
    String sql = v1();
    int tables = sql.split("(?m)^CREATE TABLE ").length - 1;
    int engines =
        sql.split("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;").length - 1;
    assertEquals(tables, engines, "every CREATE TABLE must end with the InnoDB/utf8mb4 clause");
  }

  @Test
  void rankColumnIsQuoted() throws IOException {
    // RANK is a window function in MariaDB; leave it unquoted and the DDL is a syntax error on
    // some versions.
    assertTrue(v1().contains("`rank`"));
  }
}
