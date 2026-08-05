#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
cat .msc-build/0.15.15-patch/part-*.patch > "$PATCH_FILE"
echo '1440e6a689afbf006f831944d0e34a310999e063a4f8d36dc0589266ef4aa2a6  '"$PATCH_FILE" | sha256sum -c -
patch -p1 --batch --forward < "$PATCH_FILE"

python3 - <<'PY'
from pathlib import Path
path = Path('MyStudyCompanion/backend/app/main.py')
text = path.read_text(encoding='utf-8')
if text.count('0.14.1') < 3:
    raise SystemExit('expected backend 0.14.1 version markers were not found')
path.write_text(text.replace('0.14.1', '0.15.15'), encoding='utf-8')
PY

python3 MyStudyCompanion/tools/generate_curated_color_by_number.py
python3 MyStudyCompanion/tools/verify_curated_workbook.py

python3 - <<'PY'
from __future__ import annotations

import json
from pathlib import Path

checks = {
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt'): [
        'enum class AiAssistantMode',
        'backendApi.askStudyAssistant(request)',
        'recentAiMessages(13)',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt'): [
        'AiAssistantMode.entries.forEach',
        'selected = assistant.mode == mode',
        'assistant.smartOnlineConfigured',
        'Continue the conversation',
    ],
    Path('MyStudyCompanion/backend/app/services/openai_study_service.py'): [
        '"store": False',
        '"allowed_domains": ["jw.org", "wol.jw.org"]',
        '"type": "json_schema"',
        'The AI answer was generic or did not address the question',
    ],
    Path('MyStudyCompanion/backend/app/config.py'): [
        'openai_model: str = "gpt-5.4"',
    ],
    Path('MyStudyCompanion/backend/app/main.py'): [
        'version="0.15.15"',
        '"version": "0.15.15"',
        'serviceVersion="0.15.15"',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt'): [
        'Tap a difference in either picture.',
        'The answer locations are never listed below the pictures.',
        'Picture complete—great careful work!',
    ],
    Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/WorkbookIllustrationCatalog.kt'): [
        'colorRegions',
        'pixelCount >= 900',
    ],
    Path('MyStudyCompanion/app/build.gradle.kts'): [
        'versionCode = 48',
        '0.15.15-private-alpha-smart-ai-activity-rebuild',
    ],
    Path('MyStudyCompanion/wear/build.gradle.kts'): [
        'versionCode = 360165001',
    ],
}

missing: list[str] = []
for path, markers in checks.items():
    if not path.is_file():
        missing.append(f'{path}: file is missing')
        continue
    text = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in text:
            missing.append(f'{path}: missing marker {marker!r}')

android_source = Path('MyStudyCompanion/app/src/main')
for path in android_source.rglob('*'):
    if path.is_file():
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        if 'OPENAI_API_KEY' in text:
            missing.append(f'{path}: forbidden OPENAI_API_KEY marker is packaged in Android source')

manifest_path = Path('MyStudyCompanion/app/src/main/assets/workbook/manifest.json')
try:
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
except Exception as error:
    missing.append(f'{manifest_path}: cannot parse manifest: {error}')
else:
    if manifest.get('version') != 4:
        missing.append(f'{manifest_path}: expected version 4, found {manifest.get("version")!r}')
    if manifest.get('colorByNumberVersion') != 2:
        missing.append(
            f'{manifest_path}: expected colorByNumberVersion 2, '
            f'found {manifest.get("colorByNumberVersion")!r}'
        )
    assets = manifest.get('assets', [])
    if len(assets) != 16:
        missing.append(f'{manifest_path}: expected 16 assets, found {len(assets)}')
    for item in assets:
        regions = item.get('colorRegions', [])
        if not 8 <= len(regions) <= 24:
            missing.append(
                f'{manifest_path}: asset {item.get("id")!r} has {len(regions)} regions; expected 8-24'
            )
        for name in ('color-master.webp', 'color-line.png', 'color-region-mask.png'):
            asset_path = Path('MyStudyCompanion/app/src/main/assets/workbook', str(item.get('id', '')), name)
            if not asset_path.is_file():
                missing.append(f'{asset_path}: curated activity asset is missing')

if missing:
    raise SystemExit('FAIL: 0.15.15 source gate found the following problems:\n- ' + '\n- '.join(missing))

print(f'PASS: all {sum(len(markers) for markers in checks.values())} source markers and curated activity assets are present.')
PY

echo 'Applied My Study Companion 0.15.15 smart AI and activity rebuild.'
