package gg.ascent.api.enchant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One enchant from {@code enchants.yml} (PRD E3-S1).
 *
 * @param id lowercase identifier, the key in the file and in item data
 * @param display the name players see
 * @param tier its book tier
 * @param appliesTo gear it may sit on, as written (may include ALL_ARMOR / ALL_WEAPONS)
 * @param maxLevel highest level
 * @param descriptions MiniMessage lore per level; a level absent here falls back to the highest
 *     defined level below it, and {@code <param>} placeholders are filled from the effect
 * @param effect what it does
 * @param cooldownTicks minimum ticks between procs on one player
 */
public record EnchantDefinition(
    String id,
    String display,
    Tier tier,
    Set<ItemTarget> appliesTo,
    int maxLevel,
    Map<Integer, String> descriptions,
    Effect effect,
    int cooldownTicks) {

  private static final Pattern PLACEHOLDER = Pattern.compile("<([a-z][a-z0-9_-]*)>");

  public EnchantDefinition {
    appliesTo = Set.copyOf(appliesTo);
    descriptions = Map.copyOf(descriptions);
    if (!id.matches("[a-z0-9_]+")) {
      throw new IllegalArgumentException("id must be lowercase letters, digits and underscores");
    }
    if (display == null || display.isBlank()) {
      throw new IllegalArgumentException("display must not be blank");
    }
    if (appliesTo.isEmpty()) {
      throw new IllegalArgumentException("applies-to must name at least one target");
    }
    if (maxLevel < 1 || maxLevel > 10) {
      throw new IllegalArgumentException("max-level must be 1..10, got " + maxLevel);
    }
    if (descriptions.isEmpty() || !descriptions.containsKey(1)) {
      throw new IllegalArgumentException("description needs at least level 1");
    }
    for (int level : descriptions.keySet()) {
      if (level < 1 || level > maxLevel) {
        throw new IllegalArgumentException(
            "description level " + level + " is outside 1.." + maxLevel);
      }
    }
    if (cooldownTicks < 0) {
      throw new IllegalArgumentException("cooldown-ticks cannot be negative");
    }
    for (Map.Entry<String, List<Double>> e : effect.perLevel().entrySet()) {
      if (e.getValue().size() != maxLevel) {
        throw new IllegalArgumentException(
            "effect."
                + e.getKey()
                + " has "
                + e.getValue().size()
                + " values for "
                + maxLevel
                + " levels");
      }
    }
    for (String required : effect.type().requiredParams()) {
      if (!effect.perLevel().containsKey(required)) {
        throw new IllegalArgumentException(
            "effect." + required + " is required by " + effect.type());
      }
    }
    if (effect.type().needsPotion() && effect.option("potion").isEmpty()) {
      throw new IllegalArgumentException("effect.potion is required by " + effect.type());
    }
  }

  /** Whether the enchant may sit on a concrete target. */
  public boolean appliesTo(ItemTarget concrete) {
    for (ItemTarget target : appliesTo) {
      if (target.covers(concrete)) {
        return true;
      }
    }
    return false;
  }

  /** The concrete targets this enchant may sit on. */
  public Set<ItemTarget> concreteTargets() {
    java.util.EnumSet<ItemTarget> out = java.util.EnumSet.noneOf(ItemTarget.class);
    for (ItemTarget target : appliesTo) {
      out.addAll(target.expand());
    }
    return out;
  }

  /** The lore line for {@code level}, with parameters filled in. */
  public String description(int level) {
    int clamped = Math.max(1, Math.min(maxLevel, level));
    String template = descriptions.get(1);
    for (int l = 1; l <= clamped; l++) {
      if (descriptions.containsKey(l)) {
        template = descriptions.get(l);
      }
    }
    Matcher m = PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String key = m.group(1);
      Optional<Double> value = effect.param(key, clamped);
      String replacement = value.map(EnchantDefinition::format).orElse(m.group(0));
      m.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);
    return out.toString();
  }

  /** {@code 2.0} prints as {@code 2}, {@code 1.5} as {@code 1.5}. */
  static String format(double value) {
    if (value == Math.rint(value)) {
      return String.valueOf((long) value);
    }
    return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  /**
   * @param type the behaviour
   * @param options string settings: {@code potion}, {@code target} (SELF, TARGET or ATTACKER) and
   *     {@code condition}
   * @param perLevel numeric parameters, one value per level
   */
  public record Effect(
      EffectType type, Map<String, String> options, Map<String, List<Double>> perLevel) {

    public Effect {
      options = Map.copyOf(options);
      java.util.Map<String, List<Double>> copy = new java.util.LinkedHashMap<>();
      perLevel.forEach((k, v) -> copy.put(k, List.copyOf(v)));
      perLevel = Map.copyOf(copy);
    }

    public Optional<String> option(String key) {
      return Optional.ofNullable(options.get(key));
    }

    /** The parameter's value at {@code level} (1-based), or empty if the effect lacks it. */
    public Optional<Double> param(String key, int level) {
      List<Double> values = perLevel.get(key);
      if (values == null || values.isEmpty()) {
        return Optional.empty();
      }
      int index = Math.max(0, Math.min(values.size() - 1, level - 1));
      return Optional.of(values.get(index));
    }

    /** The proc chance in percent at {@code level}; 100 when the effect has none. */
    public double chance(int level) {
      return param("chance", level).orElse(100.0);
    }
  }
}
