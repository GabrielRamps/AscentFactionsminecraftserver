package gg.ascent.api.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Every player-facing string, from {@code messages.yml}.
 *
 * <p>Strings are MiniMessage. Placeholders are supplied as {@link TagResolver}s, typically from
 * {@code Placeholder.unparsed("player", name)}. The {@code <prefix>} tag is always available. A
 * missing key renders as a visible marker rather than throwing, so a typo in a key never breaks a
 * command.
 */
public interface Messages {

  /** Renders {@code key} with the given placeholders. */
  Component render(String key, TagResolver... placeholders);

  /** Renders and sends in one step. */
  void send(Audience to, String key, TagResolver... placeholders);

  /** Whether {@code key} exists. Useful for optional messages an operator may blank out. */
  boolean has(String key);
}
