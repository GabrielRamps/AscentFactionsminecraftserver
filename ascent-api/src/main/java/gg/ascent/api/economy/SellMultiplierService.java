package gg.ascent.api.economy;

import java.util.UUID;

/**
 * The multiplier applied to {@code /sell} prices (PRD E4-S2). Phase 1 answers 1.0 for everyone;
 * prestige and outpost bonuses plug in here later.
 */
public interface SellMultiplierService {

  double multiplier(UUID player);
}
