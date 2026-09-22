package gg.ascent.plugin.economy;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.economy.EconomyService;
import gg.ascent.api.economy.SellMultiplierService;
import gg.ascent.api.economy.TxReason;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import gg.ascent.api.progress.ObjectiveType;
import gg.ascent.api.progress.ProgressBus;
import gg.ascent.plugin.economy.SellCalculator.Line;
import gg.ascent.plugin.economy.SellCalculator.Receipt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * {@code /sell}: every sellable item in the main inventory goes for its {@code prices.yml} value
 * (PRD E4-S2). Registry-tagged items are never sold here: they are gear, not ore.
 */
public final class SellCommand implements CommandExecutor, TabCompleter {

  private final EconomyService economy;
  private final SellMultiplierService multiplier;
  private final ItemRegistry items;
  private final ProgressBus progress;
  private final ConfigService config;
  private final Messages messages;

  public SellCommand(
      EconomyService economy,
      SellMultiplierService multiplier,
      ItemRegistry items,
      ProgressBus progress,
      ConfigService config,
      Messages messages) {
    this.economy = economy;
    this.multiplier = multiplier;
    this.items = items;
    this.progress = progress;
    this.config = config;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "sell.console");
      return true;
    }
    PlayerInventory inventory = player.getInventory();
    Map<String, Integer> counts = new LinkedHashMap<>();
    ItemStack[] storage = inventory.getStorageContents();
    for (ItemStack stack : storage) {
      if (stack == null || stack.getType().isAir() || items.idOf(stack).isPresent()) {
        continue;
      }
      String material = stack.getType().name();
      if (config.prices().sellPrice(material).isPresent() && !stack.hasItemMeta()) {
        counts.merge(material, stack.getAmount(), Integer::sum);
      }
    }
    Receipt receipt =
        SellCalculator.price(counts, config.prices(), multiplier.multiplier(player.getUniqueId()));
    if (receipt.isEmpty()) {
      messages.send(player, "sell.nothing");
      return true;
    }
    for (int i = 0; i < storage.length; i++) {
      ItemStack stack = storage[i];
      if (stack != null
          && !stack.getType().isAir()
          && !stack.hasItemMeta()
          && counts.containsKey(stack.getType().name())) {
        storage[i] = null;
      }
    }
    inventory.setStorageContents(storage);
    economy.deposit(player.getUniqueId(), receipt.total(), TxReason.SELL, "sell");
    progress.publish(
        player.getUniqueId(),
        ObjectiveType.SELL_VALUE,
        (int) Math.min(Integer.MAX_VALUE, receipt.total()),
        null);
    messages.send(player, "sell.header");
    for (Line line : receipt.lines()) {
      messages.send(
          player,
          "sell.line",
          Placeholder.unparsed("count", String.valueOf(line.count())),
          Placeholder.unparsed("item", pretty(line.material())),
          Placeholder.unparsed("total", Money.format(line.total())));
    }
    messages.send(
        player,
        "sell.total",
        Placeholder.unparsed("total", Money.format(receipt.total())),
        Placeholder.unparsed("balance", Money.format(economy.getBalance(player.getUniqueId()))));
    return true;
  }

  static String pretty(String material) {
    StringBuilder out = new StringBuilder();
    for (String word : material.toLowerCase(java.util.Locale.ROOT).split("_")) {
      if (word.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return out.toString();
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    return List.of();
  }
}
