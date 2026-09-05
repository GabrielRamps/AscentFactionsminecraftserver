#!/usr/bin/env python3
"""Installs the third-party plugins from PRD E0-S3 into the server's plugins folder.

Modrinth-hosted plugins are resolved for the pinned Paper version (gradle.properties:
paperVersion). OldCombatMechanics and Vault come from their GitHub releases. Existing jars for
the same plugin are replaced. Run again any time to update.

spark is not downloaded: Paper bundles it since 1.21.

Usage: server/install-plugins.py [--server-dir DIR]
"""

import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

UA = "ascent-install-plugins/0.1 (github.com/ascent)"

# slug -> (display name, loaders to accept, filename prefix used to remove old copies)
MODRINTH = {
    "luckperms": ("LuckPerms", ["paper", "bukkit"], "LuckPerms"),
    "placeholderapi": ("PlaceholderAPI", ["paper", "bukkit"], "PlaceholderAPI"),
    "viaversion": ("ViaVersion", ["paper", "bukkit"], "ViaVersion"),
    "viabackwards": ("ViaBackwards", ["paper", "bukkit"], "ViaBackwards"),
    "viarewind": ("ViaRewind", ["paper", "bukkit"], "ViaRewind"),
    "grimac": ("GrimAC", ["paper", "bukkit"], "grimac"),
    # spark is bundled with Paper since 1.21; no download needed.
    "fastasyncworldedit": ("FastAsyncWorldEdit", ["paper", "bukkit"], "FastAsyncWorldEdit"),
    "worldguard": ("WorldGuard", ["paper", "bukkit"], "worldguard"),
}

GITHUB = {
    "kernitus/BukkitOldCombatMechanics": ("OldCombatMechanics", "OldCombatMechanics"),
    "MilkBowl/Vault": ("Vault", "Vault"),
}


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def download(url, dest: Path):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(req, timeout=600) as r, open(tmp, "wb") as f:
        while chunk := r.read(1 << 16):
            f.write(chunk)
    tmp.replace(dest)


def remove_old(plugins: Path, prefix: str, keep: str):
    for p in plugins.glob("*.jar"):
        if p.name.lower().startswith(prefix.lower()) and p.name != keep:
            print(f"    removing old {p.name}")
            p.unlink()


def modrinth_latest(slug, loaders, game_version):
    q = urllib.parse.quote(json.dumps(loaders))
    gv = urllib.parse.quote(json.dumps([game_version]))
    versions = get_json(f"https://api.modrinth.com/v2/project/{slug}/version?loaders={q}&game_versions={gv}")
    if not versions:
        raise RuntimeError(f"no {slug} version for {game_version} with loaders {loaders}")
    v = versions[0]
    files = [f for f in v["files"] if f.get("primary")] or v["files"]
    return v["version_number"], files[0]["url"], files[0]["filename"]


def github_latest_jar(repo):
    rel = get_json(f"https://api.github.com/repos/{repo}/releases/latest")
    jars = [a for a in rel["assets"] if a["name"].endswith(".jar")]
    if not jars:
        raise RuntimeError(f"no jar asset in latest release of {repo}")
    # Prefer the plain jar over -sources/-javadoc variants.
    jars.sort(key=lambda a: ("sources" in a["name"] or "javadoc" in a["name"], len(a["name"])))
    a = jars[0]
    return rel["tag_name"], a["browser_download_url"], a["name"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server-dir", default=os.environ.get("ASCENT_SERVER_DIR", str(Path.home() / "ascent-server")))
    args = ap.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    props = (repo_root / "gradle.properties").read_text()
    m = re.search(r"^paperVersion=(.+)$", props, re.M)
    if not m:
        sys.exit("paperVersion not found in gradle.properties")
    game_version = m.group(1).strip()

    plugins = Path(args.server_dir) / "plugins"
    plugins.mkdir(parents=True, exist_ok=True)
    print(f"Installing plugins for Paper {game_version} into {plugins}\n")

    failures = []
    for slug, (name, loaders, prefix) in MODRINTH.items():
        try:
            ver, url, filename = modrinth_latest(slug, loaders, game_version)
            print(f"  {name:20} {ver:24} {filename}")
            download(url, plugins / filename)
            remove_old(plugins, prefix, filename)
        except Exception as e:  # noqa: BLE001
            print(f"  {name:20} FAILED: {e}")
            failures.append(name)

    for repo, (name, prefix) in GITHUB.items():
        try:
            ver, url, filename = github_latest_jar(repo)
            print(f"  {name:20} {ver:24} {filename}")
            download(url, plugins / filename)
            remove_old(plugins, prefix, filename)
        except Exception as e:  # noqa: BLE001
            print(f"  {name:20} FAILED: {e}")
            failures.append(name)

    print()
    if failures:
        print("Failed: " + ", ".join(failures))
        sys.exit(1)
    print("All plugins installed. Start the server once to generate their configs, then run")
    print("server/configure-ocm.py to apply the 1.8 combat settings.")


if __name__ == "__main__":
    main()
