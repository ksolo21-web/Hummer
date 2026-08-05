#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SMART_ONLINE_VALIDATED="${MSC_SMART_ONLINE_VALIDATED:-false}"
case "$SMART_ONLINE_VALIDATED" in
  true|false) ;;
  *)
    echo 'MSC_SMART_ONLINE_VALIDATED must be true or false.' >&2
    exit 1
    ;;
esac
export MSC_SMART_ONLINE_VALIDATED="$SMART_ONLINE_VALIDATED"

# Start from the complete, verified cumulative source through 0.15.16.
# This preserves the Google sign-in, household, scrolling, voting, widget,
# family schedule, AI activity, and Smart Online truth-gate repairs.
bash .msc-build/apply-0.15.16-release-truth.sh

PATCH_FILE=".msc-build/0.15.17-interactive-paint.patch"
echo '907ebe0f158700c46b6d616cd66cef94f473c5f188178bd787c8c73ffa9b343a  '"$PATCH_FILE" | sha256sum -c -
patch -p1 --batch --forward < "$PATCH_FILE"

GENERATOR=".msc-build/generate-0.15.17-premium-paint-by-number.py"
base64 --decode .msc-build/0.15.17-premium-paint-generator.py.gz.b64 | gzip -dc > "$GENERATOR"
echo '34cde02eebe4ac2d7623be30f9141d47d3f2645fb253411948091d3305316997  '"$GENERATOR" | sha256sum -c -
chmod +x "$GENERATOR"
python3 -m py_compile "$GENERATOR"
python3 "$GENERATOR"

python3 - <<'PY'
from __future__ import annotations

from pathlib import Path

app_gradle = Path('MyStudyCompanion/app/build.gradle.kts')
app_text = app_gradle.read_text(encoding='utf-8')
app_replacements = {
    'versionCode = 49': 'versionCode = 50',
    'versionName = "0.15.16-private-alpha-truth-gated-ai-activities"':
        'versionName = "0.15.17-private-alpha-premium-interactive-paint"',
}
for old, new in app_replacements.items():
    if old not in app_text:
        raise SystemExit(f'expected Android 0.15.16 marker was not found: {old}')
    app_text = app_text.replace(old, new, 1)
app_gradle.write_text(app_text, encoding='utf-8')

wear_gradle = Path('MyStudyCompanion/wear/build.gradle.kts')
wear_text = wear_gradle.read_text(encoding='utf-8')
wear_replacements = {
    'versionCode = 360166001': 'versionCode = 360167001',
    'versionName = "0.15.16-wear-private-alpha-truth-gated-ai-activities"':
        'versionName = "0.15.17-wear-private-alpha-premium-interactive-paint"',
}
for old, new in wear_replacements.items():
    if old not in wear_text:
        raise SystemExit(f'expected Wear 0.15.16 marker was not found: {old}')
    wear_text = wear_text.replace(old, new, 1)
wear_gradle.write_text(wear_text, encoding='utf-8')

backend = Path('MyStudyCompanion/backend')
updated = 0
for candidate in backend.rglob('*'):
    if not candidate.is_file():
        continue
    try:
        text = candidate.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    if '0.15.16' not in text:
        continue
    candidate.write_text(text.replace('0.15.16', '0.15.17'), encoding='utf-8')
    updated += 1
if updated < 1:
    raise SystemExit(f'expected at least one backend 0.15.16 marker; updated {updated}')
PY

python3 - <<'PY'
from __future__ import annotations

import json
from pathlib import Path

checks = {
    Path('MyStudyCompanion/app/build.gradle.kts'): [
        'versionCode = 50',
        '0.15.17-private-alpha-premium-interactive-paint',
    ],
    Path('MyStudyCompanion/wear/build.gradle.kts'): [
        'versionCode = 360167001',
        '0.15.17-wear-private-alpha-premium-interactive-paint',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt'): [
        'detectTransformGestures',
        'inverseWorkbookPoint',
        'clampWorkbookPan',
        'zoom up to 5×',
        'Taps stay aligned with the transformed picture.',
        'workbookMilestone',
        'lastCelebratedMilestone',
        'repository.selectWorkbookColorNumber(pageKey, activity.id, nextNumber)',
        'Text("Reset view")',
        'Text("Reset picture")',
        'Picture complete—great careful work!',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt'): [
        'private val smartOnlineValidated = backendConfig.isConfigured &&',
        'smartOnlineConfigured = smartOnlineValidated',
        'The secure AI service was not verified for this build.',
    ],
    Path('MyStudyCompanion/backend/app/config.py'): [
        'openai_model: str = "gpt-5.6"',
    ],
    Path('MyStudyCompanion/backend/app/main.py'): [
        'version="0.15.17"',
        '"version": "0.15.17"',
        'serviceVersion="0.15.17"',
    ],
    Path('.msc-build/generate-0.15.17-premium-paint-by-number.py'): [
        'MIN_REGIONS, MAX_REGIONS = 28, 100',
        'MIN_FULL_REGION_PIXELS = MIN_HALF_REGION_PIXELS * 4',
        'natural edge-following connected paint shapes',
        'zoomable tap-fill with progress hints undo redo reset and celebration',
    ],
}

