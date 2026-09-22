package gg.ascent.plugin.rank;

import gg.ascent.api.message.Messages;
import gg.ascent.api.rank.RankService;
import gg.ascent.api.rank.RankService.Progress;
import gg.ascent.api.rank.UnlockService;
import gg.ascent.api.rank.UnlockService.Unlock;
import gg.ascent.plugin.leaderboard.Leaderboard;
import gg.ascent.plugin.leaderboard.Leaderboard.Entry;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** {@code /rank}, {@code /rank top} and {@code /rank unlocks} (PRD E2-S1, E2-S2). */
public final class RankCommand implements CommandExecutor, TabCompleter {

  /** Rank-top scores pack rank and progress into one number: rank first, then XP. */
  public static final long RANK_SCORE_SCALE = 1_000_000_000_000L;

  private static final List<String> SUBCOMMANDS = List.of("top", "unlocks");

  private final Plugin plugin;
  private final RankService ranks;
  private final UnlockService unlocks;
  private final Leaderboard top;
  private final Messages messages;

  public RankCommand(
      Plugin plugin, RankService ranks, UnlockService unlocks, Leaderboard top, Messages messages) {
    this.plugin = plugin;
    this.ranks = ranks;
    this.unlocks = unlocks;
    this.top = top;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "" -> show(sender);
      case "top" -> top(sender);
      case "unlocks" -> unlocks(sender);
      default -> messages.send(sender, "rank.usage");
    }
    return true;
  }

  private void show(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "rank.console");
      return;
    }
    Progress p = ranks.progress(player.getUniqueId());
    String bar = ProgressBar.render(p.fraction(), 20, '■', '□');
    messages.send(
        sender,
        p.atTop() ? "rank.show.top" : "rank.show.progress",
        Placeholder.unparsed("rank", String.valueOf(p.rank())),
        Placeholder.unparsed("max", String.valueOf(ranks.maxRank())),
        Placeholder.unparsed("xp", number(p.xp())),
        Placeholder.unparsed("needed", number(p.xpToNext())),
        Placeholder.unparsed("percent", String.valueOf(Math.round(p.fraction() * 100))),
        Placeholder.unparsed("bar", bar),
        Placeholder.unparsed("banked", number(p.banked())));
    Optional<Unlock> next = unlocks.nextUnlock(p.rank());
    if (next.isPresent()) {
      messages.send(
          sender,
          "rank.show.next",
          Placeholder.unparsed("rank", String.valueOf(next.get().rank())),
          Placeholder.component("unlock", describe(next.get())));
    }
  }

  private void top(CommandSender sender) {
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              List<Entry> entries = top.read();
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        messages.send(sender, "rank.top.header");
                        if (entries.isEmpty()) {
                          messages.send(sender, "rank.top.empty");
                          return;
                        }
                        int position = 1;
                        for (Entry entry : entries) {
                          messages.send(
                              sender,
                              "rank.top.entry",
                              Placeholder.unparsed("position", String.valueOf(position++)),
                              Placeholder.unparsed("player", entry.name()),
                              Placeholder.unparsed(
                                  "rank", String.valueOf(entry.score() / RANK_SCORE_SCALE)),
                              Placeholder.unparsed("xp", number(entry.score() % RANK_SCORE_SCALE)));
                        }
                      });
            });
  }

  private void unlocks(CommandSender sender) {
    int rank = sender instanceof Player player ? ranks.getRank(player.getUniqueId()) : 0;
    messages.send(sender, "rank.unlocks.header");
    for (Unlock unlock : unlocks.unlocks()) {
      boolean open = unlock.rank() <= rank;
      messages.send(
          sender,
          open ? "rank.unlocks.open" : "rank.unlocks.locked",
          Placeholder.unparsed("rank", String.valueOf(unlock.rank())),
          Placeholder.component("unlock", describe(unlock)));
    }
  }

  private net.kyori.adventure.text.Component describe(Unlock unlock) {
    TagResolver name = Placeholder.unparsed("name", unlock.name());
    return switch (unlock.kind()) {
      case ENCHANT_SLOT -> messages.render("rank.unlock.enchant-slot", name);
      case BOOK_TIER -> messages.render("rank.unlock.book-tier", name);
      case MINE_TIER -> messages.render("rank.unlock.mine-tier", name);
      case KIT -> messages.render("rank.unlock.kit", name);
    };
  }

  private static String number(long value) {
    return NumberFormat.getIntegerInstance(Locale.US).format(value);
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 1) {
      return List.of();
    }
    String prefix = args[0].toLowerCase(Locale.ROOT);
    return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
  }
}
