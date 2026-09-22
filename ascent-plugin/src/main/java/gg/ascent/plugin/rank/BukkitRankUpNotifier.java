package gg.ascent.plugin.rank;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.event.RankUpEvent;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerProfile;
import java.time.Duration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/** Event, chat line, title and sound (PRD E2-S1). */
public final class BukkitRankUpNotifier implements RankUpNotifier {

  private final Server server;
  private final Messages messages;
  private final ConfigService config;
  private final Logger log;

  public BukkitRankUpNotifier(Server server, Messages messages, ConfigService config, Logger log) {
    this.server = server;
    this.messages = messages;
    this.config = config;
    this.log = log;
  }

  @Override
  public void rankedUp(PlayerProfile profile, int oldRank, int newRank) {
    Player player = server.getPlayer(profile.uuid());
    if (player == null) {
      return;
    }
    server.getPluginManager().callEvent(new RankUpEvent(player, oldRank, newRank));
    var rank = Placeholder.unparsed("rank", String.valueOf(newRank));
    var previous = Placeholder.unparsed("previous", String.valueOf(oldRank));
    messages.send(player, "rank.up.chat", rank, previous);
    player.showTitle(
        Title.title(
            messages.render("rank.up.title", rank, previous),
            messages.render("rank.up.subtitle", rank, previous),
            Title.Times.times(
                Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))));
    String soundKey = config.ranks().rankUp().sound();
    try {
      player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, 1.0f, 1.0f));
    } catch (RuntimeException e) {
      log.warn("ranks.yml: rank-up.sound '{}' is not a valid sound key", soundKey);
    }
  }
}
