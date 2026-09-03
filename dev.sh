#!/usr/bin/env bash
# E0-S4: one command to rebuild the plugin and restart the dev server.
#
#   ./dev.sh                 build, copy the jar, restart, wait for "Done"
#   ./dev.sh --no-restart    build and copy only
#   ./dev.sh --console       attach to the server console when it is up
#
# Attach to the console at any time with:  tmux attach -t ascent
# Detach without stopping the server with: Ctrl-b then d

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

SESSION="${ASCENT_TMUX_SESSION:-ascent}"
BOOT_TIMEOUT="${ASCENT_BOOT_TIMEOUT:-90}"
STOP_TIMEOUT="${ASCENT_STOP_TIMEOUT:-45}"

RESTART=1
ATTACH=0
for arg in "$@"; do
  case "$arg" in
    --no-restart) RESTART=0 ;;
    --console) ATTACH=1 ;;
    -h | --help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "unknown option: $arg (try --help)" >&2
      exit 2
      ;;
  esac
done

read_env() {
  [ -f "$REPO_ROOT/.env" ] || return 0
  sed -n "s/^[[:space:]]*$1=//p" "$REPO_ROOT/.env" | tail -n 1 |
    sed 's/^"\(.*\)"$/\1/; s/^'"'"'\(.*\)'"'"'$/\1/'
}

SERVER_DIR="${ASCENT_SERVER_DIR:-$(read_env ASCENT_SERVER_DIR)}"
SERVER_DIR="${SERVER_DIR:-$HOME/ascent-server}"
LOG="$SERVER_DIR/logs/latest.log"

if [ ! -d "$SERVER_DIR/plugins" ]; then
  echo "error: $SERVER_DIR/plugins not found. Complete E0-S1 first," >&2
  echo "or set ASCENT_SERVER_DIR in .env." >&2
  exit 1
fi

echo "==> Building"
./gradlew build copyToServer -PascentServerDir="$SERVER_DIR" --console=plain

if [ "$RESTART" -eq 0 ]; then
  echo "==> Jar copied; skipping restart (--no-restart)."
  exit 0
fi

if ! command -v tmux >/dev/null 2>&1; then
  echo "error: tmux is not installed (apt install tmux)." >&2
  exit 1
fi

if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "==> Stopping running server"
  # Ask Paper to shut down cleanly so worlds and player data are flushed.
  tmux send-keys -t "$SESSION" "stop" Enter
  waited=0
  while tmux has-session -t "$SESSION" 2>/dev/null; do
    if [ "$waited" -ge "$STOP_TIMEOUT" ]; then
      echo "    server did not stop in ${STOP_TIMEOUT}s; killing the session" >&2
      tmux kill-session -t "$SESSION" 2>/dev/null || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
fi

echo "==> Starting server"
started_at=$(date +%s)
tmux new-session -d -s "$SESSION" "ASCENT_SERVER_DIR='$SERVER_DIR' '$REPO_ROOT/scripts/start.sh'"

# Paper rotates logs/latest.log on boot, so wait for a log newer than the
# restart before trusting anything we read out of it.
while :; do
  now=$(date +%s)
  elapsed=$((now - started_at))
  if [ "$elapsed" -ge "$BOOT_TIMEOUT" ]; then
    echo "==> Server did not report Done within ${BOOT_TIMEOUT}s." >&2
    echo "    Check the console: tmux attach -t $SESSION" >&2
    exit 1
  fi
  if ! tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "==> Server exited during startup. Last 30 log lines:" >&2
    [ -f "$LOG" ] && tail -n 30 "$LOG" >&2
    exit 1
  fi
  if [ -f "$LOG" ] && [ "$(stat -c %Y "$LOG")" -ge "$started_at" ] && grep -q 'Done (' "$LOG"; then
    echo "==> Server up in ${elapsed}s."
    grep -m1 'Done (' "$LOG" | sed 's/^/    /'
    break
  fi
  sleep 1
done

# Surface plugin problems that do not stop the boot.
if grep -qiE '\[Ascent\].*(ERROR|Exception)|Could not load .Ascent' "$LOG"; then
  echo "==> Ascent reported errors during startup:" >&2
  grep -iE '\[Ascent\].*(ERROR|Exception)|Could not load .Ascent' "$LOG" | head -n 20 >&2
fi

if [ "$ATTACH" -eq 1 ]; then
  exec tmux attach -t "$SESSION"
fi

echo "    Console: tmux attach -t $SESSION   (detach with Ctrl-b then d)"
