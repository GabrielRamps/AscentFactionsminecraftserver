#!/usr/bin/env bash
# Fast test loop (E0-S4): build the plugin, copy it into the test server, restart the server
# inside a tmux session, and wait for it to report "Done".
#
#   ./dev.sh            build + restart
#   ./dev.sh --no-build restart only
#   ./dev.sh --attach   build + restart, then attach to the console (Ctrl+B, D to detach)
#
# Console:  tmux attach -t ascent
# Logs:     tail -f $SERVER_DIR/logs/latest.log
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/server/lib.sh"
need tmux

SESSION="ascent"
BUILD=1
ATTACH=0
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --attach) ATTACH=1 ;;
    *) die "unknown option: $arg" ;;
  esac
done

if [[ "$BUILD" == "1" ]]; then
  log "Building plugin"
  (cd "$HERE" && ./gradlew build copyToServer -q)
fi

if tmux has-session -t "$SESSION" 2>/dev/null; then
  log "Stopping running server"
  tmux send-keys -t "$SESSION" "stop" Enter
  for _ in $(seq 1 60); do
    tmux has-session -t "$SESSION" 2>/dev/null || break
    sleep 1
  done
  if tmux has-session -t "$SESSION" 2>/dev/null; then
    warn "server did not stop in 60s, killing session"
    tmux kill-session -t "$SESSION"
  fi
fi

mkdir -p "$SERVER_DIR/logs"
: > "$SERVER_DIR/logs/latest.log"

log "Starting server in tmux session '$SESSION'"
START=$(date +%s)
tmux new-session -d -s "$SESSION" -c "$SERVER_DIR" "$HERE/server/start.sh"

for _ in $(seq 1 120); do
  if grep -q 'Done (' "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
    log "Server up in $(( $(date +%s) - START ))s"
    grep -E 'Ascent .* enabled|\[Ascent\]' "$SERVER_DIR/logs/latest.log" | tail -3 || true
    [[ "$ATTACH" == "1" ]] && exec tmux attach -t "$SESSION"
    exit 0
  fi
  if ! tmux has-session -t "$SESSION" 2>/dev/null; then
    die "server exited during startup; check $SERVER_DIR/logs/latest.log"
  fi
  sleep 1
done
die "server did not reach 'Done' within 120s; check $SERVER_DIR/logs/latest.log"
