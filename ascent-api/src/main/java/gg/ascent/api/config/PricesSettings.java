package gg.ascent.api.config;

import java.util.Map;
import java.util.OptionalLong;

/**
 * {@code prices.yml}: what {@code /sell} pays per item (PRD E4-S2), in whole dollars.
 *
 * @param sell material name to price per item
 */
public record PricesSettings(Map<String, Long> sell) {

  public PricesSettings {
    sell = Map.copyOf(sell);
  }

  /** The sell price of a material, or empty if it cannot be sold. */
  public OptionalLong sellPrice(String material) {
    Long price = sell.get(material);
    return price == null ? OptionalLong.empty() : OptionalLong.of(price);
  }
}
