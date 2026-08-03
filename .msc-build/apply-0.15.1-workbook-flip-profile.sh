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

# Kotlin 2.x does not reliably infer the overloaded LocalDate::parse method
# reference inside the nested nullable/runCatching expression. Replace it with
# a fully typed parse while keeping invalid or missing Google birthdays safe.
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/auth/AuthModels.kt"
text = path.read_text(encoding="utf-8")
old = """            val birthDate = birthDateIso?.let { runCatching(LocalDate::parse).getOrNull() }\n            if (birthDate != null) {\n"""
new = """            val rawBirthDate: String = birthDateIso?.trim().orEmpty()\n            val birthDate: LocalDate? = if (rawBirthDate.isBlank()) {\n                null\n            } else {\n                try {\n                    LocalDate.parse(rawBirthDate)\n                } catch (_: java.time.format.DateTimeParseException) {\n                    null\n                }\n            }\n            if (birthDate != null) {\n"""
if old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
elif "val rawBirthDate: String" not in text:
    raise SystemExit("Google birthday parser compatibility target was not found")
PY

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
WEB="$ROOT/MyStudyCompanionWeb"

grep -Fq '0.15.1-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq '0.15.1-wear-private-alpha-workbook-flip-profile-fix' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq 'buildCrosswordPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'buildWordSearchPuzzle' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'findDifferences' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'GoogleProfileHints' "$APP/auth/AuthModels.kt"
grep -Fq 'val rawBirthDate: String' "$APP/auth/AuthModels.kt"
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
