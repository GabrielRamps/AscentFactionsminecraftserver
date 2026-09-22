package gg.ascent.plugin.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The dupe rule (PRD E1-S5), free of Bukkit: an item id is duplicated when the total count seen
 * across online inventories exceeds the quantity that was created under that id. Splitting a stack
 * keeps its id, so two half-stacks are fine; two full stacks are not.
 */
public final class DupeScanner {

  private DupeScanner() {}

  /** One holder of an item id and how many they hold. */
  public record Holder(UUID player, String name, String where, int count) {}

  /** One duplicated id. */
  public record Finding(UUID itemId, int observed, int created, List<Holder> holders) {}

  /**
   * @param observed every tagged item seen, with who holds how many of it
   * @param created {@code quantity_created} per id; ids missing here were never registered and are
   *     reported as created 0, since an id with no row is a forgery
   * @param alreadyOpen ids that already have an unresolved alert; skipped so one dupe does not
   *     produce an alert every five minutes
   */
  public static List<Finding> findDupes(
      Map<UUID, List<Holder>> observed, Map<UUID, Integer> created, Set<UUID> alreadyOpen) {
    List<Finding> out = new ArrayList<>();
    for (Map.Entry<UUID, List<Holder>> entry : observed.entrySet()) {
      UUID id = entry.getKey();
      if (alreadyOpen.contains(id)) {
        continue;
      }
      int total = 0;
      for (Holder holder : entry.getValue()) {
        total += holder.count();
      }
      int allowed = created.getOrDefault(id, 0);
      if (total > allowed) {
        out.add(new Finding(id, total, allowed, List.copyOf(entry.getValue())));
      }
    }
    out.sort((a, b) -> Integer.compare(b.observed() - b.created(), a.observed() - a.created()));
    return out;
  }
}
