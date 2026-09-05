#!/usr/bin/env bash
# Starts the Paper server in the foreground with Aikar's flags.
# Copied into the server directory by bootstrap.sh; run it from there (or via dev.sh in tmux).
#
# Env overrides: MEM_MIN (default 2G), MEM_MAX (default 4G), JAVA_HOME.
set -euo pipefail

cd "$(dirname "$0")"

MEM_MIN="${MEM_MIN:-2G}"
MEM_MAX="${MEM_MAX:-4G}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /opt/homebrew/opt/openjdk@25 /usr/local/opt/openjdk@25; do
    [[ -x "$candidate/bin/java" ]] && JAVA_HOME="$candidate" && break
  done
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if [[ ! -e paper.jar ]]; then
  echo "error: paper.jar not found in $(pwd). Run server/update-paper.sh from the repo first." >&2
  exit 1
fi

# Aikar's flags: https://docs.papermc.io/paper/aikars-flags
AIKAR_FLAGS=(
  -XX:+UseG1GC
  -XX:+ParallelRefProcEnabled
  -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions
  -XX:+DisableExplicitGC
  -XX:+AlwaysPreTouch
  -XX:G1NewSizePercent=30
  -XX:G1MaxNewSizePercent=40
  -XX:G1HeapRegionSize=8M
  -XX:G1ReservePercent=20
  -XX:G1HeapWastePercent=5
  -XX:G1MixedGCCountTarget=4
  -XX:InitiatingHeapOccupancyPercent=15
  -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem
  -XX:MaxTenuringThreshold=1
  -Dusing.aikars.flags=https://mcflags.emc.gs
  -Daikars.new.flags=true
)

echo "Starting Paper with $JAVA ($MEM_MIN..$MEM_MAX)"
exec "$JAVA" -Xms"$MEM_MIN" -Xmx"$MEM_MAX" "${AIKAR_FLAGS[@]}" -jar paper.jar --nogui
