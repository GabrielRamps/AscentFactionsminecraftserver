#!/usr/bin/env bash
# Starts the Paper server in the foreground with Aikar's flags.
# Heap from ASCENT_HEAP (default 4G); port from ASCENT_PORT via server.properties (see bootstrap.sh).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need java

cd "$SERVER_DIR"
[[ -f paper.jar ]] || die "no paper.jar in $SERVER_DIR - run server/install-paper.sh"

HEAP="${ASCENT_HEAP:-4G}"

# Aikar's flags: https://docs.papermc.io/paper/aikars-flags
exec java \
  -Xms"$HEAP" -Xmx"$HEAP" \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar --nogui
