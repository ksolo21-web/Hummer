#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SMART_ONLINE_VALIDATED="${MSC_SMART_ONLINE_VALIDATED:-false}"
case "$SMART_ONLINE_VALIDATED" in
  true|false) ;;
  *) echo 'MSC_SMART_ONLINE_VALIDATED must be true or false.' >&2; exit 1 ;;
esac
export MSC_SMART_ONLINE_VALIDATED="$SMART_ONLINE_VALIDATED"

# Reconstruct the exact accepted source through 0.15.16 before applying this
# isolated feature layer. This preserves every prior auth, household, scroll,
# family schedule, vote, widget, activity, and truth-gate repair.
bash .msc-build/apply-0.15.16-release-truth.sh

EDITOR_PAYLOAD=".msc-build/0.15.17-interactive-editor.kt.gz.b64"
EDITOR_FILE="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt"
EDITOR_PARTS=(
  .msc-build/0.15.17-interactive-editor.part00.b64
  .msc-build/0.15.17-interactive-editor.part01.b64
  .msc-build/0.15.17-interactive-editor.part02.b64
  .msc-build/0.15.17-interactive-editor.part03.b64
  .msc-build/0.15.17-interactive-editor.part04.b64
)
echo '0a3415dabc876ebe4059ec9ef86b81956636d7def659dd6ff33d8ec098e4ff41  .msc-build/0.15.17-interactive-editor.part00.b64' | sha256sum -c -
echo '3a9915235e481ec48d11140377aaefaaf1c72e21eb7bb8eb762d551191b9ec5d  .msc-build/0.15.17-interactive-editor.part01.b64' | sha256sum -c -
echo 'c1a70bf6fad493094363e4708344d45b7dc1c57c32d58a98906107c83b4d1567  .msc-build/0.15.17-interactive-editor.part02.b64' | sha256sum -c -
echo '6a8449bd2905ed9efb2ab0335412d80474645bccf9c6f48fbeafa29831ba9de8  .msc-build/0.15.17-interactive-editor.part03.b64' | sha256sum -c -
echo '826915070ca1d52c660f2515508f78e70bc36125fcfb326a4264dd9613609cf2  .msc-build/0.15.17-interactive-editor.part04.b64' | sha256sum -c -
cat "${EDITOR_PARTS[@]}" > "$EDITOR_PAYLOAD"
echo '158c53868cd32245b9a3526a603abfbf71b0dce9fd9462a08b2d7761d66d1d26  '"$EDITOR_PAYLOAD" | sha256sum -c -
base64 --decode "$EDITOR_PAYLOAD" | gzip -dc > "$EDITOR_FILE"

# Correct the sole compile fault found by the previous CI run. The imported
# extension belongs to ui.draw, not foundation. Fail closed if source drifts.
python3 - <<'PY'
from pathlib import Path
path = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt')
text = path.read_text(encoding='utf-8')
old = 'import androidx.compose.foundation.clipToBounds'
new = 'import androidx.compose.ui.draw.clipToBounds'
if text.count(old) != 1:
    raise SystemExit(f'expected exactly one obsolete clipToBounds import, found {text.count(old)}')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
PY
echo 'daa2703cecbc94e9206353e0edaf936a675a1a843285ed0d29d9cadd74115bed  '"$EDITOR_FILE" | sha256sum -c -
for marker in \
  'detectTransformGestures' \
  'inverseWorkbookPoint' \
  'clampWorkbookPan' \
  'workbookMilestone' \
  'repository.selectWorkbookColorNumber(pageKey, activity.id, nextNumber)' \
  'Text("Reset view")' \
  'Text("Reset picture")'; do
  grep -Fq "$marker" "$EDITOR_FILE"
done

GENERATOR=".msc-build/generate-0.15.17-premium-paint-by-number.py"
base64 --decode .msc-build/0.15.17-premium-paint-generator.py.gz.b64 | gzip -dc > "$GENERATOR"
echo '34cde02eebe4ac2d7623be30f9141d47d3f2645fb253411948091d3305316997  '"$GENERATOR" | sha256sum -c -
chmod +x "$GENERATOR"
python3 -m py_compile "$GENERATOR"
python3 "$GENERATOR"

