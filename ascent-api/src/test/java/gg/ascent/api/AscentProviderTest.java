package gg.ascent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.SellMultiplierService;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.kit.KitService;
import gg.ascent.api.message.Messages;
import gg.ascent.api.mine.MineService;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.progress.ProgressBus;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.UnlockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AscentProviderTest {

  private static final AscentApi STUB =
      new AscentApi() {
        @Override
        public String version() {
          return "test";
        }

        @Override
        public boolean ready() {
          return true;
        }

        @Override
        public ConfigService config() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public Messages messages() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public DbExecutor db() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public PlayerService players() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public EconomyService economy() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public ItemRegistry items() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public RankService ranks() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public UnlockService unlocks() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public KitService kits() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public MineService mines() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public ProgressBus progress() {
          throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public SellMultiplierService sellMultiplier() {
          throw new UnsupportedOperationException("not needed by this test");
        }
      };

  @AfterEach
  void clear() {
    AscentProvider.unregister();
  }

  @Test
  void getThrowsWhenNoInstanceRegistered() {
    assertFalse(AscentProvider.isPresent());
    assertThrows(IllegalStateException.class, AscentProvider::get);
  }

  @Test
  void getReturnsRegisteredInstance() {
    AscentProvider.register(STUB);

    assertTrue(AscentProvider.isPresent());
    assertEquals("test", AscentProvider.get().version());
  }

  @Test
  void registerRejectsDoubleRegistration() {
    AscentProvider.register(STUB);

    assertThrows(IllegalStateException.class, () -> AscentProvider.register(STUB));
  }

  @Test
  void registerRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> AscentProvider.register(null));
  }

  @Test
  void unregisterIsIdempotent() {
    AscentProvider.unregister();
    AscentProvider.unregister();

    assertFalse(AscentProvider.isPresent());
  }
}
