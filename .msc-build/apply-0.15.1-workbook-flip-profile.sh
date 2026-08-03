#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_GLOB="$ROOT/.msc-build/msc-0.15.1-workbook-flip-profile.part*"
ENCODED="$(mktemp --suffix=.b64)"
ARCHIVE="$(mktemp --suffix=.tar.xz)"
trap 'rm -f "$ENCODED" "$ARCHIVE"' EXIT

EXPECTED_SHA256='f3c057e016c23a729ff50f31d3d6057e135c407e373457b6f4cc80809cf20dad'
compgen -G "$PAYLOAD_GLOB" >/dev/null
cat $PAYLOAD_GLOB > "$ENCODED"
base64 --decode "$ENCODED" > "$ARCHIVE"
echo "$EXPECTED_SHA256  $ARCHIVE" | sha256sum -c -
tar -xJf "$ARCHIVE" -C "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
WEB="$ROOT/MyStudyCompanionWeb"

grep -Fq '0.15.1-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq '0.15.1-wear-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq 'buildCrosswordPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'buildWordSearchPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'findDifferences' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'GoogleProfileHints' "$APP/auth/AuthModels.kt"
grep -Fq 'ProfileAgeSetupScreen' "$APP/ui/MyStudyCompanionApp.kt"
grep -Fq 'greetingName' "$APP/ui/HomeScreen.kt"
grep -Fq 'contentWindowInsets = WindowInsets.safeDrawing' "$APP/ui/MyStudyCompanionApp.kt"
grep -Fq 'renderCrossword' "$WEB/workbook.js"
grep -Fq 'renderWordSearch' "$WEB/workbook.js"
grep -Fq 'renderDifferences' "$WEB/workbook.js"

if grep -R -n -F 'Good morning, Kaleb' "$APP" "$WEB"; then
  echo 'Hard-coded Kaleb home greeting remains.' >&2
  exit 1
fi

printf 'Applied My Study Companion 0.15.1 workbook, Z Flip, and account-profile fixes.\n'
