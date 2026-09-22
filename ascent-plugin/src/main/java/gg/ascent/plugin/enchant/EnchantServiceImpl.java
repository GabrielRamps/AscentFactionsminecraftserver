package gg.ascent.plugin.enchant;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/** {@link EnchantService}: books as items, the reveal, and reading gear (PRD E3-S2). */
public final class EnchantServiceImpl implements EnchantService {

  private static final Gson GSON = new Gson();
  private static final java.lang.reflect.Type ENCHANT_MAP =
      new TypeToken<Map<String, Integer>>() {}.getType();

  private final ConfigService config;
  private final ItemRegistry items;
  private final RandomGenerator random;
  private final MiniMessage mini = MiniMessage.miniMessage();

  public EnchantServiceImpl(ConfigService config, ItemRegistry items, RandomGenerator random) {
    this.config = config;
    this.items = items;
    this.random = random;
  }

  private EnchantsSettings settings() {
    return config.enchants();
  }

  @Override
  public ItemStack createUnopenedBook(
      Tier tier, int amount, @Nullable UUID creator, String reason) {
    ItemStack book = new ItemStack(Material.BOOK, Math.max(1, Math.min(64, amount)));
    ItemMeta meta = book.getItemMeta();
    meta.displayName(plain(mini.deserialize(BookLore.unopenedName(settings(), tier))));
    List<Component> lore = new ArrayList<>();
    for (String line : BookLore.unopenedLore(settings(), tier)) {
      lore.add(plain(mini.deserialize(line)));
    }
    meta.lore(lore);
    meta.getPersistentDataContainer()
        .set(EnchantKeys.BOOK_TIER, PersistentDataType.STRING, tier.name());
    book.setItemMeta(meta);
    items.tag(book, ItemKind.BOOK_UNOPENED, Map.of("tier", tier.name()), creator, reason);
    return book;
  }

  @Override
  public ItemStack reveal(ItemStack unopened, @Nullable UUID player) {
    Tier tier =
        unopenedTier(unopened)
            .orElseThrow(() -> new IllegalArgumentException("not an unopened book"));
    OpenedBook roll = BookRoller.roll(settings(), tier, random);
    EnchantDefinition enchant = settings().enchant(roll.enchantId()).orElseThrow();
    items
        .idOf(unopened)
        .ifPresent(
            id ->
                items.record(
                    id,
                    ItemEvent.CONSUMED,
                    player,
                    null,
                    Map.of(
                        "reason", "reveal", "enchant", roll.enchantId(), "level", roll.level())));
    return createOpenedBook(
        enchant, roll, player, "reveal:" + tier.name().toLowerCase(Locale.ROOT));
  }

  /** Builds an opened book carrying {@code roll}. Registry-tagged. */
  public ItemStack createOpenedBook(
      EnchantDefinition enchant, OpenedBook roll, @Nullable UUID creator, String reason) {
    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
    ItemMeta meta = book.getItemMeta();
    meta.displayName(
        plain(mini.deserialize(BookLore.openedName(settings(), enchant, roll.level()))));
    List<Component> lore = new ArrayList<>();
    for (String line : BookLore.openedLore(enchant, roll)) {
      lore.add(plain(mini.deserialize(line)));
    }
    meta.lore(lore);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(EnchantKeys.ENCHANT_ID, PersistentDataType.STRING, roll.enchantId());
    pdc.set(EnchantKeys.LEVEL, PersistentDataType.INTEGER, roll.level());
    pdc.set(EnchantKeys.SUCCESS, PersistentDataType.INTEGER, roll.success());
    pdc.set(EnchantKeys.DESTROY, PersistentDataType.INTEGER, roll.destroy());
    book.setItemMeta(meta);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("enchant", roll.enchantId());
    data.put("level", roll.level());
    data.put("success", roll.success());
    data.put("destroy", roll.destroy());
    data.put("tier", enchant.tier().name());
    items.tag(book, ItemKind.BOOK_OPENED, data, creator, reason);
    return book;
  }

  @Override
  public Optional<Tier> unopenedTier(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    if (pdc == null) {
      return Optional.empty();
    }
    String tier = pdc.get(EnchantKeys.BOOK_TIER, PersistentDataType.STRING);
    if (tier == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Tier.valueOf(tier));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<OpenedBook> openedBook(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    if (pdc == null) {
      return Optional.empty();
    }
    String id = pdc.get(EnchantKeys.ENCHANT_ID, PersistentDataType.STRING);
    Integer level = pdc.get(EnchantKeys.LEVEL, PersistentDataType.INTEGER);
    Integer success = pdc.get(EnchantKeys.SUCCESS, PersistentDataType.INTEGER);
    Integer destroy = pdc.get(EnchantKeys.DESTROY, PersistentDataType.INTEGER);
    if (id == null || level == null || success == null || destroy == null) {
      return Optional.empty();
    }
    return Optional.of(new OpenedBook(id, level, success, destroy));
  }

  @Override
  public Map<String, Integer> getEnchants(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    if (pdc == null) {
      return Map.of();
    }
    String json = pdc.get(EnchantKeys.ENCHANTS, PersistentDataType.STRING);
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Integer> parsed = GSON.fromJson(json, ENCHANT_MAP);
      return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
    } catch (JsonSyntaxException e) {
      return Map.of();
    }
  }

  @Override
  public Optional<EnchantDefinition> definition(String enchantId) {
    return settings().enchant(enchantId);
  }

  @Override
  public List<EnchantDefinition> pool(Tier tier) {
    return settings().byTier(tier);
  }

  private static @Nullable PersistentDataContainer pdc(@Nullable ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    return meta == null ? null : meta.getPersistentDataContainer();
  }

  private static Component plain(Component c) {
    return c.decoration(TextDecoration.ITALIC, false);
  }
}
