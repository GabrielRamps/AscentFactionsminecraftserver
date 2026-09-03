package gg.ascent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
