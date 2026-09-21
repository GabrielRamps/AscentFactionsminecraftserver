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
| Spark | Profiling, `/spark tps`, `/spark profiler` | ci.lucko.me, its own build server; Modrinth carries only its mod-loader builds |
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
run, and `scripts/configure-ocm.sh` edits that generated file in place. It does
not paste a config from elsewhere, because OCM renames module keys between
releases and a stale key silently does nothing; if OCM moves a setting, the
script fails and names it.

What the checklist requires, and where each item comes from in OCM 2.6:

- [x] Attack cooldown disabled: `disable-attack-cooldown`, in the default `old` modeset
- [x] No sweep attack: `disable-sword-sweep`, in `old`
- [x] Old armour strength: `old-armour-strength`, in `old`
- [x] Old golden apples: `old-golden-apples`, in `old`
- [x] Old health regeneration: `old-player-regen`, in `old`
- [x] Sword blocking: `sword-blocking`, in `old`
- [x] Old knockback: `old-player-knockback`, in `old`
- [x] Crafting disabled for shield, elytra, trident, crossbow, netherite: `disable-crafting.denied`, **set by the script** (the default denies only shields)
- [x] Players cannot switch to 1.9 combat: `worlds.__default__` restricted to `old`, **set by the script**
- [x] OCM does not auto-update mid-sprint: `update-checker.auto-update: false`, **set by the script**
- [x] No offhand slot, as in 1.8: `disable-offhand` moved to `always_enabled_modules`, **set by the script**. Owner's decision, beyond the PRD checklist; sword blocking is unaffected.

Worth trying during the Sprint 4 combat validation, not now: the `attack-range`
module applies 1.8-style hit detection (smaller hitbox margin, shorter reach).
It ships disabled, is Paper 1.21.11+ only, and its comment warns of a small
cost with rapid hotbar swapping. It is the closest OCM gets to 1.8 hit-reg, so
it belongs in the session with the 1.8 veterans.

Then verify in game, not just in the config:

1. Join with a 1.21 client, then disconnect and join with a 1.8.9 client. One
   account connects once, so test them in turn. The 1.8.9 client connecting at
   all is the ViaVersion chain working.
2. With each client, hit a mob. Hits register, with no cooldown delay and no
   sweep attack.
3. Right-click with a sword and confirm it blocks.
4. Try to craft a shield and confirm it is denied.

Cross-version PvP (a 1.8 player hitting a 1.21 player) needs two accounts; it
is validated in Sprint 4 with 1.8 veterans, per PRD §10.4.

Record the result in `TESTING.md` before closing story E0-S3.
