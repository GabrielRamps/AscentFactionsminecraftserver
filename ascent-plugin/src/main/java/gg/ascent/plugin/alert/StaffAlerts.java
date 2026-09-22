package gg.ascent.plugin.alert;

import gg.ascent.api.message.Messages;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Tells staff about something: in game to everyone with {@code ascent.staff.alerts} (ops and admins
 * have it), and to Discord when a webhook is configured.
 */
public final class StaffAlerts {

  public static final String PERMISSION = "ascent.staff.alerts";

  private final Server server;
  private final Messages messages;
  private final DiscordWebhook discord;

  public StaffAlerts(Server server, Messages messages, DiscordWebhook discord) {
    this.server = server;
    this.messages = messages;
    this.discord = discord;
  }

  /** A duplicated item was found (PRD E1-S5). */
  public void dupe(UUID itemId, int observed, int created, String holders) {
    for (Player player : server.getOnlinePlayers()) {
      if (player.hasPermission(PERMISSION)) {
        messages.send(
            player,
            "alerts.dupe",
            Placeholder.unparsed("item", itemId.toString()),
            Placeholder.unparsed("observed", String.valueOf(observed)),
            Placeholder.unparsed("created", String.valueOf(created)),
            Placeholder.unparsed("holders", holders));
      }
    }
    discord.send(
        ":rotating_light: **Dupe alert** item `"
            + itemId
            + "` seen **"
            + observed
            + "** times, created "
            + created
            + ". Held by: "
            + holders
            + ". Look it up with `/ascent item lookup "
            + itemId
            + "`.");
  }

  public boolean discordEnabled() {
    return discord.isEnabled();
  }
}
