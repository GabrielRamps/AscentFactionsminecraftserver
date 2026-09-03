#!/usr/bin/env bash
# Downloads a pinned Paper build and keeps gradle.properties in sync with it.
#
#   scripts/update-paper.sh --check     report the latest stable build, change nothing
#   scripts/update-paper.sh             download it and rewrite the pin
#   scripts/update-paper.sh --version 1.21.8
#
# Uses the Fill v3 API (https://fill.papermc.io/v3/). The old api.papermc.io/v2
# is retired. If PaperMC changes the response shape, this script fails loudly
# and prints the raw response rather than pinning something wrong.
#
# PRD §10.4: pin the build and upgrade only at sprint boundaries.

set -euo pipefail

API="https://fill.papermc.io/v3"
PROJECT="paper"
UA="ascent-factions/update-paper (+https://github.com/GabrielRamps/AscentFactionsminecraftserver)"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$REPO_ROOT/gradle.properties"

LINE="${ASCENT_PAPER_LINE:-1.21}"
WANT_VERSION=""
CHECK_ONLY=0

while [ $# -gt 0 ]; do
  case "$1" in
    --check) CHECK_ONLY=1 ;;
    --line)
      LINE="${2:?--line needs a value, e.g. 1.21}"
      shift
      ;;
    --version)
      WANT_VERSION="${2:?--version needs a value, e.g. 1.21.8}"
      shift
      ;;
    -h | --help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "unknown option: $1 (try --help)" >&2
      exit 2
      ;;
  esac
  shift
done

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "error: $tool is required (apt install $tool)" >&2
    exit 1
  }
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() {
  local url="$1" out="$2"
  if ! curl -fsSL -A "$UA" -o "$out" "$url"; then
    echo "error: request failed: $url" >&2
    exit 1
  fi
}

die_shape() {
  echo "error: $1" >&2
  echo "The Fill v3 response did not look the way this script expects." >&2
  echo "Raw response saved to: $2" >&2
  echo "Check https://fill.papermc.io/swagger-ui and update this script." >&2
  exit 1
}

# --- Resolve the version ----------------------------------------------------
if [ -z "$WANT_VERSION" ]; then
  fetch "$API/projects/$PROJECT" "$TMP/project.json"
  cp "$TMP/project.json" "$TMP/project.keep.json"
  WANT_VERSION="$(
    jq -r --arg line "$LINE" '
      (.versions[$line] // empty)[]? , (.versions[]?[]? | select(startswith($line + ".")))
    ' "$TMP/project.json" 2>/dev/null | sort -V -u | tail -n 1
  )"
  [ -n "$WANT_VERSION" ] || die_shape "no versions found on the $LINE line" "$TMP/project.keep.json"
fi

# --- Resolve the newest stable build ----------------------------------------
fetch "$API/projects/$PROJECT/versions/$WANT_VERSION/builds" "$TMP/builds.json"
cp "$TMP/builds.json" "$TMP/builds.keep.json"

BUILD_JSON="$(
  jq -c '
    (if type == "array" then . else (.builds // []) end)
    | map(select((.channel // "STABLE") | ascii_upcase == "STABLE"))
    | sort_by(.id) | last // empty
  ' "$TMP/builds.json" 2>/dev/null
)"
[ -n "$BUILD_JSON" ] || die_shape "no stable build for $WANT_VERSION" "$TMP/builds.keep.json"

BUILD_ID="$(jq -r '.id // empty' <<<"$BUILD_JSON")"
URL="$(jq -r '.downloads["server:default"].url // empty' <<<"$BUILD_JSON")"
SHA="$(jq -r '.downloads["server:default"].checksums.sha256 // empty' <<<"$BUILD_JSON")"

[ -n "$BUILD_ID" ] || die_shape "build has no id" "$TMP/builds.keep.json"
[ -n "$URL" ] || die_shape "build $BUILD_ID has no server:default download url" "$TMP/builds.keep.json"

CURRENT="$(sed -n 's/^paperVersion=//p' "$PROPS" | tail -n 1)"
CURRENT_BUILD="$(sed -n 's/^paperBuild=//p' "$PROPS" | tail -n 1)"

echo "Latest stable Paper on the $LINE line: $WANT_VERSION build $BUILD_ID"
echo "Currently pinned:                      ${CURRENT:-none} build ${CURRENT_BUILD:-none}"

if [ "$CHECK_ONLY" -eq 1 ]; then
  exit 0
fi

# --- Download and verify ----------------------------------------------------
read_env() {
  [ -f "$REPO_ROOT/.env" ] || return 0
  sed -n "s/^[[:space:]]*$1=//p" "$REPO_ROOT/.env" | tail -n 1 |
    sed 's/^"\(.*\)"$/\1/; s/^'"'"'\(.*\)'"'"'$/\1/'
}
SERVER_DIR="${ASCENT_SERVER_DIR:-$(read_env ASCENT_SERVER_DIR)}"
SERVER_DIR="${SERVER_DIR:-$HOME/ascent-server}"
mkdir -p "$SERVER_DIR"

echo "Downloading $URL"
fetch "$URL" "$TMP/paper.jar"

if [ -n "$SHA" ]; then
  ACTUAL="$(sha256sum "$TMP/paper.jar" | cut -d' ' -f1)"
  if [ "$ACTUAL" != "$SHA" ]; then
    echo "error: checksum mismatch. expected $SHA, got $ACTUAL" >&2
    exit 1
  fi
  echo "sha256 verified."
else
  echo "warning: the API returned no sha256; the download was not verified." >&2
fi

if [ -f "$SERVER_DIR/paper.jar" ]; then
  cp "$SERVER_DIR/paper.jar" "$SERVER_DIR/paper.jar.bak"
  echo "Previous jar kept as paper.jar.bak"
fi
mv "$TMP/paper.jar" "$SERVER_DIR/paper.jar"
echo "Installed $SERVER_DIR/paper.jar"

# --- Rewrite the pin --------------------------------------------------------
API_VERSION="${WANT_VERSION}-R0.1-SNAPSHOT"
tmp_props="$(mktemp)"
sed -e "s|^paperApiVersion=.*|paperApiVersion=$API_VERSION|" \
  -e "s|^paperVersion=.*|paperVersion=$WANT_VERSION|" \
  -e "s|^paperBuild=.*|paperBuild=$BUILD_ID|" \
  "$PROPS" >"$tmp_props"
mv "$tmp_props" "$PROPS"

echo "Pinned gradle.properties to $API_VERSION (build $BUILD_ID)."
echo "Next: ./gradlew build && ./dev.sh, then commit gradle.properties."
