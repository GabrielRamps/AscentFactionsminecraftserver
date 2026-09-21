package gg.ascent.plugin.message;

import gg.ascent.api.config.ConfigException;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.config.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

/**
 * {@code messages.yml}, rendered with MiniMessage.
 *
 * <p>Keys are the flattened YAML path, so {@code ascent: {reload: {ok: ...}}} is {@code
 * ascent.reload.ok}. The {@code prefix} key is exposed to every message as {@code <prefix>}.
 */
public final class YamlMessages implements Messages {

  public static final String FILE = "messages.yml";
  private static final String PREFIX_KEY = "prefix";

  private final MiniMessage mini = MiniMessage.miniMessage();
  private final Logger log;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();
  private volatile Map<String, String> raw = Map.of();
  private volatile Component prefix = Component.empty();

  public YamlMessages(Logger log) {
    this.log = log;
  }

  /** Parses the file into a flat key-to-MiniMessage map. Every value must be a string. */
  public static Map<String, String> parse(Node root) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : root.deepKeys()) {
      if (!root.isString(key)) {
        throw new ConfigException(root.at(key) + ": expected a string");
      }
      out.put(key, root.rawString(key));
    }
    return out;
  }

  /** Installs a newly parsed map. Called by the config service on load and reload. */
  public void accept(Map<String, String> messages) {
    Map<String, String> copy = Map.copyOf(messages);
    Component newPrefix =
        copy.containsKey(PREFIX_KEY) ? mini.deserialize(copy.get(PREFIX_KEY)) : Component.empty();
    this.raw = copy;
    this.prefix = newPrefix;
    warned.clear();
  }

  /** The current map, for the config service's keep-last-good bookkeeping. */
  public Map<String, String> current() {
    return raw.isEmpty() ? null : raw;
  }

  @Override
  public Component render(String key, TagResolver... placeholders) {
    String text = raw.get(key);
    if (text == null) {
      if (warned.add(key)) {
        log.warn("messages.yml has no key '{}'", key);
      }
      return Component.text("<missing message: " + key + ">", NamedTextColor.RED);
    }
    TagResolver resolver =
        TagResolver.resolver(
            Placeholder.component(PREFIX_KEY, prefix), TagResolver.resolver(placeholders));
    return mini.deserialize(text, resolver);
  }

  @Override
  public void send(Audience to, String key, TagResolver... placeholders) {
    to.sendMessage(render(key, placeholders));
  }

  @Override
  public boolean has(String key) {
    return raw.containsKey(key);
  }
}
