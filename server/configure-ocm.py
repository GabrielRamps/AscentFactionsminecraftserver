#!/usr/bin/env python3
"""Applies the PRD E0-S3 combat settings to OldCombatMechanics' config.yml.

Run after the server has booted once with OCM installed (that boot writes the default config).
Edits are line-based so OCM's comments and config-version survive. Idempotent. Fails loudly if
a pattern is not found, which means OCM changed its config layout and this script needs a look.

What it does:
  - locks every world to the "old" modeset (attack cooldown off, 1.8 armour, gapples, regen,
    sword blocking, knockback, potions, crits)
  - extends the crafting denylist: shield, elytra, trident, crossbow, all netherite items
  - disables OCM's auto-updater (we pin plugin versions with install-plugins.py)

Usage: server/configure-ocm.py [--server-dir DIR]
"""

import argparse
import os
import re
import sys
from pathlib import Path

DENIED_ITEMS = [
    "shield",
    "elytra",
    "trident",
    "crossbow",
    "netherite_ingot",
    "netherite_sword",
    "netherite_pickaxe",
    "netherite_axe",
    "netherite_shovel",
    "netherite_hoe",
    "netherite_helmet",
    "netherite_chestplate",
    "netherite_leggings",
    "netherite_boots",
]
DENIED_BLOCK = "  denied:\n" + "".join(f"    - {item}\n" for item in DENIED_ITEMS)

# (label, already-configured pattern, unconfigured pattern, replacement)
RULES = [
    (
        "worlds.__default__",
        r'^[ \t]*__default__:[ \t]*\[ "old" \][ \t]*$',
        r'^([ \t]*__default__:[ \t]*)\[.*\][ \t]*$',
        r'\1[ "old" ]',
    ),
    (
        "disable-crafting.denied",
        re.escape(DENIED_BLOCK),
        r'^  denied:\n(?:    - .*\n)+',
        DENIED_BLOCK,
    ),
    (
        "update-checker.auto-update",
        r'^[ \t]*auto-update:[ \t]*false[ \t]*$',
        r'^([ \t]*auto-update:[ \t]*)true[ \t]*$',
        r'\1false',
    ),
]


def apply_rules(text: str) -> str:
    for label, done_pattern, pattern, replacement in RULES:
        if re.search(done_pattern, text, flags=re.M):
            continue
        text, n = re.subn(pattern, replacement, text, count=1, flags=re.M)
        if n != 1:
            sys.exit(f"error: could not find {label} in config.yml (pattern: {pattern!r})")
    return text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server-dir", default=os.environ.get("ASCENT_SERVER_DIR", str(Path.home() / "ascent-server")))
    args = ap.parse_args()

    cfg = Path(args.server_dir) / "plugins" / "OldCombatMechanics" / "config.yml"
    if not cfg.exists():
        sys.exit(f"error: {cfg} not found. Start the server once with OCM installed, then rerun.")
    original = cfg.read_text()
    text = apply_rules(original)

    if text == original:
        print(f"{cfg}: already configured")
        return
    cfg.with_suffix(".yml.bak").write_text(original)
    cfg.write_text(text)
    print(f"{cfg}: updated (backup at config.yml.bak). Restart the server or run /ocm reload.")


if __name__ == "__main__":
    main()
