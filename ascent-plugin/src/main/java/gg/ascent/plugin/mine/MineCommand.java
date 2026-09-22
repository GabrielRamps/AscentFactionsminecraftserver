package gg.ascent.plugin.mine;

import gg.ascent.api.message.Messages;
import gg.ascent.api.mine.MineService;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /mine}: go to your plot (PRD E4-S1). */
public final class MineCommand implements CommandExecutor, TabCompleter {

  private final MineService mines;
  private final Messages messages;

  public MineCommand(MineService mines, Messages messages) {
    this.mines = mines;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "mine.console");
      return true;
    }
    mines.visit(player.getUniqueId());
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    return List.of();
  }
}
