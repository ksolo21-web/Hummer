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

AI_REPOSITORY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt"
AI_SCREEN="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AiStudyScreen.kt"
BACKEND_AI="MyStudyCompanion/backend/app/services/openai_study_service.py"
BACKEND_CONFIG="MyStudyCompanion/backend/app/config.py"
BACKEND_MAIN="MyStudyCompanion/backend/app/main.py"
EDITOR="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt"
CATALOG="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/WorkbookIllustrationCatalog.kt"
MANIFEST="MyStudyCompanion/app/src/main/assets/workbook/manifest.json"

grep -Fq 'enum class AiAssistantMode' "$AI_REPOSITORY"
grep -Fq 'backendApi.askStudyAssistant(request)' "$AI_REPOSITORY"
grep -Fq 'recentAiMessages(13)' "$AI_REPOSITORY"
grep -Fq 'AiAssistantMode.SMART_ONLINE' "$AI_SCREEN"
grep -Fq 'Continue the conversation' "$AI_SCREEN"
grep -Fq '"store": False' "$BACKEND_AI"
grep -Fq '"allowed_domains": ["jw.org", "wol.jw.org"]' "$BACKEND_AI"
grep -Fq '"type": "json_schema"' "$BACKEND_AI"
grep -Fq 'The AI answer was generic or did not address the question' "$BACKEND_AI"
grep -Fq 'openai_model: str = "gpt-5.4"' "$BACKEND_CONFIG"
grep -Fq 'version="0.15.15"' "$BACKEND_MAIN"
grep -Fq '"version": "0.15.15"' "$BACKEND_MAIN"
grep -Fq 'serviceVersion="0.15.15"' "$BACKEND_MAIN"
! grep -R -Fq 'OPENAI_API_KEY' MyStudyCompanion/app/src/main

grep -Fq 'Tap a difference in either picture.' "$EDITOR"
grep -Fq 'The answer locations are never listed below the pictures.' "$EDITOR"
grep -Fq 'Picture complete—great careful work!' "$EDITOR"
grep -Fq 'colorRegions' "$CATALOG"
grep -Fq 'pixelCount >= 900' "$CATALOG"
python3 - "$MANIFEST" <<'PY'
import json, sys
from pathlib import Path
manifest = json.loads(Path(sys.argv[1]).read_text())
assert manifest['version'] == 4
assert manifest['colorByNumberVersion'] == 2
assert len(manifest['assets']) == 16
assert all(8 <= len(item['colorRegions']) <= 24 for item in manifest['assets'])
assert all(Path('MyStudyCompanion/app/src/main/assets/workbook', item['id'], name).is_file()
           for item in manifest['assets']
           for name in ('color-master.webp', 'color-line.png', 'color-region-mask.png'))
PY

grep -Fq 'versionCode = 48' MyStudyCompanion/app/build.gradle.kts
grep -Fq '0.15.15-private-alpha-smart-ai-activity-rebuild' MyStudyCompanion/app/build.gradle.kts
grep -Fq 'versionCode = 360165001' MyStudyCompanion/wear/build.gradle.kts

echo 'Applied My Study Companion 0.15.15 smart AI and activity rebuild.'
