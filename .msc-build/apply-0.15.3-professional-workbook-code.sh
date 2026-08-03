#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_GLOB="$ROOT/.msc-build/msc-0.15.3-professional-workbook-code.part*"
ENCODED="$(mktemp --suffix=.b64)"
ARCHIVE="$(mktemp --suffix=.tar.xz)"
trap 'rm -f "$ENCODED" "$ARCHIVE"' EXIT

EXPECTED_SHA256='068fd9e28cc0af9b5b2f0e984d11407e7032efd5507a268a296ca9f51b2471bf'
compgen -G "$PAYLOAD_GLOB" >/dev/null
cat $PAYLOAD_GLOB > "$ENCODED"
base64 --decode "$ENCODED" > "$ARCHIVE"
echo "$EXPECTED_SHA256  $ARCHIVE" | sha256sum -c -
tar -xJf "$ARCHIVE" -C "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
TESTS="$ROOT/MyStudyCompanion/app/src/test/java/com/mystudycompanion/app"
WEB="$ROOT/MyStudyCompanionWeb"

# Android stored-asset loader and exact subject mapping.
grep -Fq 'internal object ProfessionalWorkbookAssetLoader' "$APP/ui/ProfessionalWorkbookAssets.kt"
grep -Fq 'fun regionAt' "$APP/ui/ProfessionalWorkbookAssets.kt"
grep -Fq 'fun createFillOverlay' "$APP/ui/ProfessionalWorkbookAssets.kt"
grep -Fq 'WorkbookArtTemplate.NOAH_ARK' "$APP/ui/ProfessionalWorkbookAssets.kt"
grep -Fq 'WorkbookArtTemplate.GRATITUDE_JOURNAL' "$APP/ui/ProfessionalWorkbookAssets.kt"
grep -Fq 'ProfessionalWorkbookAssetsTest' "$TESTS/ui/ProfessionalWorkbookAssetsTest.kt"

# Android production workbook renderer uses stored images and masks.
grep -Fq 'rememberProfessionalWorkbookAsset' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'professionalAsset.differenceSpots' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'drawingStepIndex' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'drawPdfBitmap' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'createFillOverlay' "$APP/ui/InteractiveWorkbookEditor.kt"

# Model supports all professional workbook subjects while retaining saved-data compatibility.
grep -Fq 'NOAH_ARK' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'JONAH' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'DAVID_GOLIATH' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'DANIEL_LIONS' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'GOOD_SAMARITAN' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'WISE_BUILDERS' "$APP/companion/InteractiveWorkbookModels.kt"
grep -Fq 'ARMOR_OF_GOD' "$APP/companion/InteractiveWorkbookModels.kt"

# PWA renderer and exports use the same professional asset contract.
node --check "$WEB/workbook.js"
node --check "$WEB/sw.js"
grep -Fq 'loadProfessionalWorkbookAsset' "$WEB/workbook.js"
grep -Fq 'professionalPrintData' "$WEB/workbook.js"
grep -Fq 'regionMask' "$WEB/workbook.js"
grep -Fq 'msc-web-v0153-professional-workbook-assets-v1' "$WEB/sw.js"

printf 'Applied My Study Companion 0.15.3 professional workbook renderer code.\n'
