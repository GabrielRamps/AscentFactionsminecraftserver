package gg.ascent.api.config;

/**
 * A configuration file could not be parsed or failed validation.
 *
 * <p>The message always names the file and the path inside it, so an operator reading the log knows
 * exactly which line to fix.
 */
public final class ConfigException extends RuntimeException {

  public ConfigException(String message) {
    super(message);
  }

  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
