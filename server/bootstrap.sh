#!/usr/bin/env bash
# One-shot setup of the local test server (E0-S1 + E0-S3):
#   1. download Paper           (install-paper.sh)
#   2. accept the EULA
#   3. write server.properties keys from the PRD
#   4. install third-party plugins (install-plugins.sh)
#   5. copy config overrides from server/config/ into the server
# Re-runnable. Does NOT start the server; use server/start.sh or ./dev.sh.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/lib.sh"

mkdir -p "$SERVER_DIR"
log "Server directory: $SERVER_DIR"

"$HERE/install-paper.sh"

log "Accepting EULA (https://aka.ms/MinecraftEULA)"
echo "eula=true" > "$SERVER_DIR/eula.txt"

set_prop() { # set_prop key value  (creates the file / key if absent)
  local f="$SERVER_DIR/server.properties"
  touch "$f"
  if grep -qE "^$1=" "$f"; then
    sed -i "s|^$1=.*|$1=$2|" "$f"
  else
    echo "$1=$2" >> "$f"
  fi
}

log "Writing server.properties"
set_prop online-mode true
set_prop view-distance 6
set_prop simulation-distance 4
set_prop spawn-protection 0
set_prop allow-flight true
set_prop server-port "${ASCENT_PORT:-25566}"
set_prop motd "Ascent Factions (${ASCENT_ENV:-dev})"
set_prop max-players 200
set_prop enforce-secure-profile false

"$HERE/install-plugins.sh"

if [[ -d "$HERE/config" ]]; then
  log "Copying config overrides from server/config/"
  cp -r "$HERE/config/." "$SERVER_DIR/plugins/"
fi

cat <<MSG

Bootstrap complete. Next:
  1. Start it once so every plugin writes its default config:
       server/start.sh          (Ctrl+C after you see "Done")
  2. Apply the OldCombatMechanics settings in server/config/README.md.
  3. Build and install the Ascent plugin, then restart:
       ./dev.sh
  4. Join from your client (port ${ASCENT_PORT:-25566}) and op yourself from the console:
       op <your-name>
MSG
