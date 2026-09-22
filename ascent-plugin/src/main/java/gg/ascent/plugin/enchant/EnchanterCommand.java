package gg.ascent.plugin.enchant;

import gg.ascent.api.config.ConfigService;
import gg.ascent.api.enchant.EnchantService;
import gg.ascent.api.enchant.Tier;
import gg.ascent.api.message.Messages;
import gg.ascent.api.player.PlayerService;
import gg.ascent.api.rank.UnlockService;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/** {@code /enchanter} and the clicks inside its menu (PRD E3-S6). */
public final class EnchanterCommand implements CommandExecutor, TabCompleter, Listener {

  private final EnchantService enchants;
  private final PlayerService players;
  private final UnlockService unlocks;
  private final ConfigService config;
  private final Messages messages;

  public EnchanterCommand(
      EnchantService enchants,
      PlayerService players,
      UnlockService unlocks,
      ConfigService config,
      Messages messages) {
    this.enchants = enchants;
    this.players = players;
    this.unlocks = unlocks;
    this.config = config;
    this.messages = messages;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      messages.send(sender, "enchanter.console");
      return true;
    }
    open(player);
    return true;
  }

  private void open(Player player) {
    int rank = players.require(player.getUniqueId()).rank();
    player.openInventory(
        EnchanterMenu.build(player, rank, config.enchants(), unlocks, messages).getInventory());
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof EnchanterMenu menu)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || event.getClickedInventory() == null
        || !(event.getClickedInventory().getHolder() instanceof EnchanterMenu)) {
      return;
    }
    Tier tier = menu.tierAt(event.getSlot());
    if (tier == null) {
      return;
    }
    int count = EnchanterMath.countFor(event.getClick());
    if (count == 0) {
      return;
    }
    int cost = EnchanterMath.cost(config.enchants().tier(tier).xpLevels(), count);
    var tags =
        new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] {
          Placeholder.unparsed("count", String.valueOf(count)),
          Placeholder.unparsed("tier", BookLore.tierName(tier)),
          Placeholder.unparsed("cost", String.valueOf(cost)),
          Placeholder.unparsed("levels", String.valueOf(player.getLevel()))
        };
    if (player.getLevel() < cost) {
      messages.send(player, "enchanter.too-poor", tags);
      player.playSound(
          Sound.sound(Key.key("block.note_block.bass"), Sound.Source.PLAYER, 1f, 0.8f));
      return;
    }
    player.setLevel(player.getLevel() - cost);
    ItemStack books = enchants.createUnopenedBook(tier, count, player.getUniqueId(), "enchanter");
    for (ItemStack rest : player.getInventory().addItem(books).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }
    messages.send(player, "enchanter.bought", tags);
    player.playSound(
        Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 1f, 0.8f));
    open(player); // refresh the level shown in the lore
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof EnchanterMenu) {
      event.setCancelled(true);
    }
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    return List.of();
  }

  /** The buying rules, without Bukkit. */
  public static final class EnchanterMath {
    private EnchanterMath() {}

    /** Left 1, right 8, any shift-click 16, anything else 0 (PRD E3-S6). */
    public static int countFor(ClickType click) {
      return switch (click) {
        case LEFT -> 1;
        case RIGHT -> 8;
        case SHIFT_LEFT, SHIFT_RIGHT -> 16;
        default -> 0;
      };
    }

    /** No stack discount. */
    public static int cost(int perBook, int count) {
      return perBook * count;
    }
  }
}
