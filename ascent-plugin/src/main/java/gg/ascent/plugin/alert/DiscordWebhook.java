package gg.ascent.plugin.alert;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Outgoing Discord webhook (PRD §6.6). Fire and forget: a failure is logged and ignored, and
 * nothing here ever blocks the main thread.
 */
public final class DiscordWebhook {

  private static final Gson GSON = new Gson();

  private final @Nullable URI url;
  private final HttpClient client;
  private final Logger log;

  /**
   * @param url the webhook URL, or null/blank to disable
   */
  public DiscordWebhook(@Nullable String url, Logger log) {
    this.url = url == null || url.isBlank() ? null : URI.create(url.trim());
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.log = log;
  }

  public boolean isEnabled() {
    return url != null;
  }

  /** Posts a plain message. Discord caps content at 2000 characters; longer text is cut. */
  public void send(String content) {
    if (url == null) {
      return;
    }
    String body =
        GSON.toJson(
            Map.of("content", content.length() > 1990 ? content.substring(0, 1990) : content));
    HttpRequest request =
        HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("User-Agent", "ascent-factions")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                log.warn("Discord webhook failed: {}", error.toString());
              } else if (response.statusCode() >= 300) {
                log.warn("Discord webhook answered {}: {}", response.statusCode(), response.body());
              }
            });
  }
}
