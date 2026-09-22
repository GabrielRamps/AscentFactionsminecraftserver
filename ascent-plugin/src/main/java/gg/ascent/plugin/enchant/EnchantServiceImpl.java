package gg.ascent.plugin.enchant;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.db.DbExecutor;
import gg.ascent.api.enchant.ApplyResult;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.Incompatibility;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.event.EnchantAppliedEvent;
import gg.ascent.api.event.EnchantApplyEvent;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemKind;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.rank.UnlockService;
import gg.ascent.plugin.item.LoreRenderer;
import gg.ascent.plugin.item.LoreRenderer.EnchantLine;
import gg.ascent.plugin.item.LoreRenderer.LoreSpec;
import gg.ascent.plugin.util.Futures;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * {@link EnchantService}: books as items, the reveal, the apply roll, and gear (PRD E3-S2, E3-S3).
 */
public final class EnchantServiceImpl implements EnchantService {

  private static final Gson GSON = new Gson();
  private static final java.lang.reflect.Type ENCHANT_MAP =
      new TypeToken<Map<String, Integer>>() {}.getType();

  private final Server server;
  private final ConfigService config;
  private final ItemRegistry items;
  private final PlayerService players;
  private final UnlockService unlocks;
  private final LoreRenderer lore;
  private final Messages messages;
  private final DbExecutor db;
  private final EnchantRollRepository rolls;
  private final RandomGenerator random;
  private final Clock clock;
  private final Logger log;
  private final MiniMessage mini = MiniMessage.miniMessage();

  public EnchantServiceImpl(
      Server server,
      ConfigService config,
      ItemRegistry items,
      PlayerService players,
      UnlockService unlocks,
      LoreRenderer lore,
      Messages messages,
      DbExecutor db,
      EnchantRollRepository rolls,
      RandomGenerator random,
      Clock clock,
      Logger log) {
    this.server = server;
    this.config = config;
    this.items = items;
    this.players = players;
    this.unlocks = unlocks;
    this.lore = lore;
    this.messages = messages;
    this.db = db;
    this.rolls = rolls;
    this.random = random;
    this.clock = clock;
    this.log = log;
  }

  private EnchantsSettings settings() {
    return config.enchants();
  }

  // --- books -----------------------------------------------------------------------------------

