#!/usr/bin/env bash
# Downloads the pinned Paper build (gradle.properties: paperVersion / paperBuild) into the server
# directory via the Fill v3 API, verifies SHA-256, and points paper.jar at it.
#
#   server/update-paper.sh            # fetch the pinned build
#   server/update-paper.sh --latest   # fetch the latest stable build of paperVersion and re-pin
#                                     # gradle.properties (paperBuild, paperApiVersion) to it
#
# Env: ASCENT_SERVER_DIR (default ~/ascent-server)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/.." && pwd)"
props="$repo/gradle.properties"
server_dir="${ASCENT_SERVER_DIR:-$HOME/ascent-server}"

prop() { grep -E "^$1=" "$props" | head -1 | cut -d= -f2- | tr -d '[:space:]'; }

version="$(prop paperVersion)"
build="$(prop paperBuild)"
mode="pinned"
[[ "${1:-}" == "--latest" ]] && mode="latest"

api="https://fill.papermc.io/v3/projects/paper/versions/$version/builds"
if [[ "$mode" == "latest" ]]; then
  url="$api/latest"
else
  url="$api/$build"
fi

echo "Querying $url"
meta="$(curl -fsSL --max-time 30 -A "ascent-update-paper/0.1" "$url")"

read -r build channel jar_url sha256 jar_name <<<"$(python3 - "$meta" <<'EOF'
import json, sys
d = json.loads(sys.argv[1])
dl = d["downloads"]["server:default"]
print(d["id"], d["channel"], dl["url"], dl["checksums"]["sha256"], dl["name"])
EOF
)"

if [[ "$channel" != "STABLE" ]]; then
  echo "error: build $build is channel $channel, not STABLE. Refusing." >&2
  exit 1
fi

mkdir -p "$server_dir"
target="$server_dir/$jar_name"

if [[ -f "$target" ]] && echo "$sha256  $target" | shasum -a 256 -c --status; then
  echo "Already have $jar_name (checksum ok)"
else
  echo "Downloading $jar_name"
  curl -fL --progress-bar --max-time 600 -A "ascent-update-paper/0.1" -o "$target.part" "$jar_url"
  echo "$sha256  $target.part" | shasum -a 256 -c --status || {
    echo "error: checksum mismatch for $jar_name" >&2
    rm -f "$target.part"
    exit 1
  }
  mv "$target.part" "$target"
fi

ln -sfn "$jar_name" "$server_dir/paper.jar"
echo "paper.jar -> $jar_name"

if [[ "$mode" == "latest" ]]; then
  api_version="$version.build.$build-stable"
  sed -i '' -e "s/^paperBuild=.*/paperBuild=$build/" \
            -e "s/^paperApiVersion=.*/paperApiVersion=$api_version/" "$props"
  echo "Re-pinned gradle.properties: paperBuild=$build paperApiVersion=$api_version"
fi
