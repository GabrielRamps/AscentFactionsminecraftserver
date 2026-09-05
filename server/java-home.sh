#!/usr/bin/env bash
# Sourced by the other scripts. Resolves a JDK that satisfies javaVersion in gradle.properties.
# Order: $JAVA_HOME if set, then macOS java_home, then Homebrew's keg-only openjdk@N.

ascent_resolve_java_home() {
  local want="$1"
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME"
    return 0
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    local jh
    jh="$(/usr/libexec/java_home -v "$want" 2>/dev/null || true)"
    if [[ -n "$jh" && -x "$jh/bin/java" ]]; then
      echo "$jh"
      return 0
    fi
  fi
  for prefix in /opt/homebrew /usr/local; do
    if [[ -x "$prefix/opt/openjdk@$want/bin/java" ]]; then
      echo "$prefix/opt/openjdk@$want"
      return 0
    fi
  done
  if command -v java >/dev/null 2>&1; then
    echo "$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    return 0
  fi
  return 1
}

ascent_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

ascent_prop() {
  # ascent_prop <key> : read a value from gradle.properties
  grep -E "^$1=" "$(ascent_repo_root)/gradle.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]'
}

ASCENT_JAVA_VERSION="$(ascent_prop javaVersion)"
if ! JAVA_HOME="$(ascent_resolve_java_home "$ASCENT_JAVA_VERSION")"; then
  echo "error: no JDK $ASCENT_JAVA_VERSION found. Install with: brew install openjdk@$ASCENT_JAVA_VERSION" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
