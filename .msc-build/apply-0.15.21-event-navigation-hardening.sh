#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The caller reconstructs the accepted source through 0.15.18 first. Reapply
# 0.15.19 and 0.15.20 exactly, then add only the Events navigation hardening.
bash .msc-build/apply-0.15.20-event-activity-crash-fix.sh

PATCH_FILE=".msc-build/0.15.21-event-navigation-hardening.patch"
echo '23ccacd02011832e8f48573b26223ad6b3de8046599872dbd740c5d761adf9e9  '"$PATCH_FILE" | sha256sum -c -
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
    "versionCode = 53",
    "versionCode = 54",
    "phone version code",
)
replace_once(
    Path("MyStudyCompanion/app/build.gradle.kts"),
    'versionName = "0.15.20-private-alpha-event-activity-crash-fix"',
    'versionName = "0.15.21-private-alpha-event-navigation-hardening"',
    "phone version name",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    "versionCode = 360170001",
    "versionCode = 360171001",
    "Wear version code",
)
replace_once(
    Path("MyStudyCompanion/wear/build.gradle.kts"),
    'versionName = "0.15.20-wear-private-alpha-event-activity-crash-fix"',
    'versionName = "0.15.21-wear-private-alpha-event-navigation-hardening"',
    "Wear version name",
)
PY

python3 - <<'PY'
from pathlib import Path

parent_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/EventNotebooksSection.kt")
parent = parent_path.read_text(encoding="utf-8")
for marker in (
    "if (interactiveBook?.pages?.any { it.key == page.key } == true)",
    "interactivePageKey = page.key",
    "InteractiveWorkbookDialog(",
):
    if marker not in parent:
        raise SystemExit(f"Events screen is missing navigation-hardening marker: {marker}")
if "repository.setActiveWorkbook(interactiveBook?.id.orEmpty(), page.key)" in parent:
    raise SystemExit("Events button still performs synchronous workbook persistence")

editor_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt")
editor = editor_path.read_text(encoding="utf-8")
for marker in (
    "private fun CompanionHubRepository.setActiveWorkbookSafely",
    "catch (_: Exception)",
    "LaunchedEffect(book.id, page.key)",
    "repository.setActiveWorkbookSafely(book.id, page.key)",
    "var expandedActivityId by rememberSaveable(book.id, page.key)",
    "val loadsInteractiveWorkspace = when (activity.kind)",
    'Text("Start activity")',
    'Text("Close activity")',
    "private data class LoadedDifferenceIllustration",
    "private data class LoadedColorIllustration",
    "loadWorkbookAssetSafely",
    'WorkbookActivityUnavailable("The word-search grid is incomplete.")',
    "if (steps.isEmpty())",
):
    if marker not in editor:
        raise SystemExit(f"Interactive editor is missing cumulative marker: {marker}")
for forbidden in (
    "repository.setActiveWorkbook(book.id, book.pages[pageIndex].key)",
    "repository.setActiveWorkbook(book.id, key)",
    "private data class LoadedWorkbookIllustration",
    "rememberWorkbookIllustration(activity)",
):
    if forbidden in editor:
        raise SystemExit(f"Interactive editor still contains unsafe marker: {forbidden}")

checks = {
    Path("MyStudyCompanion/app/build.gradle.kts"): [
        "versionCode = 54",
        'versionName = "0.15.21-private-alpha-event-navigation-hardening"',
    ],
    Path("MyStudyCompanion/wear/build.gradle.kts"): [
        "versionCode = 360171001",
        'versionName = "0.15.21-wear-private-alpha-event-navigation-hardening"',
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
    raise SystemExit("FAIL: 0.15.21 cumulative source gate:\n- " + "\n- ".join(missing))

print("PASS: Events opens before persistence, heavy activities load on demand, and all cumulative markers remain present.")
PY

echo 'Applied My Study Companion 0.15.21 Events navigation hardening.'
