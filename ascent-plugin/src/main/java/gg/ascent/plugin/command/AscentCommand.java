package gg.ascent.plugin.command;

import gg.ascent.plugin.AscentPlugin;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Administrative root command.
 *
 * <p>Epic 0 ships {@code /ascent version} only, so that a freshly built jar can be verified on the
 * dev server. Story E1-S1 adds {@code /ascent reload}, and later stories add the debug subcommands
 * described in the PRD.
 */
public final class AscentCommand implements CommandExecutor, TabCompleter {

  private static final List<String> SUBCOMMANDS = List.of("version");

  private final AscentPlugin plugin;

  public AscentCommand(AscentPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0 || !args[0].toLowerCase(Locale.ROOT).equals("version")) {
      sender.sendMessage(
          MiniMessage.miniMessage().deserialize("<gray>Usage: <white>/ascent version"));
      return true;
    }

    sender.sendMessage(
        MiniMessage.miniMessage()
            .deserialize(
                "<gradient:#f2c14e:#f78154>Ascent</gradient> <gray>v<version>",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion())));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 1) {
      return List.of();
    }
    String prefix = args[0].toLowerCase(Locale.ROOT);
    return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
  }
}
