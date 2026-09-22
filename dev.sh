#!/usr/bin/env bash
# E0-S4: one command to rebuild the plugin and restart the dev server.
#
#   ./dev.sh                 build the jar, copy it, restart, wait for "Done"
#   ./dev.sh --no-restart    build and copy only
#   ./dev.sh --console       attach to the server console when it is up
#   ./dev.sh --full          also run the tests and format checks (CI does
#                            this on every push; locally it needs a spare
#                            JVM the machine may not have next to the server)
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
FULL=0
for arg in "$@"; do
  case "$arg" in
    --no-restart) RESTART=0 ;;
    --console) ATTACH=1 ;;
    --full) FULL=1 ;;
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

source "$REPO_ROOT/scripts/lib.sh"

LOG="$SERVER_DIR/logs/latest.log"

if [ ! -d "$SERVER_DIR/plugins" ]; then
  die "$SERVER_DIR/plugins not found.
Run scripts/bootstrap.sh first, or set ASCENT_SERVER_DIR in .env."
fi

echo "==> Building"
if [ "$FULL" -eq 1 ]; then
  ./gradlew build copyToServer -PascentServerDir="$SERVER_DIR" --console=plain
else
  # Just the shaded jar. Tests fork a second JVM, and next to a running Paper
  # server plus the database containers that fork can time out on a laptop.
  ./gradlew copyToServer -PascentServerDir="$SERVER_DIR" --console=plain
fi

if [ "$RESTART" -eq 0 ]; then
  echo "==> Jar copied; skipping restart (--no-restart)."
  exit 0
fi

if ! command -v tmux >/dev/null 2>&1; then
  echo "error: tmux is not installed (apt install tmux)." >&2
  exit 1
fi

# PIDs of Paper JVMs running out of this server directory. Killing the tmux
# session does not always take the JVM with it: a shutdown that hangs in a
# plugin or a world save keeps running, holding world/session.lock and the
# port, and the next boot then fails with "already locked" while players
# time out against the half-dead old process.
server_pids() {
  local pid
  for pid in $(pgrep -f -- "-jar ${ASCENT_PAPER_JAR:-paper.jar}" 2>/dev/null); do
    if [ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" = "$SERVER_DIR" ]; then
      echo "$pid"
    fi
  done
}

# Waits up to $1 seconds for every server JVM to exit.
wait_for_exit() {
  local waited=0
  while [ -n "$(server_pids)" ]; do
    if [ "$waited" -ge "$1" ]; then
      return 1
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 0
}

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

# The session is gone; make sure the JVM is too, or the new boot cannot lock
# the world. Try a polite SIGTERM first, then SIGKILL.
if [ -n "$(server_pids)" ]; then
  echo "==> A Paper process is still running in $SERVER_DIR; stopping it"
  # shellcheck disable=SC2046
  kill $(server_pids) 2>/dev/null || true
  if ! wait_for_exit 20; then
    echo "    still running after 20s; force-killing" >&2
    # shellcheck disable=SC2046
    kill -9 $(server_pids) 2>/dev/null || true
    wait_for_exit 10 || die "could not stop the old server; see: pgrep -af paper.jar"
  fi
fi

echo "==> Starting server"
# Paper rolls logs/latest.log on boot by renaming it away and creating a new
# file. Remember the old file's inode so we only trust a "Done" line from the
# new one; matching on mtime let the previous boot's line through, because the
# stop sequence had just written to the old file.
old_log_inode=""
[ -f "$LOG" ] && old_log_inode=$(stat -c %i "$LOG")
# Measure the boot with the kernel's monotonic uptime, not the wall clock:
# WSL2's clock can jump minutes in either direction mid-boot, which made this
# loop declare a timeout while the server was coming up normally.
started_at=$(cut -d. -f1 /proc/uptime)
tmux new-session -d -s "$SESSION" "ASCENT_SERVER_DIR='$SERVER_DIR' '$REPO_ROOT/scripts/start.sh'"

while :; do
  now=$(cut -d. -f1 /proc/uptime)
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
  if [ -f "$LOG" ] && [ "$(stat -c %i "$LOG")" != "$old_log_inode" ] && grep -q 'Done (' "$LOG"; then
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
