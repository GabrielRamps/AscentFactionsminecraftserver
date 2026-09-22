package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EnchantDefinition;

/** One enchant on one piece of gear, resolved against the catalog. */
public record GearEnchant(EnchantDefinition definition, int level) {

  public String id() {
    return definition.id();
  }
}
