package gg.ascent.plugin.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.plugin.item.DupeScanner.Finding;
import gg.ascent.plugin.item.DupeScanner.Holder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PRD E1-S5 technical note: split stacks share an id; only a count above what was created is a
 * dupe.
 */
class DupeScannerTest {

  private static final UUID STEVE = UUID.randomUUID();
  private static final UUID ALEX = UUID.randomUUID();

  private static Holder held(UUID who, int count) {
    return new Holder(who, who == STEVE ? "Steve" : "Alex", "inventory", count);
  }

  @Test
  void splitStackIsNotADupe() {
    UUID dust = UUID.randomUUID();
    List<Finding> findings =
        DupeScanner.findDupes(
            Map.of(dust, List.of(held(STEVE, 40), held(ALEX, 24))), Map.of(dust, 64), Set.of());

    assertTrue(findings.isEmpty());
  }

  @Test
  void countAboveCreatedIsADupe() {
    UUID dust = UUID.randomUUID();
    List<Finding> findings =
        DupeScanner.findDupes(
            Map.of(dust, List.of(held(STEVE, 64), held(ALEX, 64))), Map.of(dust, 64), Set.of());

    assertEquals(1, findings.size());
    assertEquals(128, findings.get(0).observed());
    assertEquals(64, findings.get(0).created());
    assertEquals(2, findings.get(0).holders().size());
  }

  @Test
  void unknownIdIsAForgery() {
    UUID forged = UUID.randomUUID();
    List<Finding> findings =
        DupeScanner.findDupes(Map.of(forged, List.of(held(STEVE, 1))), Map.of(), Set.of());

    assertEquals(1, findings.size());
    assertEquals(0, findings.get(0).created());
  }

  @Test
  void alreadyOpenAlertsAreSkippedAndWorstComesFirst() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    List<Finding> findings =
        DupeScanner.findDupes(
            Map.of(
                a, List.of(held(STEVE, 2)),
                b, List.of(held(STEVE, 10)),
                c, List.of(held(ALEX, 3))),
            Map.of(a, 1, b, 1, c, 1),
            Set.of(a));

    assertEquals(List.of(b, c), findings.stream().map(Finding::itemId).toList());
  }
}
