package gg.ascent.plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.rank.UnlockService.Kind;
import gg.ascent.api.rank.UnlockService.Unlock;
import gg.ascent.plugin.config.SettingsParsers;
import gg.ascent.plugin.config.TestYamlAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** PRD E2-S2, against the bundled defaults. */
class UnlockServiceImplTest {

  private UnlockServiceImpl unlocks;

  @BeforeEach
  void setUp() {
    ConfigService config = Mockito.mock(ConfigService.class);
    Mockito.when(config.ranks())
        .thenReturn(SettingsParsers.ranks(TestYamlAccess.bundled("ranks.yml")));
    Mockito.when(config.enchants())
        .thenReturn(SettingsParsers.enchants(TestYamlAccess.bundled("enchants.yml")));
    Mockito.when(config.kits())
        .thenReturn(SettingsParsers.kits(TestYamlAccess.bundled("kits.yml")));
    unlocks = new UnlockServiceImpl(config);
  }

  @Test
  void enchantSlotCapIsSixPlusRankOverTenCappedAtSixteen() {
    assertEquals(6, unlocks.enchantSlotCap(1));
    assertEquals(6, unlocks.enchantSlotCap(9));
    assertEquals(7, unlocks.enchantSlotCap(10));
    assertEquals(11, unlocks.enchantSlotCap(55));
    assertEquals(16, unlocks.enchantSlotCap(100));
    assertEquals(16, unlocks.enchantSlotCap(500));
  }

  @Test
  void bookTiersFollowEnchantsYml() {
    assertEquals(Tier.SIMPLE, unlocks.maxBookTier(1));
    assertEquals(Tier.SIMPLE, unlocks.maxBookTier(4));
    assertEquals(Tier.UNIQUE, unlocks.maxBookTier(5));
    assertEquals(Tier.ELITE, unlocks.maxBookTier(15));
    assertEquals(Tier.ULTIMATE, unlocks.maxBookTier(39));
    assertEquals(Tier.LEGENDARY, unlocks.maxBookTier(40));
  }

  @Test
  void mineTiersOpenAtOneTwentyForty() {
    assertEquals(1, unlocks.mineTier(1));
    assertEquals(1, unlocks.mineTier(19));
    assertEquals(2, unlocks.mineTier(20));
    assertEquals(3, unlocks.mineTier(40));
    assertEquals(3, unlocks.mineTier(100));
  }

  @Test
  void kitsOpenInOrder() {
    assertEquals(List.of("starter"), unlocks.availableKits(1));
    assertEquals(List.of("starter", "rank10"), unlocks.availableKits(24));
    assertEquals(5, unlocks.availableKits(75).size());
  }

  @Test
  void unlockListIsSortedAndTheNextUnlockIsAboveTheRank() {
    List<Unlock> all = unlocks.unlocks();
    for (int i = 1; i < all.size(); i++) {
      assertTrue(all.get(i - 1).rank() <= all.get(i).rank(), all.toString());
    }
    assertTrue(all.contains(new Unlock(10, Kind.ENCHANT_SLOT, "7")));
    assertTrue(all.contains(new Unlock(100, Kind.ENCHANT_SLOT, "16")));
    assertTrue(all.contains(new Unlock(40, Kind.BOOK_TIER, "LEGENDARY")));
    assertTrue(all.contains(new Unlock(20, Kind.MINE_TIER, "2")));
    assertTrue(all.contains(new Unlock(75, Kind.KIT, "rank75")));
    assertEquals(5, unlocks.nextUnlock(1).orElseThrow().rank(), "UNIQUE books at rank 5");
    assertTrue(unlocks.nextUnlock(100).isEmpty());
  }
}
