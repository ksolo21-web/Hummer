#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION=9.4.1
SHA256=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb
CACHE_DIR="$APP_HOME/.gradle-bootstrap"
ZIP="$CACHE_DIR/gradle-$VERSION-bin.zip"
DIST="$CACHE_DIR/gradle-$VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP" ]; then
    URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "KREATIV Studio needs curl or wget once to download Gradle $VERSION." >&2
      exit 1
    fi
  fi
  ACTUAL=$(if command -v sha256sum >/dev/null 2>&1; then sha256sum "$ZIP" | awk '{print $1}'; else shasum -a 256 "$ZIP" | awk '{print $1}'; fi)
  [ "$ACTUAL" = "$SHA256" ] || { echo "Gradle archive checksum mismatch." >&2; rm -f "$ZIP"; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required." >&2; exit 1; }
  unzip -q -o "$ZIP" -d "$CACHE_DIR"
fi
exec "$DIST/bin/gradle" "$@"
