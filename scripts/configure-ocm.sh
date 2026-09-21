#!/usr/bin/env bash
# Applies the PRD's 1.8-combat settings (story E0-S3) to OldCombatMechanics.
#
#   scripts/configure-ocm.sh
#
# Run after the server has booted at least once, because OCM writes its config
# on first start. Idempotent: re-running changes nothing. Edits the generated
# file in place rather than replacing it, because OCM renames keys between
# releases and a pasted config silently stops applying. If OCM's layout
# changes, this fails loudly and names the setting it could not find.
#
# Most of the checklist is already satisfied by OCM's default "old" modeset:
# attack cooldown off, no sweep, 1.8 armour, old golden apples, old regen,
# sword blocking, 1.8 knockback. Only the deltas below are needed.

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
need python3

CONFIG="$SERVER_DIR/plugins/OldCombatMechanics/config.yml"
[ -f "$CONFIG" ] || die "OldCombatMechanics config not found: $CONFIG
Start the server once (./dev.sh) so the plugin writes it, then re-run this."

# Keep the untouched original next to it, once.
[ -f "$CONFIG.orig" ] || cp "$CONFIG" "$CONFIG.orig"

python3 - "$CONFIG" <<'PY'
import sys, pathlib

path = pathlib.Path(sys.argv[1])
text = path.read_text()
changed = []

def edit(name, old, new):
    global text
    if new in text:
        return
    if old not in text:
        sys.exit(
            f"error: could not find the {name} setting in {path}.\n"
            "OldCombatMechanics may have changed its config layout. Compare the "
            "file against scripts/configure-ocm.sh and update the script."
        )
    text = text.replace(old, new, 1)
    changed.append(name)

# PRD E0-S3: crafting denied for shields, elytra, tridents, crossbows and
# netherite. Netherite gear is made at a smithing table, not a crafting table,
# so deny the ingot as well: with no ingots there is nothing to smith.
edit(
    "disable-crafting deny list",
    "  denied:\n    - shield\n",
    "  denied:\n"
    "    - shield\n"
    "    - elytra\n"
    "    - trident\n"
    "    - crossbow\n"
    "    - netherite_ingot\n"
    "    - netherite_sword\n"
    "    - netherite_pickaxe\n"
    "    - netherite_axe\n"
    "    - netherite_shovel\n"
    "    - netherite_hoe\n"
    "    - netherite_helmet\n"
    "    - netherite_chestplate\n"
    "    - netherite_leggings\n"
    "    - netherite_boots\n",
)

# 1.8 combat is a design decision, not a player choice. Removing "new" from the
# allowed modesets stops anyone switching to cooldown-and-sweep combat with
# /ocm mode, which would make every fight depend on which mode each side chose.
edit(
    "allowed modesets",
    '  __default__: [ "old", "new" ]\n',
    '  __default__: [ "old" ]\n',
)

# PRD §10.4: pin builds and upgrade at sprint boundaries. OCM must not replace
# itself underneath a sprint.
edit(
    "update-checker auto-update",
    "  auto-update: true\n",
    "  auto-update: false\n",
)

path.write_text(text)
print("Applied: " + ", ".join(changed) if changed else "Already applied; nothing to change.")
PY

log "OldCombatMechanics configured. Restart the server (./dev.sh) to load it."
