#!/usr/bin/env bash
# Installs the third-party plugins from story E0-S3 into $SERVER_DIR/plugins.
#
#   scripts/install-plugins.sh
#
# Most come from Modrinth, matched against the Minecraft version pinned in
# gradle.properties. Vault and OldCombatMechanics come from their GitHub
# releases because they are not reliably published on Modrinth.
#
# Safe to re-run: each jar is replaced with the current latest. A plugin that
# cannot be resolved produces a warning and is skipped, never a hard failure --
# install that one by hand from server/PLUGINS.md and re-run.

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

need curl
need jq

PLUGINS_DIR="$SERVER_DIR/plugins"
mkdir -p "$PLUGINS_DIR"

[ -n "$MC_VERSION" ] || die "paperVersion is not set in gradle.properties"
log "Installing plugins for Minecraft $MC_VERSION into $PLUGINS_DIR"

MODRINTH_SLUGS=(
  luckperms
  placeholderapi
  viaversion
  viabackwards
  viarewind
  grimac
  spark
  fastasyncworldedit
  worldguard
)

urlencode() { jq -rn --arg v "$1" '$v|@uri'; }

# Newest Paper/Spigot/Bukkit build of a Modrinth project, optionally restricted
# to MC_VERSION. Prints "url<TAB>filename<TAB>version".
modrinth_latest() {
  local slug="$1" pin_version="$2" query
  query="loaders=$(urlencode '["paper","spigot","bukkit"]')"
  [ "$pin_version" = "1" ] && query+="&game_versions=$(urlencode "[\"$MC_VERSION\"]")"
  curl -fsSL -A "$USER_AGENT" "https://api.modrinth.com/v2/project/$slug/version?$query" |
    jq -r '.[0] | select(. != null)
             | (.files[] | select(.primary) | "\(.url)\t\(.filename)") + "\t" + .version_number'
}

install_modrinth() {
  local slug="$1" line url filename version prefix
  line="$(modrinth_latest "$slug" 1 || true)"
  if [ -z "$line" ]; then
    warn "$slug: no build lists $MC_VERSION; falling back to its newest Paper build"
    line="$(modrinth_latest "$slug" 0 || true)"
  fi
  if [ -z "$line" ]; then
    warn "$slug: not found on Modrinth. Install it by hand (see server/PLUGINS.md)."
    return 0
  fi
  IFS=$'\t' read -r url filename version <<<"$line"
  log "$slug $version -> $filename"
  # Drop older copies of the same plugin before writing the new one.
  prefix="${filename%%-*}"
  find "$PLUGINS_DIR" -maxdepth 1 -iname "${prefix}-*.jar" ! -name "$filename" -delete 2>/dev/null || true
  curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/$filename" "$url"
}

# Newest release asset of a GitHub repo whose name matches a regex.
install_github_latest() {
  local repo="$1" pattern="$2" json url name
  json="$(curl -fsSL -A "$USER_AGENT" "https://api.github.com/repos/$repo/releases/latest" || true)"
  if [ -z "$json" ]; then
    warn "$repo: could not reach the GitHub releases API. Install it by hand."
    return 1
  fi
  url="$(jq -r --arg re "$pattern" '.assets[]? | select(.name | test($re)) | .browser_download_url' <<<"$json" | head -1)"
  if [ -z "$url" ]; then
    warn "$repo: no release asset matching /$pattern/. Install it by hand."
    return 1
  fi
  name="$(basename "$url")"
  log "$repo $(jq -r '.tag_name // "?"' <<<"$json") -> $name"
  curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/$name" "$url"
}

for slug in "${MODRINTH_SLUGS[@]}"; do
  install_modrinth "$slug"
done

install_github_latest "kernitus/BukkitOldCombatMechanics" '^OldCombatMechanics.*\.jar$' ||
  warn "OldCombatMechanics is required for 1.8 combat; install it before testing combat feel."

# Vault publishes irregularly; fall back to the last known-good release.
if ! install_github_latest "MilkBowl/Vault" '^Vault.*\.jar$'; then
  log "Vault: falling back to the pinned 1.7.3 release"
  curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/Vault.jar" \
    "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" ||
    warn "Vault: download failed. Install it by hand."
fi

log "Plugins now installed:"
find "$PLUGINS_DIR" -maxdepth 1 -name '*.jar' -printf '    %f\n' | sort

cat <<'MSG'

Not installed automatically:
  Nothing, if every line above succeeded. Check the warnings if any appeared.

Next: start the server once so each plugin writes its default config, then
apply the OldCombatMechanics settings from server/PLUGINS.md.
MSG
