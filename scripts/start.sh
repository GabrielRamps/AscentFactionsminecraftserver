#!/usr/bin/env bash
# Starts the Paper server with Aikar's flags.
#
# Reads ASCENT_SERVER_DIR and ASCENT_MEMORY from the environment, falling back
# to the repo's .env, then to ~/ascent-server and 4G.
#
#   scripts/start.sh                 # run in the foreground
#   ASCENT_MEMORY=12G scripts/start.sh
#
# dev.sh runs this inside tmux. Run it directly when you want the console.

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

MEMORY="${ASCENT_MEMORY:-$(read_env ASCENT_MEMORY)}"
MEMORY="${MEMORY:-4G}"
PAPER_JAR="${ASCENT_PAPER_JAR:-paper.jar}"

if [ ! -d "$SERVER_DIR" ]; then
  die "server directory not found: $SERVER_DIR
Run scripts/bootstrap.sh, or set ASCENT_SERVER_DIR in .env."
fi
if [ ! -f "$SERVER_DIR/$PAPER_JAR" ]; then
  die "$PAPER_JAR not found in $SERVER_DIR
Run scripts/bootstrap.sh to download a pinned Paper build."
fi
if [ ! -f "$SERVER_DIR/eula.txt" ] || ! grep -q '^eula=true' "$SERVER_DIR/eula.txt"; then
  die "the Minecraft EULA has not been accepted.
Read https://aka.ms/MinecraftEULA then write eula=true to $SERVER_DIR/eula.txt"
fi

# Aikar's flags (https://docs.papermc.io/paper/aikars-flags). Heaps of 12G and
# above use the large-heap region/nursery sizing.
mem_to_mb() {
  local raw="${1^^}"
  local num="${raw%[GM]}"
  case "$raw" in
    *G) echo $((num * 1024)) ;;
    *M) echo "$num" ;;
    *) echo $((num / 1024 / 1024)) ;;
  esac
}

MEM_MB="$(mem_to_mb "$MEMORY")"

FLAGS=(
  "-Xms${MEMORY}" "-Xmx${MEMORY}"
  --add-modules=jdk.incubator.vector
  -XX:+UseG1GC
  -XX:+ParallelRefProcEnabled
  -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions
  -XX:+DisableExplicitGC
  -XX:+AlwaysPreTouch
  -XX:G1HeapWastePercent=5
  -XX:G1MixedGCCountTarget=4
  -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem
  -XX:MaxTenuringThreshold=1
  -Dusing.aikars.flags=https://mcflags.emc.gs
  -Daikars.new.flags=true
)

if [ "$MEM_MB" -ge 12288 ]; then
  FLAGS+=(
    -XX:G1NewSizePercent=40
    -XX:G1MaxNewSizePercent=50
    -XX:G1HeapRegionSize=16M
    -XX:G1ReservePercent=15
    -XX:InitiatingHeapOccupancyPercent=20
  )
else
  FLAGS+=(
    -XX:G1NewSizePercent=30
    -XX:G1MaxNewSizePercent=40
    -XX:G1HeapRegionSize=8M
    -XX:G1ReservePercent=20
    -XX:InitiatingHeapOccupancyPercent=15
  )
fi

cd "$SERVER_DIR"
log "Starting Paper in $SERVER_DIR with ${MEMORY} heap"
exec java "${FLAGS[@]}" -jar "$PAPER_JAR" --nogui
