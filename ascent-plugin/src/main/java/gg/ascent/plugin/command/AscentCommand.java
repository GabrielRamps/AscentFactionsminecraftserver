package gg.ascent.plugin.command;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.ReloadReport;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.AscentPlugin;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Administrative root command: {@code /ascent version} and {@code /ascent reload}.
 *
 * <p>Later stories add {@code give}, {@code rank}, {@code debug}, {@code item lookup} and {@code
 * rollback} here (PRD §6.4.1). Every string comes from {@code messages.yml}.
 */
public final class AscentCommand implements CommandExecutor, TabCompleter {

  private static final List<String> SUBCOMMANDS = List.of("version", "reload");

  private final AscentPlugin plugin;
  private final ConfigService config;
  private final Messages messages;

  public AscentCommand(AscentPlugin plugin, ConfigService config, Messages messages) {
    this.plugin = plugin;
    this.config = config;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "version" -> version(sender);
      case "reload" -> reload(sender);
      default -> messages.send(sender, "ascent.usage");
    }
    return true;
  }

  private void version(CommandSender sender) {
    messages.send(
        sender,
        "ascent.version",
        Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
  }

  private void reload(CommandSender sender) {
    ReloadReport report = config.reload();
    if (report.allOk()) {
      messages.send(
          sender,
          "ascent.reload.ok",
          Placeholder.unparsed("count", String.valueOf(report.files().size())));
      return;
    }
    messages.send(
        sender,
        "ascent.reload.failed",
        Placeholder.unparsed("count", String.valueOf(report.failures().size())));
    for (ReloadReport.FileResult failure : report.failures()) {
      messages.send(sender, "ascent.reload.error", Placeholder.unparsed("error", failure.error()));
    }
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
