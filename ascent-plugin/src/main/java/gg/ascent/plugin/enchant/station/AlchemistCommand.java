package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantDefinition;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.EnchantService.MagicDust;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.enchant.BookLore;
import gg.ascent.plugin.enchant.station.AlchemistMath.BookFusion;
import gg.ascent.plugin.enchant.station.AlchemistMath.DustFusion;
import gg.ascent.plugin.item.LoreRenderer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** {@code /alchemist} and the fusion itself (PRD E3-S8). */
public final class AlchemistCommand extends StationListener<AlchemistMenu>
    implements CommandExecutor, TabCompleter {

  private final EnchantService enchants;
  private final ConfigService config;
  private final ItemRegistry items;
  private final Messages messages;

  public AlchemistCommand(
      Plugin plugin,
      EnchantService enchants,
      ConfigService config,
      ItemRegistry items,
      Messages messages) {
    super(plugin, AlchemistMenu.class);
    this.enchants = enchants;
    this.config = config;
    this.items = items;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "alchemist.console");
      return true;
    }
    AlchemistMenu menu = new AlchemistMenu(messages);
    menu.refresh(enchants, config.enchants(), player.getLevel());
    player.openInventory(menu.getInventory());
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    return List.of();
  }

  @Override
  protected boolean isInputSlot(int slot) {
    return AlchemistMenu.isInputSlot(slot);
  }

  @Override
  protected boolean isConfirmSlot(int slot) {
    return slot == AlchemistMenu.CONFIRM_SLOT;
  }

  @Override
  protected Collection<Integer> inputSlots() {
    return AlchemistMenu.INPUT_SLOTS;
  }

  @Override
  protected void refresh(AlchemistMenu menu, Player player) {
    menu.refresh(enchants, config.enchants(), player.getLevel());
  }

  @Override
  protected void confirm(AlchemistMenu menu, Player player) {
    EnchantsSettings settings = config.enchants();
    menu.refresh(enchants, settings, player.getLevel());
    Optional<BookFusion> book = menu.book();
    Optional<DustFusion> dust = menu.dust();
    if (book.isEmpty() && dust.isEmpty()) {
      messages.send(player, "alchemist.mismatch");
      return;
    }
    int cost = menu.cost();
    var costTag = Placeholder.unparsed("cost", String.valueOf(cost));
    if (player.getLevel() < cost) {
      messages.send(
          player,
          "alchemist.too-poor",
          costTag,
          Placeholder.unparsed("levels", String.valueOf(player.getLevel())));
      player.playSound(
          Sound.sound(Key.key("block.note_block.bass"), Sound.Source.PLAYER, 1f, 0.8f));
      return;
    }
    UUID actor = player.getUniqueId();
    Inventory inv = menu.getInventory();
    ItemStack a = inv.getItem(AlchemistMenu.FIRST_SLOT);
    ItemStack b = inv.getItem(AlchemistMenu.SECOND_SLOT);
    if (a == null || b == null) {
      return;
    }
    ItemStack result;
    String name;
    if (book.isPresent()) {
      result = enchants.createOpenedBook(book.get().result(), actor, "alchemist");
      EnchantDefinition def = enchants.definition(book.get().result().enchantId()).orElseThrow();
      name = def.display() + " " + LoreRenderer.roman(book.get().result().level());
    } else {
      MagicDust d = dust.get().result();
      result = enchants.createMagicDust(d.tier(), d.percent(), 1, actor, "alchemist");
      name = BookLore.tierName(d.tier()) + " Magic Dust +" + d.percent() + "%";
    }
    UUID resultId = items.idOf(result).orElse(null);
    Map<String, Object> context =
        Map.of("what", "alchemist", "result", resultId == null ? "?" : resultId.toString());
    consume(inv, AlchemistMenu.FIRST_SLOT, a, actor, context);
    consume(inv, AlchemistMenu.SECOND_SLOT, b, actor, context);
    player.setLevel(player.getLevel() - cost);
    give(player, result);
    messages.send(player, "alchemist.combined", Placeholder.unparsed("result", name), costTag);
    player.playSound(
        Sound.sound(Key.key("block.brewing_stand.brew"), Sound.Source.PLAYER, 1f, 1.2f));
    menu.refresh(enchants, settings, player.getLevel());
  }

  private void consume(
      Inventory inv, int slot, ItemStack stack, UUID actor, Map<String, Object> context) {
    items.idOf(stack).ifPresent(id -> items.record(id, ItemEvent.CONSUMED, actor, null, context));
    ItemStack rest = stack.clone();
    rest.setAmount(stack.getAmount() - 1);
    inv.setItem(slot, rest.getAmount() <= 0 ? null : rest);
  }
}
