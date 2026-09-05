# Shared helpers for the scripts in this directory. Source it, do not run it.
#
#   source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
#
# Provides: REPO_ROOT, SERVER_DIR, MC_VERSION, USER_AGENT, and the
# log/warn/die/need/read_env helpers.

# shellcheck shell=bash

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

log() { printf '\033[36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }
die() {
  printf '\033[31merror:\033[0m %s\n' "$*" >&2
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required (try: sudo apt install $1)"
}

# Read one key from .env without sourcing the file, so a password containing
# shell metacharacters can never be executed.
read_env() {
  [ -f "$ENV_FILE" ] || return 0
  sed -n "s/^[[:space:]]*$1=//p" "$ENV_FILE" | tail -n 1 |
    sed 's/^"\(.*\)"$/\1/; s/^'"'"'\(.*\)'"'"'$/\1/'
}

# Read one key from gradle.properties, so the Paper pin has a single home.
read_prop() {
  sed -n "s/^[[:space:]]*$1=//p" "$REPO_ROOT/gradle.properties" | tail -n 1
}

SERVER_DIR="${ASCENT_SERVER_DIR:-$(read_env ASCENT_SERVER_DIR)}"
SERVER_DIR="${SERVER_DIR:-$HOME/ascent-server}"

# The Minecraft version the server runs, and the one plugins are matched
# against. Kept in gradle.properties so it cannot drift from the compile-time
# Paper API. scripts/update-paper.sh rewrites it.
MC_VERSION="${ASCENT_MC_VERSION:-$(read_prop paperVersion)}"

USER_AGENT="ascent-factions/scripts (+https://github.com/GabrielRamps/AscentFactionsminecraftserver)"
