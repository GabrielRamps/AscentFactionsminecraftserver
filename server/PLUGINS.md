# Third-party plugins (story E0-S3)

Ascent builds on a small set of third-party plugins for cross-cutting concerns.
Everything that *is* the game — factions, enchants, rank, mines, spawners,
contracts, events — is ours and lives in `ascent-plugin`.

`scripts/install-plugins.sh` installs all of these, matching them against the
Minecraft version pinned in `gradle.properties`. `scripts/bootstrap.sh` runs it
for you. The table below is the fallback: if the installer warns that it could
not resolve a plugin, download that one by hand from its project home, drop the
jar in `$ASCENT_SERVER_DIR/plugins/`, and restart.

| Plugin | Purpose | Source |
|---|---|---|
| LuckPerms | Permissions and groups (PRD §6.5) | luckperms.net |
| Vault | Economy bridge for third-party plugins | github.com/MilkBowl/Vault |
| PlaceholderAPI | Placeholders for scoreboard, tab and chat | Hangar (extendedclip) |
| OldCombatMechanics | 1.8-style combat on a modern server | github.com/kernitus/BukkitOldCombatMechanics |
| ViaVersion | Lets newer clients join | Hangar (ViaVersion) |
| ViaBackwards | Lets older clients join, back to 1.9 | Hangar (ViaVersion) |
| ViaRewind | Extends that support back to 1.8.x | Hangar (ViaVersion) |
| GrimAC | Anti-cheat | github.com/GrimAnticheat/Grim |
| Spark | Profiling, `/spark tps`, `/spark profiler` | spark.lucko.me |
| FastAsyncWorldEdit | World edits and mine resets (Epic 4) | Modrinth / github.com/IntellectualSites |
| WorldGuard | Spawn and warzone regions only | enginehub.org |

Notes that bite people:

- **WorldGuard needs WorldEdit.** FastAsyncWorldEdit is a WorldEdit fork and
  satisfies that dependency. Do not install both FAWE and stock WorldEdit.
- **Vault does nothing on its own.** It stays inert until story E1-S4 registers
  Ascent's economy as the Vault provider.
- **Via order matters.** ViaRewind requires ViaBackwards, which requires
  ViaVersion. Install all three or none.
- **Match the server version.** Every jar here must support the Paper line
  pinned in `gradle.properties`. Check before upgrading Paper, not after.

## OldCombatMechanics configuration

The PRD requires 1.8 combat feel. OCM generates its own `config.yml` on first
run; **edit that generated file** rather than pasting one from elsewhere, because
OCM renames module keys between releases and a stale key silently does nothing.

After the first boot, open `plugins/OldCombatMechanics/config.yml` and enable:

- [ ] Attack cooldown disabled (no 1.9 attack-speed sweep)
- [ ] Old armour strength (1.8 armour damage-reduction formula)
- [ ] Old golden apples (notch apples craftable and 1.8 effects)
- [ ] Old health regeneration (1.8 regen rate and exhaustion)
- [ ] Sword blocking (right-click sword blocks, replacing shields)
- [ ] Old knockback (1.8 knockback values)
- [ ] Crafting disabled for: shield, elytra, trident, crossbow, and all netherite items

Then verify in game, not just in the config:

1. Join with a 1.8.9 client and a 1.21 client at the same time.
2. Hit a mob and a player with each. Hits register on both, with no cooldown
   delay and no sweep attack.
3. Right-click with a sword and confirm it blocks.
4. Try to craft a shield and confirm it is denied.

Record the result in `TESTING.md` before closing story E0-S3.
