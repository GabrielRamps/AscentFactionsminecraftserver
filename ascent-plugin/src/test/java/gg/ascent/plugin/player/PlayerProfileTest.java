package gg.ascent.plugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.player.PlayerProfile;
import gg.ascent.api.player.PlayerSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {

  private static PlayerProfile fresh() {
    return new PlayerProfile(PlayerSnapshot.fresh(UUID.randomUUID(), "Steve", 1000, Instant.EPOCH));
  }

  @Test
  void startsClean() {
    assertFalse(fresh().isDirty());
  }

  @Test
  void everySetterMarksDirty() {
    PlayerProfile p = fresh();
    p.setBalance(5);
    assertTrue(p.isDirty());
    p.clearDirty();
    p.setRank(2);
    assertTrue(p.isDirty());
    p.clearDirty();
    p.addPlaySeconds(10);
    assertTrue(p.isDirty());
    p.clearDirty();
    p.setName("Steve");
    assertFalse(p.isDirty(), "an unchanged name is not a change");
  }

  @Test
  void snapshotIsACopy() {
    PlayerProfile p = fresh();
    PlayerSnapshot before = p.snapshot();
    p.setBalance(9);

    assertEquals(1000, before.balance());
    assertEquals(9, p.snapshot().balance());
  }

  @Test
  void rejectsNegativeBalanceAndRankBelowOne() {
    PlayerProfile p = fresh();
    assertThrows(IllegalArgumentException.class, () -> p.setBalance(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setRank(0));
  }
}
