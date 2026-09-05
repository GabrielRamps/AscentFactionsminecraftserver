#!/usr/bin/env bash
# One-shot setup of the dev server: stories E0-S1 and E0-S3.
#
#   scripts/bootstrap.sh
#
# Does, in order:
#   1. download and pin a Paper build          (update-paper.sh)
#   2. accept the Minecraft EULA
#   3. apply server/config/server.properties into the server
#   4. install the third-party plugins         (install-plugins.sh)
#
# Re-runnable: every step is idempotent. It does NOT start the server; use
# ./dev.sh for that.

set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/lib.sh"

need curl
need jq

mkdir -p "$SERVER_DIR"
log "Server directory: $SERVER_DIR"

# --- 1. Paper ---------------------------------------------------------------
"$HERE/update-paper.sh"

# --- 2. EULA ----------------------------------------------------------------
# You are accepting https://aka.ms/MinecraftEULA by running this script.
if [ -f "$SERVER_DIR/eula.txt" ] && grep -q '^eula=true' "$SERVER_DIR/eula.txt"; then
  log "EULA already accepted"
else
  log "Accepting the Minecraft EULA (https://aka.ms/MinecraftEULA)"
  echo "eula=true" >"$SERVER_DIR/eula.txt"
fi

# --- 3. server.properties ---------------------------------------------------
# Merge our documented keys into whatever Paper generated, rather than
# overwriting the file, so keys a newer Paper adds are preserved.
TEMPLATE="$REPO_ROOT/server/config/server.properties"
PROPS="$SERVER_DIR/server.properties"

set_prop() {
  local key="$1" value="$2" found=0 line
  touch "$PROPS"
  : >"$PROPS.tmp"
  while IFS= read -r line || [ -n "$line" ]; do
    if [ "${line%%=*}" = "$key" ]; then
      printf '%s=%s\n' "$key" "$value" >>"$PROPS.tmp"
      found=1
    else
      printf '%s\n' "$line" >>"$PROPS.tmp"
    fi
  done <"$PROPS"
  [ "$found" -eq 1 ] || printf '%s=%s\n' "$key" "$value" >>"$PROPS.tmp"
  mv "$PROPS.tmp" "$PROPS"
}

log "Applying server.properties from the template"
applied=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in '' | '#'*) continue ;; esac
  case "$line" in *=*) ;; *) continue ;; esac
  set_prop "${line%%=*}" "${line#*=}"
  applied=$((applied + 1))
done <"$TEMPLATE"
log "Applied $applied keys"

# The dev server runs on its own port so it never collides with a prod server
# on the same host (PRD §6.7).
if [ -n "${ASCENT_PORT:-}" ]; then
  log "Overriding server-port to $ASCENT_PORT"
  set_prop server-port "$ASCENT_PORT"
fi

# --- 4. Plugins -------------------------------------------------------------
"$HERE/install-plugins.sh"

cat <<MSG

Bootstrap complete.

Next:
  1. Build the plugin and start the server:
       ./dev.sh
  2. Join from your client, then op yourself from the console:
       tmux attach -t ascent
       op <your-minecraft-name>
  3. Apply the OldCombatMechanics settings listed in server/PLUGINS.md,
     then ./dev.sh again to restart.
MSG
