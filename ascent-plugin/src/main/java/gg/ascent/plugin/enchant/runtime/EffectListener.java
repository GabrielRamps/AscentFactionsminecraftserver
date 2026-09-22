package gg.ascent.plugin.enchant.runtime;

import gg.ascent.api.enchant.EffectType;
import gg.ascent.api.item.ItemRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/** The Bukkit hooks of the effect runtime (PRD E3-S4). */
public final class EffectListener implements Listener {

  private final FighterViews views;
  private final CombatResolver resolver;
  private final EffectApplier applier;
  private final Cooldowns cooldowns;
  private final Silences silences;
  private final SafeZones safeZones;
  private final ItemRegistry items;
  private final RandomGenerator random;

  public EffectListener(
      FighterViews views,
      CombatResolver resolver,
      EffectApplier applier,
      Cooldowns cooldowns,
      Silences silences,
      SafeZones safeZones,
      ItemRegistry items,
      RandomGenerator random) {
    this.views = views;
    this.resolver = resolver;
    this.applier = applier;
    this.cooldowns = cooldowns;
    this.silences = silences;
    this.safeZones = safeZones;
    this.items = items;
    this.random = random;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onHit(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    LivingEntity attacker = attackerOf(event.getDamager());
    if (attacker == null || (!(attacker instanceof Player) && !(victim instanceof Player))) {
      return;
    }
    if (safeZones.isSafezone(attacker.getLocation())
        || safeZones.isSafezone(victim.getLocation())) {
      return;
    }
    long now = Bukkit.getCurrentTick();
    boolean projectile = event.getDamager() instanceof Projectile;
    HitOutcome outcome =
        resolver.resolve(
            views.of(attacker, now), views.of(victim, now), event.getDamage(), projectile, now);
    event.setDamage(outcome.damage());
    applier.apply(outcome.procs(), attacker, victim, now);
  }

  private static LivingEntity attackerOf(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }
    if (damager instanceof Projectile projectile) {
      ProjectileSource source = projectile.getShooter();
      return source instanceof LivingEntity living ? living : null;
    }
    return null;
  }

  @EventHandler(ignoreCancelled = true)
  public void onKill(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null || safeZones.isSafezone(killer.getLocation())) {
      return;
    }
    double hearts = ToolEffects.healOnKill(views.gear(killer.getInventory().getItemInMainHand()));
    if (hearts > 0) {
      applier.apply(
          List.of(new Proc.Heal(Proc.Side.ATTACKER, hearts, "heal_on_kill")),
          killer,
          killer,
          Bukkit.getCurrentTick());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onDurability(PlayerItemDamageEvent event) {
    if (ToolEffects.saveDurability(views.gear(event.getItem()), random)) {
      event.setCancelled(true);
    }
  }

  /** After the mines listener has had its say (HIGH), so a refused break gets no drops either. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack tool = player.getInventory().getItemInMainHand();
    List<GearEnchant> gear = views.gear(tool);
    boolean smelt = ToolEffects.has(gear, EffectType.AUTO_SMELT);
    boolean telepathy = ToolEffects.has(gear, EffectType.TELEPATHY);
    if (!smelt && !telepathy) {
      return;
    }
    Collection<ItemStack> drops = event.getBlock().getDrops(tool, player);
    List<ItemStack> out = new ArrayList<>();
    for (ItemStack drop : drops) {
      if (smelt) {
        Material smelted = Material.matchMaterial(ToolEffects.smelt(drop.getType().name()));
        if (smelted != null && smelted != drop.getType()) {
          drop = new ItemStack(smelted, drop.getAmount());
        }
      }
      out.add(drop);
    }
    event.setDropItems(false);
    for (ItemStack drop : out) {
      if (telepathy) {
        for (ItemStack rest : player.getInventory().addItem(drop).values()) {
          player
              .getWorld()
              .dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), rest);
        }
      } else {
        player
            .getWorld()
            .dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop);
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onArrow(ProjectileHitEvent event) {
    if (!(event.getEntity().getShooter() instanceof Player shooter)) {
      return;
    }
    if (safeZones.isSafezone(event.getEntity().getLocation())) {
      return;
    }
    Optional<Double> damage =
        ToolEffects.lightning(views.gear(shooter.getInventory().getItemInMainHand()), random);
    if (damage.isEmpty()) {
      return;
    }
    var at =
        event.getHitEntity() != null
            ? event.getHitEntity().getLocation()
            : event.getEntity().getLocation();
    at.getWorld().strikeLightningEffect(at);
    if (event.getHitEntity() instanceof LivingEntity hit && hit != shooter) {
      hit.damage(damage.get() * 2.0, shooter);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    cooldowns.forget(event.getPlayer().getUniqueId());
    silences.forget(event.getPlayer().getUniqueId());
  }

  /** For the debug command. */
  public ItemRegistry items() {
    return items;
  }
}
