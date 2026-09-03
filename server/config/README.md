# Third-party plugin configuration (E0-S3)

Files placed in this directory are copied over `$SERVER_DIR/plugins/` by `server/bootstrap.sh`.
Keep only deliberate overrides here; let each plugin generate its own defaults first.

## OldCombatMechanics

Start the server once so OCM writes `plugins/OldCombatMechanics/config.yml`, then set the
following in that file. OCM groups modules into *modesets*; make sure every module below is
enabled and part of the `old` modeset, and that `old` is the default modeset for all worlds.

| Module key                      | Setting                                                                                  |
|---------------------------------|------------------------------------------------------------------------------------------|
| `disable-attack-cooldown`       | enabled                                                                                  |
| `old-armour-strength`           | enabled                                                                                  |
| `old-golden-apples`             | enabled (old regen values; god apple cooldown is set by our plugin in E9-S2)             |
| `old-player-regen`              | enabled                                                                                  |
| `sword-blocking`                | enabled                                                                                  |
| `old-player-knockback`          | enabled                                                                                  |
| `old-tool-damage`               | enabled                                                                                  |
| `old-critical-hits`             | enabled                                                                                  |
| `disable-sweep`                 | enabled                                                                                  |
| `disable-offhand`               | enabled                                                                                  |
| `disable-crafting`              | enabled; `denied` list: `SHIELD`, `ELYTRA`, `TRIDENT`, `CROSSBOW`, every `NETHERITE_*` item |
| `disable-enderpearl-cooldown`   | enabled (our plugin applies the 16s cooldown in E9-S2)                                   |

Then `/ocm reload` or restart, and verify with a 1.8.9 client and a 1.21 client that hits register
on both and that the attack-cooldown indicator is gone on the 1.21 client.

## ViaVersion / ViaBackwards / ViaRewind

Defaults are fine. ViaRewind's `config.yml` can leave `replace-adventure`/`emulate-*` at defaults.

## WorldGuard

Only used for `spawn` and `warzone` regions (E8-S1). Nothing to change until that story.

## GrimAC

Defaults until E10-S1, where alert routing to the staff channel is configured.

## LuckPerms

After first boot, from the console:

```
lp creategroup admin
lp group admin permission set ascent.admin true
lp user <you> parent add admin
```

The full group ladder (`default`, `helper`, `mod`, `admin`, `owner`) is set up in E1-S6.
