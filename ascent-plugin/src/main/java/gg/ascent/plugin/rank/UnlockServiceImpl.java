package gg.ascent.plugin.rank;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.config.KitsSettings;
import gg.ascent.api.config.RanksSettings;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.rank.UnlockService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link UnlockService} computed from the live configuration, so a reload changes the gates. */
public final class UnlockServiceImpl implements UnlockService {

  private final ConfigService config;

  public UnlockServiceImpl(ConfigService config) {
    this.config = config;
  }

  @Override
  public int enchantSlotCap(int rank) {
    RanksSettings.EnchantSlots slots = config.ranks().unlocks().enchantSlots();
    return Math.min(slots.max(), slots.base() + Math.max(0, rank) / slots.perRanks());
  }

  @Override
  public Tier maxBookTier(int rank) {
    EnchantsSettings enchants = config.enchants();
    Tier best = Tier.SIMPLE;
    for (Tier tier : Tier.values()) {
      if (enchants.tier(tier).minRank() <= rank && tier.isAtLeast(best)) {
        best = tier;
      }
    }
    return best;
  }

  @Override
  public int mineTier(int rank) {
    int best = 1;
    for (Map.Entry<Integer, Integer> e : config.ranks().unlocks().mineTiers().entrySet()) {
      if (e.getValue() <= rank && e.getKey() > best) {
        best = e.getKey();
      }
    }
    return best;
  }

  @Override
  public List<String> availableKits(int rank) {
    List<String> out = new ArrayList<>();
    for (KitsSettings.Kit kit : config.kits().byRank()) {
      if (kit.minRank() <= rank) {
        out.add(kit.id());
      }
    }
    return out;
  }

  @Override
  public List<Unlock> unlocks() {
    List<Unlock> out = new ArrayList<>();
    RanksSettings.Unlocks unlocks = config.ranks().unlocks();
    RanksSettings.EnchantSlots slots = unlocks.enchantSlots();
    int max = config.ranks().xpCurve().maxRank();
    for (int rank = slots.perRanks(); rank <= max; rank += slots.perRanks()) {
      int cap = enchantSlotCap(rank);
      if (cap == enchantSlotCap(rank - 1)) {
        break; // the cap has been reached; higher ranks add nothing
      }
      out.add(new Unlock(rank, Kind.ENCHANT_SLOT, String.valueOf(cap)));
    }
    for (Tier tier : Tier.values()) {
      out.add(new Unlock(config.enchants().tier(tier).minRank(), Kind.BOOK_TIER, tier.name()));
    }
    for (Map.Entry<Integer, Integer> e : unlocks.mineTiers().entrySet()) {
      out.add(new Unlock(e.getValue(), Kind.MINE_TIER, String.valueOf(e.getKey())));
    }
    for (KitsSettings.Kit kit : config.kits().byRank()) {
      out.add(new Unlock(kit.minRank(), Kind.KIT, kit.id()));
    }
    out.sort(
        Comparator.comparingInt(Unlock::rank)
            .thenComparing(Unlock::kind)
            .thenComparing(Unlock::name));
    return out;
  }

  @Override
  public Optional<Unlock> nextUnlock(int rank) {
    for (Unlock unlock : unlocks()) {
      if (unlock.rank() > rank) {
        return Optional.of(unlock);
      }
    }
    return Optional.empty();
  }
}
