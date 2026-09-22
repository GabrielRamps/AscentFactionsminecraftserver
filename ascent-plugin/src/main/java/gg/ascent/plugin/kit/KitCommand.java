package gg.ascent.plugin.kit;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.kit.KitService.ClaimResult;
import gg.ascent.api.kit.KitService.KitStatus;
import gg.ascent.api.message.Messages;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /kit} opens the menu; {@code /kit <id>} claims directly (PRD E2-S3). */
public final class KitCommand implements CommandExecutor, TabCompleter {

  private final KitManager kits;
  private final ConfigService config;
  private final Messages messages;

  public KitCommand(KitManager kits, ConfigService config, Messages messages) {
    this.kits = kits;
    this.config = config;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "kit.console");
      return true;
    }
    if (args.length == 0) {
      player.openInventory(KitMenu.build(player, kits, config, messages).getInventory());
      return true;
    }
    String kitId = args[0].toLowerCase(Locale.ROOT);
    report(player, kitId, kits.claim(player.getUniqueId(), kitId));
    return true;
  }

  /** Tells the player how a claim went. */
  void report(Player player, String kitId, ClaimResult result) {
    var kit = Placeholder.unparsed("kit", kitId);
    switch (result) {
      case OK -> messages.send(player, "kit.claimed", kit);
      case UNKNOWN_KIT -> messages.send(player, "kit.unknown", kit);
      case LOCKED -> {
        int minRank =
            kits.kits(player.getUniqueId()).stream()
                .filter(k -> k.id().equals(kitId))
                .map(KitStatus::minRank)
                .findFirst()
                .orElse(0);
        messages.send(
            player, "kit.locked", kit, Placeholder.unparsed("rank", String.valueOf(minRank)));
      }
      case ON_COOLDOWN -> {
        String left =
            kits.kits(player.getUniqueId()).stream()
                .filter(k -> k.id().equals(kitId))
                .map(k -> Durations.humanize(k.cooldownLeft()))
                .findFirst()
                .orElse("?");
        messages.send(player, "kit.cooldown", kit, Placeholder.unparsed("time", left));
      }
      case NOT_READY -> messages.send(player, "kit.not-ready", kit);
    }
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 1) {
      return List.of();
    }
    String prefix = args[0].toLowerCase(Locale.ROOT);
    return config.kits().byRank().stream()
        .map(k -> k.id())
        .filter(id -> id.startsWith(prefix))
        .toList();
  }
}
