package gg.ascent.plugin.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.ascent.api.progress.ObjectiveType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SimpleProgressBusTest {

  @Test
  void deliversToEveryListenerAndSurvivesAFailingOne() {
    SimpleProgressBus bus =
        new SimpleProgressBus(LoggerFactory.getLogger(SimpleProgressBusTest.class));
    List<String> seen = new ArrayList<>();
    bus.subscribe((p, t, a, q) -> seen.add(t + ":" + a + ":" + q));
    bus.subscribe(
        (p, t, a, q) -> {
          throw new IllegalStateException("boom");
        });
    bus.subscribe((p, t, a, q) -> seen.add("second"));

    bus.publish(UUID.randomUUID(), ObjectiveType.MINE_BLOCKS, 1, "STONE");
    bus.publish(UUID.randomUUID(), ObjectiveType.SELL_VALUE, 0, null);

    assertEquals(List.of("MINE_BLOCKS:1:STONE", "second"), seen);
    assertEquals(3, bus.listenerCount());
  }
}
