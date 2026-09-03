#!/usr/bin/env bash
# Installs the third-party plugins from E0-S3 into $SERVER_DIR/plugins using the Modrinth API
# (latest version compatible with mcVersion), plus Vault from its GitHub release.
# Safe to re-run: it overwrites each jar with the current latest.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need curl; need jq

PLUGINS_DIR="$SERVER_DIR/plugins"
mkdir -p "$PLUGINS_DIR"

# Modrinth project slugs. Order matters only for readability.
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

# Not (reliably) on Modrinth: pulled from GitHub releases instead.
VAULT_URL="https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar"
OCM_REPO="kernitus/BukkitOldCombatMechanics"

urlencode() { jq -rn --arg v "$1" '$v|@uri'; }

modrinth_latest() { # modrinth_latest <slug> <with_game_version:0|1> -> "url<TAB>filename<TAB>version"
  local slug="$1" filter_gv="$2" q
  q="loaders=$(urlencode '["paper","spigot","bukkit"]')"
  [[ "$filter_gv" == "1" ]] && q+="&game_versions=$(urlencode "[\"$MC_VERSION\"]")"
  curl -fsSL -A "$USER_AGENT" "https://api.modrinth.com/v2/project/$slug/version?$q" |
    jq -r '.[0] | select(. != null) | (.files[] | select(.primary) | "\(.url)\t\(.filename)") + "\t" + .version_number'
}

install_modrinth() {
  local slug="$1" line
  line="$(modrinth_latest "$slug" 1 || true)"
  if [[ -z "$line" ]]; then
    warn "$slug: no version lists $MC_VERSION explicitly; taking the newest paper/spigot build instead"
    line="$(modrinth_latest "$slug" 0 || true)"
  fi
  [[ -n "$line" ]] || { warn "$slug: nothing found on Modrinth, install it by hand"; return 0; }
  local url filename version
  IFS=$'\t' read -r url filename version <<<"$line"
  log "$slug $version -> $filename"
  # Remove older copies of the same plugin (name prefix up to the first '-') before writing.
  local prefix="${filename%%-*}"
  find "$PLUGINS_DIR" -maxdepth 1 -iname "${prefix}-*.jar" ! -name "$filename" -delete 2>/dev/null || true
  curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/$filename" "$url"
}

install_github_latest() { # install_github_latest <owner/repo> <asset name regex>
  local repo="$1" pattern="$2" json url name
  json="$(curl -fsSL -A "$USER_AGENT" "https://api.github.com/repos/$repo/releases/latest")"
  url="$(jq -r --arg re "$pattern" '.assets[] | select(.name | test($re)) | .browser_download_url' <<<"$json" | head -1)"
  [[ -n "$url" ]] || { warn "$repo: no asset matching /$pattern/ in the latest release, install it by hand"; return 0; }
  name="$(basename "$url")"
  log "$repo $(jq -r .tag_name <<<"$json") -> $name"
  curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/$name" "$url"
}

for slug in "${MODRINTH_SLUGS[@]}"; do
  install_modrinth "$slug"
done

install_github_latest "$OCM_REPO" '^OldCombatMechanics.*\.jar$'

log "Vault 1.7.3 -> Vault.jar"
curl -fsSL -A "$USER_AGENT" -o "$PLUGINS_DIR/Vault.jar" "$VAULT_URL"

log "Installed plugins:"
ls -1 "$PLUGINS_DIR"/*.jar | xargs -n1 basename
