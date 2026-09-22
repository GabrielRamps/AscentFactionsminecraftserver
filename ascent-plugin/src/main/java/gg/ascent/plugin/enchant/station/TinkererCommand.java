package gg.ascent.plugin.enchant.station;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.config.EnchantsSettings;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.item.ItemEvent;
import gg.ascent.api.item.ItemRegistry;
import gg.ascent.api.message.Messages;
import gg.ascent.plugin.enchant.station.TinkererMath.Offer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** {@code /tinkerer} and the trade itself (PRD E3-S7). */
public final class TinkererCommand extends StationListener<TinkererMenu>
    implements CommandExecutor, TabCompleter {

  private final EnchantService enchants;
  private final ConfigService config;
  private final ItemRegistry items;
  private final Messages messages;
  private final RandomGenerator random;
  private final TinkererValuer valuer;

  public TinkererCommand(
      Plugin plugin,
      EnchantService enchants,
      ConfigService config,
      ItemRegistry items,
      Messages messages,
      RandomGenerator random) {
    super(plugin, TinkererMenu.class);
    this.enchants = enchants;
    this.config = config;
    this.items = items;
    this.messages = messages;
    this.random = random;
    this.valuer = new TinkererValuer(enchants, config);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "tinkerer.console");
      return true;
    }
    player.openInventory(new TinkererMenu(messages).getInventory());
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    return List.of();
  }

  @Override
  protected boolean isInputSlot(int slot) {
    return TinkererMenu.isOfferSlot(slot);
  }

  @Override
  protected boolean isConfirmSlot(int slot) {
    return slot == TinkererMenu.CONFIRM_SLOT;
  }

  @Override
  protected Collection<Integer> inputSlots() {
    return TinkererMenu.offerSlots();
  }

  @Override
  protected void refresh(TinkererMenu menu, Player player) {
    menu.refresh(valuer, config.enchants());
  }

  @Override
  protected void confirm(TinkererMenu menu, Player player) {
    EnchantsSettings settings = config.enchants();
    menu.refresh(valuer, settings);
    Map<Integer, ItemStack> offered = menu.offered();
    if (offered.isEmpty()) {
      return;
    }
    if (!menu.ready()) {
      messages.send(player, "tinkerer.rejected");
      return;
    }
    UUID actor = player.getUniqueId();
    Map<Integer, Integer> bottles = new LinkedHashMap<>(); // levels each -> count
    List<ItemStack> dust = new ArrayList<>();
    int levels = 0;
    for (Map.Entry<Integer, ItemStack> e : offered.entrySet()) {
      ItemStack item = e.getValue();
      Optional<Offer> priced = valuer.value(item);
      if (priced.isEmpty()) {
        continue; // changed under us; leave it in the pane
      }
      Offer offer = priced.get();
      levels += offer.totalLevels();
      bottles.merge(offer.levelsEach(), offer.amount(), Integer::sum);
      if (offer.dustTier() != null) {
        for (int i = 0; i < offer.amount(); i++) {
          OptionalInt percent = TinkererMath.rollDust(settings, offer.dustTier(), random);
          if (percent.isPresent()) {
            dust.add(
                enchants.createMagicDust(
                    offer.dustTier(), percent.getAsInt(), 1, actor, "tinkerer"));
          }
        }
      }
      int total = offer.totalLevels();
      items
          .idOf(item)
          .ifPresent(
              id ->
                  items.record(
                      id,
                      ItemEvent.CONSUMED,
                      actor,
                      null,
                      Map.of("what", "tinkerer", "levels", total, "amount", item.getAmount())));
      menu.getInventory().setItem(e.getKey(), null);
    }
    for (Map.Entry<Integer, Integer> b : bottles.entrySet()) {
      int left = b.getValue();
      while (left > 0) {
        int n = Math.min(64, left);
        give(player, enchants.createXpBottle(b.getKey(), n, actor, "tinkerer"));
        left -= n;
      }
    }
    for (ItemStack d : dust) {
      give(player, d);
    }
    Component dustNote =
        dust.isEmpty()
            ? Component.empty()
            : messages.render(
                "tinkerer.traded-dust", Placeholder.unparsed("dust", String.valueOf(dust.size())));
    messages.send(
        player,
        "tinkerer.traded",
        Placeholder.unparsed("items", String.valueOf(offered.size())),
        Placeholder.unparsed("levels", String.valueOf(levels)),
        Placeholder.component("dust", dustNote));
    player.playSound(Sound.sound(Key.key("block.anvil.use"), Sound.Source.PLAYER, 0.7f, 1.4f));
    menu.refresh(valuer, settings);
  }
}
