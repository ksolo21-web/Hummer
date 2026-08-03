#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_GLOB="$ROOT/.msc-build/msc-0.15.1-complete-workbook-profile.part*"
ENCODED="$(mktemp --suffix=.b64)"
ARCHIVE="$(mktemp --suffix=.tar.xz)"
trap 'rm -f "$ENCODED" "$ARCHIVE"' EXIT

EXPECTED_SHA256='e37626a17f0440405e045a4a10057b1f5d9e78f8a3ba4b826a2d836f0bc724d7'
compgen -G "$PAYLOAD_GLOB" >/dev/null
cat $PAYLOAD_GLOB > "$ENCODED"
base64 --decode "$ENCODED" > "$ARCHIVE"
echo "$EXPECTED_SHA256  $ARCHIVE" | sha256sum -c -
tar -xJf "$ARCHIVE" -C "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
TESTS="$ROOT/MyStudyCompanion/app/src/test/java/com/mystudycompanion/app"
WEB="$ROOT/MyStudyCompanionWeb"

# Release identity and profile/account repairs.
grep -Fq '0.15.1-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq '0.15.1-wear-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq 'GoogleProfileHints' "$APP/auth/AuthModels.kt"
grep -Fq 'val rawBirthDate: String' "$APP/auth/AuthModels.kt"
grep -Fq 'SCOPE_BIRTHDAY' "$APP/auth/GoogleSignInCoordinator.kt"
grep -Fq 'ProfileAgeSetupScreen' "$APP/ui/MyStudyCompanionApp.kt"
grep -Fq 'greetingName' "$APP/ui/HomeScreen.kt"
grep -Fq 'contentWindowInsets = WindowInsets.safeDrawing' "$APP/ui/MyStudyCompanionApp.kt"
grep -Fq 'maxWidth < 390.dp' "$APP/ui/HomeScreen.kt"

# Complete workbook restoration: genuine activity library, saved work, closed-region
# color-by-number, drawing tools and steps, all puzzle engines, and completed exports.
grep -Fq 'ACTIVITY_LIBRARY' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'fun activityLibraryBook' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'Triple("gratitude-journal"' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'selectedColorNumbers' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'colorUndo' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'drawingSteps' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'fun hasStoredWork' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'fun applyWorkbookColor' "$APP/companion/CompanionHubRepository.kt"
grep -Fq 'fun redoWorkbookColor' "$APP/companion/CompanionHubRepository.kt"
grep -Fq 'fun redoWorkbookStroke' "$APP/companion/CompanionHubRepository.kt"
grep -Fq 'fun resetWorkbookPage' "$APP/companion/CompanionHubRepository.kt"
grep -Fq 'StoredWorkbookWorkView' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'pointInsidePolygon' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'drawingStepsFor' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'filledCorrectly' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'Open 16-page activity library' "$APP/ui/FamilyWorshipScreen.kt"
grep -Fq 'activityLibraryContainsAllPromisedRealSavedActivities' "$TESTS/companion/InteractiveWorkbookGeneratorTest.kt"

# Connected puzzle engines remain required on Android and web.
grep -Fq 'buildCrosswordPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'buildWordSearchPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'DifferencePuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
node --check "$WEB/workbook.js"
grep -Fq 'renderSavedWork' "$WEB/workbook.js"
grep -Fq 'activityLibraryBook' "$WEB/workbook.js"
grep -Fq 'selectedColorNumbers' "$WEB/workbook.js"
grep -Fq 'colorUndo' "$WEB/workbook.js"
grep -Fq 'drawingSteps' "$WEB/workbook.js"
grep -Fq 'renderCrossword' "$WEB/workbook.js"
grep -Fq 'renderWordSearch' "$WEB/workbook.js"
grep -Fq 'renderDifferences' "$WEB/workbook.js"
grep -Fq 'msc-web-v0151-complete-workbook-profile-v2' "$WEB/sw.js"

if grep -R -n -F 'Good morning, Kaleb' "$APP" "$WEB"; then
  echo 'Hard-coded Kaleb home greeting remains.' >&2
  exit 1
fi

printf 'Applied complete My Study Companion 0.15.1 workbook, persistence, Z Flip, and child-profile repairs.\n'
