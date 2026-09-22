package gg.ascent.plugin.economy;

import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.player.PlayerSnapshot;
import gg.ascent.plugin.leaderboard.Leaderboard;
import gg.ascent.plugin.leaderboard.Leaderboard.Entry;
import gg.ascent.plugin.util.Futures;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/** {@code /bal [player]}, {@code /pay <player> <amount>} and {@code /baltop} (PRD E1-S4). */
public final class EconomyCommands implements CommandExecutor, TabCompleter {

  private final Plugin plugin;
  private final EconomyService economy;
  private final PlayerService players;
  private final Leaderboard baltop;
  private final Messages messages;
  private final Logger log;

  public EconomyCommands(
      Plugin plugin,
      EconomyService economy,
      PlayerService players,
      Leaderboard baltop,
      Messages messages,
      Logger log) {
    this.plugin = plugin;
    this.economy = economy;
    this.players = players;
    this.baltop = baltop;
    this.messages = messages;
    this.log = log;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    switch (command.getName().toLowerCase(Locale.ROOT)) {
      case "bal" -> balance(sender, args);
      case "pay" -> pay(sender, args);
      case "baltop" -> top(sender);
      default -> {
        return false;
      }
    }
    return true;
  }

  private void balance(CommandSender sender, String[] args) {
    if (args.length == 0) {
      if (!(sender instanceof Player player)) {
        messages.send(sender, "economy.bal.console");
        return;
      }
      messages.send(sender, "economy.bal.own", amount(economy.getBalance(player.getUniqueId())));
      return;
    }
    String name = args[0];
    Futures.logFailure(
        players
            .lookupByName(name)
            .thenAccept(
                found -> {
                  if (found.isEmpty()) {
                    messages.send(sender, "economy.unknown-player", name(name));
                    return;
                  }
                  PlayerSnapshot target = found.get();
                  messages.send(
                      sender, "economy.bal.other", name(target.name()), amount(target.balance()));
                }),
        log,
        "/bal " + name);
  }

  private void pay(CommandSender sender, String[] args) {
    if (!(sender instanceof Player payer)) {
      messages.send(sender, "economy.pay.console");
      return;
    }
    if (args.length != 2) {
      messages.send(sender, "economy.pay.usage");
      return;
    }
    OptionalLong parsed = Money.parse(args[1]);
    if (parsed.isEmpty()) {
      messages.send(sender, "economy.pay.bad-amount", Placeholder.unparsed("input", args[1]));
      return;
    }
    long amount = parsed.getAsLong();
    if (economy.getBalance(payer.getUniqueId()) < amount) {
      messages.send(
          sender,
          "economy.pay.insufficient",
          amount(amount),
          Placeholder.unparsed("balance", Money.format(economy.getBalance(payer.getUniqueId()))));
      return;
    }
    String name = args[0];
    if (name.equalsIgnoreCase(payer.getName())) {
      messages.send(sender, "economy.pay.self");
      return;
    }
    Futures.logFailure(
        players
            .lookupByName(name)
            .thenCompose(
                found -> {
                  if (found.isEmpty()) {
                    messages.send(sender, "economy.unknown-player", name(name));
                    return java.util.concurrent.CompletableFuture.completedFuture(
                        Optional.<PlayerSnapshot>empty());
                  }
                  PlayerSnapshot target = found.get();
                  return economy
                      .transferOffline(payer.getUniqueId(), target.uuid(), amount, TxReason.PAY)
                      .thenApply(ok -> ok ? Optional.of(target) : Optional.<PlayerSnapshot>empty());
                })
            .thenAccept(
                paid -> {
                  if (paid.isEmpty()) {
                    return;
                  }
                  PlayerSnapshot target = paid.get();
                  messages.send(sender, "economy.pay.sent", name(target.name()), amount(amount));
                  Player online = Bukkit.getPlayer(target.uuid());
                  if (online != null) {
                    messages.send(
                        online, "economy.pay.received", name(payer.getName()), amount(amount));
                  }
                }),
        log,
        "/pay " + name + " " + amount);
  }

  private void top(CommandSender sender) {
    // Redis is a network hop; read it off the main thread and come back to reply.
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              List<Entry> entries = baltop.read();
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        messages.send(sender, "economy.baltop.header");
                        if (entries.isEmpty()) {
                          messages.send(sender, "economy.baltop.empty");
                          return;
                        }
                        int position = 1;
                        for (Entry entry : entries) {
                          messages.send(
                              sender,
                              "economy.baltop.entry",
                              Placeholder.unparsed("position", String.valueOf(position++)),
                              name(entry.name()),
                              amount(entry.score()));
                        }
                      });
            });
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    String cmd = command.getName().toLowerCase(Locale.ROOT);
    if (args.length == 1 && (cmd.equals("bal") || cmd.equals("pay"))) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
          .sorted()
          .toList();
    }
    if (args.length == 2 && cmd.equals("pay")) {
      return List.of("100", "1000", "10000");
    }
    return List.of();
  }

  private static TagResolver amount(long amount) {
    return Placeholder.unparsed("amount", Money.format(amount));
  }

  private static TagResolver name(String name) {
    return Placeholder.unparsed("player", name);
  }
}
