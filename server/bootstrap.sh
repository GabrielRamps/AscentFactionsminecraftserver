#!/usr/bin/env bash
# One-shot setup of the local test server (PRD E0-S1).
#   - creates $ASCENT_SERVER_DIR (default ~/ascent-server)
#   - downloads the pinned Paper build
#   - writes eula.txt and the tuned server.properties keys
#   - installs start.sh
#
# Running this script accepts the Minecraft EULA: https://aka.ms/MinecraftEULA
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
server_dir="${ASCENT_SERVER_DIR:-$HOME/ascent-server}"

mkdir -p "$server_dir/plugins"
echo "Server directory: $server_dir"

"$here/update-paper.sh"

cat >"$server_dir/eula.txt" <<'EOF'
# By changing the setting below to TRUE you are indicating your agreement to the EULA
# (https://aka.ms/MinecraftEULA).
eula=true
EOF

props="$server_dir/server.properties"
touch "$props"
set_prop() {
  if grep -qE "^$1=" "$props"; then
    sed -i '' -e "s|^$1=.*|$1=$2|" "$props"
  else
    echo "$1=$2" >>"$props"
  fi
}
set_prop online-mode true
set_prop view-distance 6
set_prop simulation-distance 4
set_prop spawn-protection 0
set_prop allow-flight true
set_prop motd "Ascent Factions (dev)"
set_prop max-players 20

cp "$here/start.sh" "$server_dir/start.sh"
chmod +x "$server_dir/start.sh"

cat <<EOF

Done. Next:
  1. cd "$server_dir" && ./start.sh          # first boot generates the world; wait for "Done"
  2. In the console:  op <your-name>          # then stop
  3. server/install-plugins.py               # third-party plugins (E0-S3)
  4. ./dev.sh                                # build + copy + restart in tmux (E0-S4)
EOF
