package gg.ascent.plugin.config;

import gg.ascent.api.config.ConfigException;
import gg.ascent.api.config.IntRange;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Typed, validating access to one YAML section.
 *
 * <p>Every getter either returns a value of the requested type or throws a {@link ConfigException}
 * whose message names the file and the full path, so a bad line is found without guessing. Parsers
 * are the only code that touches YAML; everything downstream sees settings records.
 */
public final class Node {

  private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([smhd])");

  private final String file;
  private final ConfigurationSection section;

  public Node(String file, ConfigurationSection section) {
    this.file = file;
    this.section = section;
  }

  /** The child section at {@code path}, which must exist. */
  public Node section(String path) {
    ConfigurationSection child = section.getConfigurationSection(path);
    if (child == null) {
      throw missing(path, "a section");
    }
    return new Node(file, child);
  }

  /** The child section at {@code path}, or an empty node if it is absent. */
  public Node sectionOrEmpty(String path) {
    ConfigurationSection child = section.getConfigurationSection(path);
    return new Node(file, child == null ? new MemoryConfiguration() : child);
  }

  public Set<String> keys() {
    return section.getKeys(false);
  }

  /** Every leaf path below this node, dotted, in file order. */
  public Set<String> deepKeys() {
    Set<String> out = new LinkedHashSet<>();
    for (String key : section.getKeys(true)) {
      if (!section.isConfigurationSection(key)) {
        out.add(key);
      }
    }
    return out;
  }

  public boolean isString(String path) {
    return section.isString(path);
  }

  /** The string at {@code path} as written, blank allowed; callers use this for message text. */
  public String rawString(String path) {
    String value = section.getString(path);
    if (value == null) {
      throw missing(path, "a string");
    }
    return value;
  }

  public boolean has(String path) {
    return section.contains(path, true);
  }

  public String string(String path) {
    Object value = raw(path);
    if (value instanceof String s && !s.isBlank()) {
      return s;
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    throw wrongType(path, "a non-empty string", value);
  }

  public boolean bool(String path) {
    Object value = raw(path);
    if (value instanceof Boolean b) {
      return b;
    }
    throw wrongType(path, "true or false", value);
  }

  public int integer(String path) {
    Object value = raw(path);
    if (value instanceof Integer i) {
      return i;
    }
    if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
      return l.intValue();
    }
    throw wrongType(path, "a whole number", value);
  }

  public long longValue(String path) {
    Object value = raw(path);
    if (value instanceof Integer || value instanceof Long) {
      return ((Number) value).longValue();
    }
    throw wrongType(path, "a whole number", value);
  }

  public double decimal(String path) {
    Object value = raw(path);
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw wrongType(path, "a number", value);
  }

  /** An integer that must be at least {@code min}. */
  public int integerAtLeast(String path, int min) {
    int value = integer(path);
    if (value < min) {
      throw new ConfigException(at(path) + ": must be at least " + min + ", got " + value);
    }
    return value;
  }

  /** A long that must be at least {@code min}. */
  public long longAtLeast(String path, long min) {
    long value = longValue(path);
    if (value < min) {
      throw new ConfigException(at(path) + ": must be at least " + min + ", got " + value);
    }
    return value;
  }

  /** A decimal within {@code [min, max]}. */
  public double decimalBetween(String path, double min, double max) {
    double value = decimal(path);
    if (value < min || value > max) {
      throw new ConfigException(
          at(path) + ": must be between " + min + " and " + max + ", got " + value);
    }
    return value;
  }

  /**
   * A duration written as {@code 30s}, {@code 15m}, {@code 6h} or {@code 1d}, or a bare number of
   * seconds.
   */
  public Duration duration(String path) {
    Object value = raw(path);
    if (value instanceof Integer || value instanceof Long) {
      long seconds = ((Number) value).longValue();
      if (seconds < 0) {
        throw new ConfigException(at(path) + ": duration cannot be negative");
      }
      return Duration.ofSeconds(seconds);
    }
    if (value instanceof String s) {
      Matcher m = DURATION.matcher(s.trim().toLowerCase(Locale.ROOT));
      if (m.matches()) {
        long amount = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
          case "s" -> Duration.ofSeconds(amount);
          case "m" -> Duration.ofMinutes(amount);
          case "h" -> Duration.ofHours(amount);
          default -> Duration.ofDays(amount);
        };
      }
    }
    throw wrongType(path, "a duration like 30s, 15m, 6h or 1d", value);
  }

  /** An enum constant, matched case-insensitively; the error lists the valid values. */
  public <E extends Enum<E>> E enumValue(String path, Class<E> type) {
    String name = string(path).trim().toUpperCase(Locale.ROOT).replace('-', '_');
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException e) {
      throw new ConfigException(
          at(path) + ": '" + name + "' is not one of " + List.of(type.getEnumConstants()));
    }
  }

  /** An inclusive range written as {@code [min, max]}, both within {@code [floor, ceiling]}. */
  public IntRange intRange(String path, int floor, int ceiling) {
    List<?> list = section.getList(path);
    boolean pair =
        list != null
            && list.size() == 2
            && list.get(0) instanceof Number
            && list.get(1) instanceof Number;
    if (!pair) {
      throw wrongType(path, "a pair like [min, max]", section.get(path));
    }
    int min = ((Number) list.get(0)).intValue();
    int max = ((Number) list.get(1)).intValue();
    if (min > max) {
      throw new ConfigException(at(path) + ": min " + min + " exceeds max " + max);
    }
    if (min < floor || max > ceiling) {
      throw new ConfigException(
          at(path)
              + ": values must be within "
              + floor
              + ".."
              + ceiling
              + ", got ["
              + min
              + ", "
              + max
              + "]");
    }
    return new IntRange(min, max);
  }

  public List<String> stringList(String path) {
    List<?> list = section.getList(path);
    if (list == null) {
      throw missing(path, "a list");
    }
    List<String> out = new ArrayList<>(list.size());
    for (int i = 0; i < list.size(); i++) {
      Object item = list.get(i);
      if (item instanceof String s && !s.isBlank()) {
        out.add(s);
      } else {
        throw new ConfigException(
            at(path) + "[" + i + "]: expected a non-empty string, got " + describe(item));
      }
    }
    return out;
  }

  /** A path for error messages: {@code file: a.b.c}. */
  public String at(String path) {
    String base = section.getCurrentPath();
    String full = base == null || base.isEmpty() ? path : base + "." + path;
    return file + ": " + full;
  }

  private Object raw(String path) {
    Object value = section.get(path);
    if (value == null) {
      throw missing(path, "a value");
    }
    return value;
  }

  private ConfigException missing(String path, String what) {
    return new ConfigException(at(path) + ": missing; expected " + what);
  }

  private ConfigException wrongType(String path, String expected, Object actual) {
    return new ConfigException(at(path) + ": expected " + expected + ", got " + describe(actual));
  }

  private static String describe(Object value) {
    if (value == null) {
      return "nothing";
    }
    if (value instanceof String s) {
      return "\"" + s + "\"";
    }
    return value + " (" + value.getClass().getSimpleName() + ")";
  }
}