python3 - <<'PY'
from __future__ import annotations
import json
from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit(f'{path}: expected exactly one {old!r}, found {text.count(old)}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

replace_once(
    Path('MyStudyCompanion/app/build.gradle.kts'),
    'versionCode = 49',
    'versionCode = 50',
)
replace_once(
    Path('MyStudyCompanion/app/build.gradle.kts'),
    'versionName = "0.15.16-private-alpha-truth-gated-ai-activities"',
    'versionName = "0.15.17-private-alpha-premium-interactive-paint"',
)
replace_once(
    Path('MyStudyCompanion/wear/build.gradle.kts'),
    'versionCode = 360166001',
    'versionCode = 360167001',
)
replace_once(
    Path('MyStudyCompanion/wear/build.gradle.kts'),
    'versionName = "0.15.16-wear-private-alpha-truth-gated-ai-activities"',
    'versionName = "0.15.17-wear-private-alpha-premium-interactive-paint"',
)

updated = 0
for path in Path('MyStudyCompanion/backend').rglob('*'):
    if not path.is_file():
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    if '0.15.16' in text:
        path.write_text(text.replace('0.15.16', '0.15.17'), encoding='utf-8')
        updated += 1
if updated < 1:
    raise SystemExit('no backend 0.15.16 version marker was updated')

manifest_path = Path('MyStudyCompanion/app/src/main/assets/workbook/manifest.json')
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
assert manifest['version'] == 5
assert manifest['colorByNumberVersion'] == 3
assert manifest['colorByNumberQuality'] == 'premium-edge-following-paint-by-number-v5'
assets = manifest['assets']
assert len(assets) == 16
for asset in assets:
    regions = asset['colorRegions']
    assert 28 <= len(regions) <= 100, (asset['id'], len(regions))
    for region in regions:
        assert int(region['pixelCount']) >= 2400, (asset['id'], region['id'])
        assert 1 <= int(region['number']) <= 8
        assert str(region['id']).strip()
    for name in ('color-master.webp', 'color-line.png', 'color-region-mask.png'):
        generated = Path('MyStudyCompanion/app/src/main/assets/workbook', asset['id'], name)
        assert generated.is_file() and generated.stat().st_size >= 1024, generated

preview = Path('MyStudyCompanion/build/reports/workbook/color-by-number-premium-v5-contact-sheet.jpg')
assert preview.is_file() and preview.stat().st_size >= 10_000

checks = {
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt'): [
        'import androidx.compose.ui.draw.clipToBounds',
        'zoom up to 5×',
        'Taps stay aligned with the transformed picture.',
        'lastCelebratedMilestone',
        'Picture complete—great careful work!',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt'): [
        'private val smartOnlineValidated = backendConfig.isConfigured &&',
        'smartOnlineConfigured = smartOnlineValidated',
        'The secure AI service was not verified for this build.',
    ],
    Path('MyStudyCompanion/backend/app/config.py'): ['openai_model: str = "gpt-5.6"'],
    Path('MyStudyCompanion/backend/app/main.py'): [
        'version="0.15.17"',
        '"version": "0.15.17"',
        'serviceVersion="0.15.17"',
    ],
}
for path, markers in checks.items():
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in text:
            raise SystemExit(f'{path}: missing {marker!r}')

for path in Path('MyStudyCompanion/app/src/main').rglob('*'):
    if not path.is_file():
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    if 'OPENAI_API_KEY' in text:
        raise SystemExit(f'{path}: forbidden OPENAI_API_KEY marker in Android source')

print('PASS: 0.15.17 cumulative source, interaction, premium-art, version, and Smart Online truth gates passed.')
PY

echo "Applied My Study Companion 0.15.17 premium interactive paint; Smart Online validated=$SMART_ONLINE_VALIDATED."
