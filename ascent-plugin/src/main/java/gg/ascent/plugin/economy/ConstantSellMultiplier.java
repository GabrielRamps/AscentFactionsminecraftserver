package gg.ascent.plugin.economy;

import gg.ascent.api.economy.SellMultiplierService;
import java.util.UUID;

/** Phase 1: everyone sells at face value (PRD E4-S2). */
public final class ConstantSellMultiplier implements SellMultiplierService {

  private final double value;

  public ConstantSellMultiplier(double value) {
    this.value = value;
  }

  @Override
  public double multiplier(UUID player) {
    return value;
  }
}
