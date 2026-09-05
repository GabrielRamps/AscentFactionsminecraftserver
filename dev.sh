#!/usr/bin/env bash
# Fast test loop (PRD E0-S4): build, copy the jar to the test server, restart it in tmux.
#
#   ./dev.sh              build + copy + restart
#   ./dev.sh --no-build   restart only
#   ./dev.sh --attach     as above, then attach to the server console (Ctrl-B D to detach)
#   ./dev.sh --stop       stop the server and exit
#   ./dev.sh --logs       tail the server log
#
# Env: ASCENT_SERVER_DIR (default ~/ascent-server), ASCENT_TMUX_SESSION (default ascent)
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=server/java-home.sh
source server/java-home.sh

server_dir="${ASCENT_SERVER_DIR:-$HOME/ascent-server}"
session="${ASCENT_TMUX_SESSION:-ascent}"

do_build=1
do_attach=0
for arg in "$@"; do
  case "$arg" in
    --no-build) do_build=0 ;;
    --attach) do_attach=1 ;;
    --stop)
      if tmux has-session -t "$session" 2>/dev/null; then
        tmux send-keys -t "$session" "stop" Enter
        echo "Sent 'stop' to session '$session'"
      else
        echo "Server is not running"
      fi
      exit 0
      ;;
    --logs) exec tail -f "$server_dir/logs/latest.log" ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [[ ! -x "$server_dir/start.sh" || ! -e "$server_dir/paper.jar" ]]; then
  echo "error: $server_dir is not bootstrapped. Run server/bootstrap.sh first." >&2
  exit 1
fi

if (( do_build )); then
  ./gradlew build copyToServer -PserverDir="$server_dir" --console=plain -q
  echo "Built and copied: $(ls "$server_dir"/plugins/Ascent-*.jar)"
fi

if tmux has-session -t "$session" 2>/dev/null; then
  echo "Stopping running server..."
  tmux send-keys -t "$session" "stop" Enter
  for _ in $(seq 1 60); do
    tmux has-session -t "$session" 2>/dev/null || break
    sleep 1
  done
  if tmux has-session -t "$session" 2>/dev/null; then
    echo "Server did not stop in 60s; killing session" >&2
    tmux kill-session -t "$session"
  fi
fi

start_ts=$(date +%s)
tmux new-session -d -s "$session" -c "$server_dir" "./start.sh"
echo "Server starting in tmux session '$session'. Waiting for Done..."

log="$server_dir/logs/latest.log"
for _ in $(seq 1 120); do
  if [[ -f "$log" ]] && grep -q 'Done (' "$log" 2>/dev/null \
     && [[ $(stat -f %m "$log") -ge $start_ts ]]; then
    echo "Server is up in $(( $(date +%s) - start_ts ))s."
    break
  fi
  if ! tmux has-session -t "$session" 2>/dev/null; then
    echo "Server exited during startup. Last log lines:" >&2
    tail -n 30 "$log" >&2 || true
    exit 1
  fi
  sleep 1
done

echo "Console: tmux attach -t $session   (detach with Ctrl-B then D)"
if (( do_attach )); then
  exec tmux attach -t "$session"
fi
