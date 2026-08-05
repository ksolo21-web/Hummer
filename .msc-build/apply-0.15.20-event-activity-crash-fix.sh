#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Preserve the full accepted 0.15.19 Smart Online, OAuth, cloud, widget,
# workbook, household, schedule, voting, and persistence lineage.
bash .msc-build/apply-0.15.19-smart-online-hybrid-fix.sh

PATCH_FILE=".msc-build/0.15.20-event-activity-crash.patch"
echo '6bce3ab6fae348bbefe23cbe94e99c43119ad97985e4b258b881ece0f4b68bfc  '"$PATCH_FILE" | sha256sum -c -
patch -p1 --batch --forward < "$PATCH_FILE"

python3 - <<'PY'
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one occurrence, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    Path("MyStudyCompanion/app/build.gradle.kts"),
    "versionCode = 52",
    "versionCode = 53",
    "phone version code",
)
replace_once(
    Path("MyStudyCompanion/app/build.gradle.kts"),
    'versionName = "0.15.19-private-alpha-smart-online-hybrid"',
    'versionName = "0.15.20-private-alpha-event-activity-crash-fix"',
    "phone version name",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    "versionCode = 360169001",
    "versionCode = 360170001",
    "Wear version code",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    'versionName = "0.15.19-wear-private-alpha-smart-online-hybrid"',
    'versionName = "0.15.20-wear-private-alpha-event-activity-crash-fix"',
    "Wear version name",
)
PY

python3 - <<'PY'
from pathlib import Path

editor_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt")
editor = editor_path.read_text(encoding="utf-8")
required_editor = (
    "private data class LoadedDifferenceIllustration",
    "private data class LoadedColorIllustration",
    "loadWorkbookAssetSafely",
    "loadTransparentLineBitmap",
    "source.recycle()",
    "rememberDifferenceIllustration(activity)",
    "rememberColorIllustration(activity)",
    "rememberWorkbookIllustrationAsset(activity)",
    'WorkbookActivityUnavailable("The crossword layout is incomplete.")',
    'WorkbookActivityUnavailable("The word-search grid is incomplete.")',
    "row.getOrNull(puzzle.columns - 1) != null",
    "val firstCell = cells.firstOrNull()",
    "puzzle.placements.chunked(3)",
    "if (steps.isEmpty())",
    "The comparison pictures could not be loaded.",
    "The paint-by-number picture could not be loaded.",
    "The guided drawing asset could not be loaded.",
)
for marker in required_editor:
    if marker not in editor:
        raise SystemExit(f"Interactive editor is missing crash-fix marker: {marker}")

for forbidden in (
    "private data class LoadedWorkbookIllustration",
    "rememberWorkbookIllustration(activity)",
    "cells.first() == first",
    "puzzle.placements.first { it.word == word }",
):
    if forbidden in editor:
        raise SystemExit(f"Interactive editor still contains unsafe marker: {forbidden}")

checks = {
    Path("MyStudyCompanion/app/build.gradle.kts"): [
        "versionCode = 53",
        'versionName = "0.15.20-private-alpha-event-activity-crash-fix"',
    ],
    Path("MyStudyCompanion/wear/build.gradle.kts"): [
        "versionCode = 360170001",
        'versionName = "0.15.20-wear-private-alpha-event-activity-crash-fix"',
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt"): [
        "private val smartOnlineValidated = backendConfig.isConfigured",
        "smartOnlineConfigured = smartOnlineValidated || online",
        "Verified official sources were refreshed online",
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"): [
        "class CompanionDashboardWidget : GlanceAppWidget()",
        "SizeMode.Responsive",
        "loadWidgetData(context)",
    ],
}
missing = []
for path, markers in checks.items():
    if not path.is_file():
        missing.append(f"{path}: missing file")
        continue
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            missing.append(f"{path}: missing {marker!r}")
if missing:
    raise SystemExit("FAIL: 0.15.20 cumulative source gate:\n- " + "\n- ".join(missing))

print("PASS: event activity crash guards, reduced bitmap loading, and all cumulative 0.15.19 markers are present.")
PY

echo 'Applied My Study Companion 0.15.20 event interactive-activity crash fix.'
