package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.ApplyResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** The {@code enchant_rolls} table: the data that proves the odds are honest (PRD E3-S3). */
public interface EnchantRollRepository {

  void record(
      Connection c,
      UUID player,
      UUID bookItemId,
      UUID targetItemId,
      String enchantId,
      int level,
      int success,
      int destroy,
      ApplyResult outcome,
      Instant at)
      throws SQLException;
}
