#!/usr/bin/env bash
# Downloads the pinned Paper build via the PaperMC Fill v3 API into $SERVER_DIR/paper.jar.
# Pins come from gradle.properties (mcVersion, paperBuild). Verifies the SHA-256.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need curl; need jq; need sha256sum

mkdir -p "$SERVER_DIR"
API="https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds"

if [[ "$PAPER_BUILD" == "latest" ]]; then
  log "Resolving latest Paper build for $MC_VERSION"
  BUILD_JSON="$(curl -fsSL -A "$USER_AGENT" "$API/latest")"
else
  log "Resolving Paper $MC_VERSION build $PAPER_BUILD"
  BUILD_JSON="$(curl -fsSL -A "$USER_AGENT" "$API/$PAPER_BUILD")"
fi

BUILD_ID="$(jq -r '.id' <<<"$BUILD_JSON")"
CHANNEL="$(jq -r '.channel' <<<"$BUILD_JSON")"
URL="$(jq -r '.downloads["server:default"].url' <<<"$BUILD_JSON")"
SHA="$(jq -r '.downloads["server:default"].checksums.sha256' <<<"$BUILD_JSON")"
NAME="$(jq -r '.downloads["server:default"].name' <<<"$BUILD_JSON")"

[[ "$URL" != "null" && -n "$URL" ]] || die "could not find a server download in the Fill response:
$BUILD_JSON"
[[ "$CHANNEL" == "STABLE" ]] || warn "build $BUILD_ID is on channel $CHANNEL, not STABLE"

log "Downloading $NAME (build $BUILD_ID, $CHANNEL)"
TMP="$(mktemp)"
curl -fSL -A "$USER_AGENT" -o "$TMP" "$URL"
echo "$SHA  $TMP" | sha256sum -c --quiet || die "SHA-256 mismatch for $NAME"

mv "$TMP" "$SERVER_DIR/paper.jar"
echo "$NAME" > "$SERVER_DIR/paper.version"
log "Installed $SERVER_DIR/paper.jar ($NAME)"