  @Override
  public ItemStack createUnopenedBook(
      Tier tier, int amount, @Nullable UUID creator, String reason) {
    ItemStack book = new ItemStack(Material.BOOK, Math.max(1, Math.min(64, amount)));
    ItemMeta meta = book.getItemMeta();
    meta.displayName(plain(mini.deserialize(BookLore.unopenedName(settings(), tier))));
    List<Component> lines = new ArrayList<>();
    for (String line : BookLore.unopenedLore(settings(), tier)) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
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

  @Override
  public ItemStack createOpenedBook(OpenedBook book, @Nullable UUID creator, String reason) {
    EnchantDefinition def =
        settings()
            .enchant(book.enchantId())
            .orElseThrow(() -> new IllegalArgumentException("unknown enchant " + book.enchantId()));
    return createOpenedBook(def, book, creator, reason);
  }

  /** Builds an opened book carrying {@code roll}. Registry-tagged. */
  public ItemStack createOpenedBook(
      EnchantDefinition enchant, OpenedBook roll, @Nullable UUID creator, String reason) {
    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
    ItemMeta meta = book.getItemMeta();
    meta.displayName(
        plain(mini.deserialize(BookLore.openedName(settings(), enchant, roll.level()))));
    List<Component> lines = new ArrayList<>();
    for (String line : BookLore.openedLore(enchant, roll)) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
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

  // --- gear ------------------------------------------------------------------------------------

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
  public boolean isProtected(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    Byte flag = pdc == null ? null : pdc.get(EnchantKeys.WHITE_SCROLL, PersistentDataType.BYTE);
    return flag != null && flag == 1;
  }

  private void writeEnchants(ItemStack gear, Map<String, Integer> enchants) {
    ItemMeta meta = gear.getItemMeta();
    if (enchants.isEmpty()) {
      meta.getPersistentDataContainer().remove(EnchantKeys.ENCHANTS);
    } else {
      meta.getPersistentDataContainer()
          .set(EnchantKeys.ENCHANTS, PersistentDataType.STRING, GSON.toJson(enchants));
    }
    gear.setItemMeta(meta);
  }

  @Override
  public void refreshLore(ItemStack gear) {
    Map<String, Integer> enchants = getEnchants(gear);
    List<EnchantLine> lines = new ArrayList<>();
    for (Map.Entry<String, Integer> e : enchants.entrySet()) {
      settings()
          .enchant(e.getKey())
          .ifPresent(
              def -> lines.add(new EnchantLine(def.id(), def.display(), def.tier(), e.getValue())));
    }
    Component footer = items.idOf(gear).isPresent() ? messages.render("lore.footer.gear") : null;
    lore.apply(gear, new LoreSpec(lines, isProtected(gear), footer));
  }

  @Override
  public boolean setEnchant(
      ItemStack target, String enchantId, int level, @Nullable UUID actor, String reason) {
    Optional<EnchantDefinition> def = settings().enchant(enchantId);
    if (def.isEmpty() || level < 1 || level > def.get().maxLevel()) {
      return false;
    }
    Optional<gg.ascent.api.enchant.ItemTarget> kind =
        gg.ascent.api.enchant.ItemTarget.classify(target.getType().name());
    if (kind.isEmpty() || !def.get().appliesTo(kind.get())) {
      return false;
    }
    Map<String, Integer> enchants = new LinkedHashMap<>(getEnchants(target));
    enchants.put(enchantId, level);
    writeEnchants(target, enchants);
    UUID id = ensureTagged(target, actor, reason);
    items.record(
        id,
        ItemEvent.APPLIED,
        actor,
        null,
        Map.of("enchant", enchantId, "level", level, "reason", reason));
    refreshLore(target);
    return true;
  }

  private UUID ensureTagged(ItemStack gear, @Nullable UUID actor, String reason) {
    Optional<UUID> existing = items.idOf(gear);
    if (existing.isPresent()) {
      return existing.get();
    }
    items.tag(gear, ItemKind.GEAR, Map.of("material", gear.getType().name()), actor, reason);
    return items.idOf(gear).orElseThrow();
  }

  // --- the roll --------------------------------------------------------------------------------

  @Override
  public Optional<Incompatibility> checkCompatible(ItemStack book, ItemStack target, int rank) {
    Optional<OpenedBook> opened = openedBook(book);
    if (opened.isEmpty() || target == null || target.getType().isAir()) {
      return Optional.of(Incompatibility.NOT_GEAR);
    }
    Optional<EnchantDefinition> def = settings().enchant(opened.get().enchantId());
    if (def.isEmpty()) {
      return Optional.of(Incompatibility.NOT_GEAR);
    }
    return ApplyRules.check(
        def.get(),
        opened.get().level(),
        target.getType().name(),
        getEnchants(target),
        unlocks.enchantSlotCap(rank));
  }

  @Override
  public ApplyResult apply(ItemStack book, ItemStack target, Player player) {
    int rank = players.require(player.getUniqueId()).rank();
    if (checkCompatible(book, target, rank).isPresent()) {
      return ApplyResult.INCOMPATIBLE;
    }
    OpenedBook opened = openedBook(book).orElseThrow();
    EnchantDefinition def = settings().enchant(opened.enchantId()).orElseThrow();

    EnchantApplyEvent event =
        new EnchantApplyEvent(player, book, target, opened.enchantId(), opened.level());
    server.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      return ApplyResult.INCOMPATIBLE;
    }

    UUID actor = player.getUniqueId();
    UUID bookId = items.idOf(book).orElse(null);
    UUID targetId = ensureTagged(target, actor, "apply");
    boolean scrolled = isProtected(target);
    ApplyResult result = ApplyRoller.roll(opened.success(), opened.destroy(), scrolled, random);
    event.setResult(result);

    Map<String, Object> context =
        Map.of(
            "enchant", opened.enchantId(),
            "level", opened.level(),
            "success", opened.success(),
            "destroy", opened.destroy(),
            "outcome", result.name(),
            "target", targetId.toString());
    if (bookId != null) {
      items.record(
          bookId,
          result == ApplyResult.SUCCESS ? ItemEvent.APPLIED : ItemEvent.CONSUMED,
          actor,
          null,
          context);
    }
    switch (result) {
      case SUCCESS -> {
        Map<String, Integer> enchants = new LinkedHashMap<>(getEnchants(target));
        enchants.put(opened.enchantId(), opened.level());
        writeEnchants(target, enchants);
        items.record(targetId, ItemEvent.APPLIED, actor, null, context);
        refreshLore(target);
      }
      case DESTROYED -> {
        items.record(targetId, ItemEvent.DESTROYED, actor, null, context);
        target.setAmount(0);
      }
      case SCROLL_SAVED -> {
        ItemMeta meta = target.getItemMeta();
        meta.getPersistentDataContainer().remove(EnchantKeys.WHITE_SCROLL);
        target.setItemMeta(meta);
        items.record(
            targetId,
            ItemEvent.CONSUMED,
            actor,
            null,
            Map.of("what", "white_scroll", "enchant", opened.enchantId()));
        refreshLore(target);
      }
      case FAILED, INCOMPATIBLE -> {
        // nothing on the item
      }
    }
    book.setAmount(book.getAmount() - 1);

    Instant now = clock.instant();
    if (bookId != null) {
      UUID finalBookId = bookId;
      Futures.logFailure(
          db.run(
              c ->
                  rolls.record(
                      c,
                      actor,
                      finalBookId,
                      targetId,
                      opened.enchantId(),
                      opened.level(),
                      opened.success(),
                      opened.destroy(),
                      result,
                      now)),
          log,
          "recording enchant roll");
    }
    server
        .getPluginManager()
        .callEvent(
            new EnchantAppliedEvent(player, target, opened.enchantId(), opened.level(), result));
    return result;
  }

  // --- scrolls and dust (PRD E3-S5) ---------------------------------------------------------

  @Override
  public ItemStack createWhiteScroll(int amount, @Nullable UUID creator, String reason) {
    ItemStack scroll = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
    ItemMeta meta = scroll.getItemMeta();
    meta.displayName(plain(mini.deserialize(DustLore.scrollName())));
    List<Component> lines = new ArrayList<>();
    for (String line : DustLore.scrollLore()) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
    meta.getPersistentDataContainer()
        .set(EnchantKeys.SCROLL_ITEM, PersistentDataType.BYTE, (byte) 1);
    scroll.setItemMeta(meta);
    items.tag(scroll, ItemKind.WHITE_SCROLL, Map.of(), creator, reason);
    return scroll;
  }

  @Override
  public ItemStack createMagicDust(
      Tier tier, int percent, int amount, @Nullable UUID creator, String reason) {
    int p = Math.max(1, Math.min(15, percent));
    ItemStack dust = new ItemStack(Material.GLOWSTONE_DUST, Math.max(1, Math.min(64, amount)));
    ItemMeta meta = dust.getItemMeta();
    meta.displayName(plain(mini.deserialize(DustLore.dustName(settings(), tier, p))));
    List<Component> lines = new ArrayList<>();
    for (String line : DustLore.dustLore(settings(), tier, p)) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(EnchantKeys.DUST_TIER, PersistentDataType.STRING, tier.name());
    pdc.set(EnchantKeys.DUST_PERCENT, PersistentDataType.INTEGER, p);
    dust.setItemMeta(meta);
    items.tag(
        dust, ItemKind.MAGIC_DUST, Map.of("tier", tier.name(), "percent", p), creator, reason);
    return dust;
  }

  @Override
  public boolean isWhiteScroll(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    Byte flag = pdc == null ? null : pdc.get(EnchantKeys.SCROLL_ITEM, PersistentDataType.BYTE);
    return flag != null && flag == 1;
  }

  @Override
  public Optional<MagicDust> magicDust(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    if (pdc == null) {
      return Optional.empty();
    }
    String tier = pdc.get(EnchantKeys.DUST_TIER, PersistentDataType.STRING);
    Integer percent = pdc.get(EnchantKeys.DUST_PERCENT, PersistentDataType.INTEGER);
    if (tier == null || percent == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new MagicDust(Tier.valueOf(tier), percent));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean applyScroll(ItemStack scroll, ItemStack gear, @Nullable UUID actor) {
    if (!isWhiteScroll(scroll)
        || gear == null
        || gg.ascent.api.enchant.ItemTarget.classify(gear.getType().name()).isEmpty()
        || isProtected(gear)) {
      return false;
    }
    ItemMeta meta = gear.getItemMeta();
    meta.getPersistentDataContainer()
        .set(EnchantKeys.WHITE_SCROLL, PersistentDataType.BYTE, (byte) 1);
    gear.setItemMeta(meta);
    UUID gearId = ensureTagged(gear, actor, "scroll");
    items
        .idOf(scroll)
        .ifPresent(
            id ->
                items.record(
                    id, ItemEvent.CONSUMED, actor, null, Map.of("gear", gearId.toString())));
    items.record(gearId, ItemEvent.APPLIED, actor, null, Map.of("what", "white_scroll"));
    scroll.setAmount(scroll.getAmount() - 1);
    refreshLore(gear);
    return true;
  }

  @Override
  public boolean applyDust(ItemStack dust, ItemStack book, @Nullable UUID actor) {
    Optional<MagicDust> d = magicDust(dust);
    Optional<OpenedBook> b = openedBook(book);
    if (d.isEmpty() || b.isEmpty() || b.get().success() >= 100) {
      return false;
    }
    EnchantDefinition def = settings().enchant(b.get().enchantId()).orElse(null);
    if (def == null || def.tier() != d.get().tier()) {
      return false;
    }
    int success = DustLore.boostedSuccess(b.get().success(), d.get().percent());
    OpenedBook boosted =
        new OpenedBook(b.get().enchantId(), b.get().level(), success, b.get().destroy());
    ItemMeta meta = book.getItemMeta();
    meta.getPersistentDataContainer().set(EnchantKeys.SUCCESS, PersistentDataType.INTEGER, success);
    List<Component> lines = new ArrayList<>();
    for (String line : BookLore.openedLore(def, boosted)) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
    book.setItemMeta(meta);
    Optional<UUID> bookId = items.idOf(book);
    items
        .idOf(dust)
        .ifPresent(
            id ->
                items.record(
                    id,
                    ItemEvent.CONSUMED,
                    actor,
                    null,
                    Map.of("book", bookId.map(UUID::toString).orElse("?"), "success", success)));
    bookId.ifPresent(
        id ->
            items.record(
                id,
                ItemEvent.APPLIED,
                actor,
                null,
                Map.of("what", "magic_dust", "percent", d.get().percent(), "success", success)));
    dust.setAmount(dust.getAmount() - 1);
    return true;
  }

  // --- XP bottles (PRD E3-S7) ---------------------------------------------------------------

  @Override
  public ItemStack createXpBottle(int levels, int amount, @Nullable UUID creator, String reason) {
    int l = Math.max(1, levels);
    // Paper underneath, drawn as an experience bottle: a real EXPERIENCE_BOTTLE makes the client
    // predict a throw and remove the item before the server cancels it, which looks like the
    // bottle vanishing and coming back. Paper has no client-side use, so nothing flickers.
    ItemStack bottle = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
    ItemMeta meta = bottle.getItemMeta();
    meta.setItemModel(org.bukkit.NamespacedKey.minecraft("experience_bottle"));
    meta.displayName(plain(mini.deserialize(BottleLore.name(l))));
    List<Component> lines = new ArrayList<>();
    for (String line : BottleLore.lore(l)) {
      lines.add(plain(mini.deserialize(line)));
    }
    meta.lore(lines);
    meta.getPersistentDataContainer().set(EnchantKeys.XP_LEVELS, PersistentDataType.INTEGER, l);
    bottle.setItemMeta(meta);
    items.tag(bottle, ItemKind.XP_BOTTLE, Map.of("levels", l), creator, reason);
    return bottle;
  }

  @Override
  public OptionalInt xpBottleLevels(@Nullable ItemStack item) {
    PersistentDataContainer pdc = pdc(item);
    Integer levels =
        pdc == null ? null : pdc.get(EnchantKeys.XP_LEVELS, PersistentDataType.INTEGER);
    return levels == null ? OptionalInt.empty() : OptionalInt.of(levels);
  }

  @Override
  public double bonusXpPercent(@Nullable ItemStack tool) {
    List<gg.ascent.plugin.enchant.runtime.GearEnchant> gear = new ArrayList<>();
    for (Map.Entry<String, Integer> e : getEnchants(tool).entrySet()) {
      settings()
          .enchant(e.getKey())
          .ifPresent(
              def -> gear.add(new gg.ascent.plugin.enchant.runtime.GearEnchant(def, e.getValue())));
    }
    return gg.ascent.plugin.enchant.runtime.ToolEffects.bonusXpPercent(gear, random);
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
