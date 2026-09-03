#!/usr/bin/env bash
# Shared helpers for the server/*.sh scripts. Source, don't execute.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load .env if present (never committed).
if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

SERVER_DIR="${ASCENT_SERVER_DIR:-$HOME/ascent-server}"
USER_AGENT="AscentFactions/0.1 (https://github.com/GabrielRamps/AscentFactionsminecraftserver)"

prop() { # prop <key> -> value from gradle.properties
  grep -E "^$1=" "$REPO_ROOT/gradle.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]'
}

MC_VERSION="$(prop mcVersion)"
PAPER_BUILD="$(prop paperBuild)"

log()  { printf '\033[1;34m[ascent]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ascent]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[ascent]\033[0m %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "missing required tool: $1"; }
