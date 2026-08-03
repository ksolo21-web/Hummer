#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH_B64="$ROOT/.msc-build/msc-0.15.3-professional-workbook-code.patch.xz.b64"
PATCH_XZ="$(mktemp --suffix=.patch.xz)"
PATCH_FILE="$(mktemp --suffix=.patch)"
trap 'rm -f "$PATCH_XZ" "$PATCH_FILE"' EXIT

EXPECTED_PATCH_SHA256='363b6e14a52393dfe2fdc047f0e8b6f79a330dd8c678a5809773393b24f0cfa3'
base64 --decode "$PATCH_B64" > "$PATCH_XZ"
echo "$EXPECTED_PATCH_SHA256  $PATCH_XZ" | sha256sum -c -
xz --decompress --stdout "$PATCH_XZ" > "$PATCH_FILE"
(
  cd "$ROOT"
  patch -p1 --forward --batch < "$PATCH_FILE"
)

# The activity model stores the numbered-palette summary. The full 35–70
# closed regions are owned and verified by WorkbookIllustrationCatalog and its
# pixel mask, so the legacy generator test must only require a usable palette.
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / 'MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion/InteractiveWorkbookGeneratorTest.kt'
text = path.read_text(encoding='utf-8')
legacy = 'assertTrue(art.filter { it.kind == WorkbookActivityKind.COLOR_BY_NUMBER }.all { it.colorRegions.size == 6 })'
wrong_layer = 'assertTrue(art.filter { it.kind == WorkbookActivityKind.COLOR_BY_NUMBER }.all { it.colorRegions.size in 35..70 })'
correct = 'assertTrue(art.filter { it.kind == WorkbookActivityKind.COLOR_BY_NUMBER }.all { it.colorRegions.isNotEmpty() })'
if legacy in text:
    text = text.replace(legacy, correct, 1)
elif wrong_layer in text:
    text = text.replace(wrong_layer, correct, 1)
elif correct not in text:
    raise SystemExit('The workbook color interaction assertion was not found.')
path.write_text(text, encoding='utf-8')
PY

python3 "$ROOT/.msc-build/generate-0.15.3-professional-workbook-assets.py" --repo-root "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
TESTS="$ROOT/MyStudyCompanion/app/src/test/java/com/mystudycompanion/app"
ANDROID_ASSETS="$ROOT/MyStudyCompanion/app/src/main/assets/workbook"
WEB_ASSETS="$ROOT/MyStudyCompanionWeb/assets/workbook"

# Production renderer must load stored professional assets and masks.
grep -Fq 'object WorkbookIllustrationCatalog' "$APP/companion/WorkbookIllustrationCatalog.kt"
grep -Fq 'regionMaskPath' "$APP/companion/WorkbookIllustrationCatalog.kt"
grep -Fq 'difference-changed.webp' "$APP/companion/WorkbookIllustrationCatalog.kt"
grep -Fq 'drawing-step-1.webp' "$APP/companion/WorkbookIllustrationCatalog.kt"
grep -Fq 'loadAssetBitmap' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'rememberWorkbookIllustration' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'buildColorOverlay' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'regionAt' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'drawIllustrationBitmap' "$APP/ui/InteractiveWorkbookEditor.kt"
grep -Fq 'WorkbookIllustrationCatalogTest' "$TESTS/companion/WorkbookIllustrationCatalogTest.kt"
grep -Fq 'it.colorRegions.isNotEmpty()' "$TESTS/companion/InteractiveWorkbookGeneratorTest.kt"

# Reject the primitive vector scene regression from all active production code.
if grep -R -n -E 'fun drawWorkbookArt|WorkbookArtScene\(|workbookArtScene\(' "$APP/ui/InteractiveWorkbookEditor.kt"; then
  echo 'Primitive procedural workbook art remains active.' >&2
  exit 1
fi

python3 - "$ANDROID_ASSETS" "$WEB_ASSETS" <<'PY'
import json
import sys
from pathlib import Path
from PIL import Image
import numpy as np

for root_arg in sys.argv[1:]:
    root = Path(root_arg)
    manifest = json.loads((root / 'manifest.json').read_text())
    assert manifest['version'] >= 3
    assert len(manifest['assets']) == 16
    assert all(35 <= len(asset['regions']) <= 70 for asset in manifest['assets'])
    assert all(len(asset['differences']) == 5 for asset in manifest['assets'])
    for asset in manifest['assets']:
        folder = root / asset['id']
        for name in (
            'master.webp', 'line.webp', 'drawing-step-1.webp',
            'drawing-step-2.webp', 'difference-changed.webp', 'region-mask.png',
        ):
            path = folder / name
            assert path.is_file() and path.stat().st_size > 600, path
        labels = np.asarray(Image.open(folder / 'region-mask.png'))
        assert 35 <= len(np.unique(labels)) <= 70
PY

printf 'Applied My Study Companion 0.15.3 professional stored-illustration workbook repair.\n'
