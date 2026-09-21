package gg.ascent.plugin.player;

import gg.ascent.api.db.DbException;
import gg.ascent.api.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.slf4j.Logger;

/** Feeds Bukkit's login, join and quit events to the {@link PlayerManager}. */
public final class PlayerListener implements Listener {

  private final PlayerManager players;
  private final Messages messages;
  private final Logger log;

  public PlayerListener(PlayerManager players, Messages messages, Logger log) {
    this.players = players;
    this.messages = messages;
    this.log = log;
  }

  /** Off the main thread: load the row here so the join itself never waits on the database. */
  @EventHandler(priority = EventPriority.LOW)
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }
    try {
      players.preLogin(event.getUniqueId(), event.getName());
    } catch (DbException e) {
      log.error("Could not load player data for {} ({})", event.getName(), event.getUniqueId(), e);
      event.disallow(
          AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.render("player.load-failed"));
    }
  }

  /** Another plugin refused the login after our load ran: forget what we loaded. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onLogin(PlayerLoginEvent event) {
    if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
      players.loginRefused(event.getPlayer().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onJoin(PlayerJoinEvent event) {
    if (players.join(event.getPlayer().getUniqueId(), event.getPlayer().getName()).isEmpty()) {
      // Can only happen if the pre-login load was skipped (a reload mid-handshake, say).
      log.error("{} joined without loaded player data; kicking.", event.getPlayer().getName());
      Component reason = messages.render("player.load-failed");
      event.getPlayer().kick(reason);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    players.quit(event.getPlayer().getUniqueId());
  }
}