missing: list[str] = []
for path, markers in checks.items():
    if not path.is_file():
        missing.append(f'{path}: missing file')
        continue
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in text:
            missing.append(f'{path}: missing marker {marker!r}')

manifest_path = Path('MyStudyCompanion/app/src/main/assets/workbook/manifest.json')
try:
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
except Exception as error:
    missing.append(f'{manifest_path}: cannot parse manifest: {error}')
else:
    if manifest.get('version') != 5:
        missing.append(f'{manifest_path}: expected version 5, found {manifest.get("version")!r}')
    if manifest.get('colorByNumberVersion') != 3:
        missing.append(
            f'{manifest_path}: expected colorByNumberVersion 3, '
            f'found {manifest.get("colorByNumberVersion")!r}'
        )
    if manifest.get('colorByNumberQuality') != 'premium-edge-following-paint-by-number-v5':
        missing.append(f'{manifest_path}: premium quality marker is missing')
    design = manifest.get('colorByNumberDesign', {})
    if design.get('regionRange') != '28-100':
        missing.append(f'{manifest_path}: expected the 28-100 region range')
    if int(design.get('minimumRegionPixels', 0)) != 2400:
        missing.append(f'{manifest_path}: expected a 2,400-pixel minimum region floor')
    assets = manifest.get('assets', [])
    if len(assets) != 16:
        missing.append(f'{manifest_path}: expected 16 assets, found {len(assets)}')
    for item in assets:
        asset_id = str(item.get('id', ''))
        regions = item.get('colorRegions', [])
        if not 28 <= len(regions) <= 100:
            missing.append(f'{manifest_path}: {asset_id!r} has {len(regions)} regions; expected 28-100')
        numbers_used = set(item.get('colorNumbersUsed', []))
        if not numbers_used or not numbers_used.issubset(set(range(1, 9))):
            missing.append(f'{manifest_path}: {asset_id!r} has an invalid palette')
        for region in regions:
            if int(region.get('pixelCount', 0)) < 2400:
                missing.append(
                    f'{manifest_path}: {asset_id!r} region {region.get("id")!r} is below 2,400 pixels'
                )
            if int(region.get('number', 0)) not in range(1, 9):
                missing.append(
                    f'{manifest_path}: {asset_id!r} region {region.get("id")!r} has an invalid number'
                )
            if not str(region.get('id', '')).strip():
                missing.append(f'{manifest_path}: {asset_id!r} contains a blank region id')
        for name in ('color-master.webp', 'color-line.png', 'color-region-mask.png'):
            asset_path = Path('MyStudyCompanion/app/src/main/assets/workbook', asset_id, name)
            if not asset_path.is_file() or asset_path.stat().st_size < 1024:
                missing.append(f'{asset_path}: generated premium asset is missing or empty')

preview = Path('MyStudyCompanion/build/reports/workbook/color-by-number-premium-v5-contact-sheet.jpg')
if not preview.is_file() or preview.stat().st_size < 10_000:
    missing.append(f'{preview}: premium visual review sheet is missing or empty')

android_source = Path('MyStudyCompanion/app/src/main')
for path in android_source.rglob('*'):
    if not path.is_file():
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    if 'OPENAI_API_KEY' in text:
        missing.append(f'{path}: forbidden OPENAI_API_KEY marker is packaged in Android source')

if missing:
    raise SystemExit('FAIL: 0.15.17 cumulative source gate:\n- ' + '\n- '.join(missing))

print('PASS: 0.15.17 cumulative source, interaction, premium-art, version, and Smart Online truth gates passed.')
PY

echo "Applied My Study Companion 0.15.17 premium interactive paint; Smart Online validated=$SMART_ONLINE_VALIDATED."
