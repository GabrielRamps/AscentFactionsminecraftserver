package gg.ascent.plugin.mine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Picks block names by weight from a tier's composition. */
public final class BlockMix {

  private final List<String> names = new ArrayList<>();
  private final int[] cumulative;
  private final int total;

  public BlockMix(Map<String, Integer> composition) {
    if (composition.isEmpty()) {
      throw new IllegalArgumentException("empty composition");
    }
    cumulative = new int[composition.size()];
    int sum = 0;
    int i = 0;
    for (Map.Entry<String, Integer> e : composition.entrySet()) {
      sum += Math.max(0, e.getValue());
      names.add(e.getKey());
      cumulative[i++] = sum;
    }
    if (sum <= 0) {
      throw new IllegalArgumentException("composition has no weight");
    }
    total = sum;
  }

  public String pick(RandomGenerator random) {
    return names.get(pickIndex(random));
  }

  /** The index into {@link #names()} of a weighted pick. */
  public int pickIndex(RandomGenerator random) {
    int roll = random.nextInt(total);
    for (int i = 0; i < cumulative.length; i++) {
      if (roll < cumulative[i]) {
        return i;
      }
    }
    return cumulative.length - 1;
  }

  public List<String> names() {
    return List.copyOf(names);
  }
}
