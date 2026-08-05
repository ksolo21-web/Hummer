#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from __future__ import annotations

from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one occurrence, found {count}")
    return text.replace(old, new, 1)

repo_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt")
text = repo_path.read_text(encoding="utf-8")

text = replace_once(
    text,
    'description = "Use Smart Online AI when available and automatically fall back to private offline help.",',
    'description = "Refresh verified official sources online and use secure cloud AI when available, otherwise answer privately on this device.",',
    "Auto mode description",
)
text = replace_once(
    text,
    'description = "Use the secure cloud AI for the strongest conversational answers and official-source research.",',
    'description = "Refresh verified JW.org and Watchtower Online Library sources online, then use secure cloud AI when available or private on-device AI.",',
    "Smart Online mode description",
)
text = replace_once(
    text,
    "private val smartOnlineValidated = backendConfig.isConfigured && false",
    "private val smartOnlineValidated = backendConfig.isConfigured",
    "secure cloud configuration marker",
)
text = replace_once(
    text,
    "        val (geminiStatus, localStatus) = providerRouter.statuses()\n",
    "        val (geminiStatus, localStatus) = providerRouter.statuses()\n        val online = networkStatusMonitor.isOnline()\n",
    "runtime connectivity snapshot",
)
text = replace_once(
    text,
    "            online = networkStatusMonitor.isOnline(),\n",
    "            online = online,\n",
    "runtime online state",
)
text = replace_once(
    text,
    "        mutableAssistantState.value = mutableAssistantState.value.copy(\n            smartOnlineConfigured = smartOnlineValidated,\n        )\n",
    "        mutableAssistantState.value = mutableAssistantState.value.copy(\n            smartOnlineConfigured = smartOnlineValidated || online,\n        )\n",
    "assistant availability state",
)

start_marker = (
    "            val canTrySmartOnline = mode != AiAssistantMode.PRIVATE_OFFLINE "
    "&& online && smartOnlineValidated\n"
)
end_marker = "            saveAssistantMessage(result.answer, result.citations)\n"
start = text.find(start_marker)
if start < 0:
    raise SystemExit("Smart Online decision block start was not found")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("Smart Online decision block end was not found")

new_block = '''            val canUseOnlineAssistant = mode != AiAssistantMode.PRIVATE_OFFLINE && online

            val result = if (canUseOnlineAssistant) {
                if (smartOnlineValidated) {
                    runCatching { smartOnlineAnswer(cleanQuestion, bundle) }
                        .fold(
                            onSuccess = { it },
                            onFailure = { cloudError ->
                                val local = privateOfflineAnswer(cleanQuestion, bundle.passages)
                                local.copy(
                                    answer = local.answer +
                                        "\\n\\n— The secure cloud assistant was temporarily unavailable. Verified official sources were refreshed online, and this answer was generated privately on this device.",
                                    providerLabel = "Verified Online + ${local.providerLabel}",
                                    usedFallback = true,
                                )
                            },
                        )
                } else {
                    val local = privateOfflineAnswer(cleanQuestion, bundle.passages)
                    local.copy(
                        answer = local.answer +
                            "\\n\\n— Verified official sources were refreshed online, and this answer was generated privately on this device.",
                        providerLabel = "Verified Online + ${local.providerLabel}",
                    )
                }
            } else if (mode == AiAssistantMode.SMART_ONLINE) {
                throw SmartOnlineUnavailableException(
                    "The device is offline. Reconnect to refresh verified official sources, or use Private Offline.",
                )
            } else {
                privateOfflineAnswer(cleanQuestion, bundle.passages)
            }

'''
text = text[:start] + new_block + text[end:]

text = replace_once(
    text,
    '"I could not find enough verified JW.org or Watchtower Online Library material in the current source library to answer that safely. Refresh official sources or switch to Smart Online AI."',
    '"I could not find enough verified JW.org or Watchtower Online Library material in the current source library to answer that safely. Refresh official sources and ask again, or make the question more specific to the current Daily Text, weekly reading, or Family Worship material."',
    "empty-source guidance",
)

required = (
    "private val smartOnlineValidated = backendConfig.isConfigured",
    "smartOnlineConfigured = smartOnlineValidated || online",
    "val canUseOnlineAssistant = mode != AiAssistantMode.PRIVATE_OFFLINE && online",
    "Verified official sources were refreshed online",
    'providerLabel = "Verified Online + ${local.providerLabel}"',
)
for marker in required:
    if marker not in text:
        raise SystemExit(f"AiStudyRepository is missing required marker: {marker}")
for forbidden in (
    "backendConfig.isConfigured && false",
    "The secure AI service was not verified for this build.",
    "val canTrySmartOnline =",
):
    if forbidden in text:
        raise SystemExit(f"AiStudyRepository still contains obsolete marker: {forbidden}")
repo_path.write_text(text, encoding="utf-8")

app_gradle = Path("MyStudyCompanion/app/build.gradle.kts")
app_text = app_gradle.read_text(encoding="utf-8")
app_text = replace_once(app_text, "versionCode = 51", "versionCode = 52", "phone version code")
app_text = replace_once(
    app_text,
    'versionName = "0.15.18-private-alpha-premium-widgets"',
    'versionName = "0.15.19-private-alpha-smart-online-hybrid"',
    "phone version name",
)
app_gradle.write_text(app_text, encoding="utf-8")

wear_gradle = Path("MyStudyCompanion/wear/build.gradle.kts")
wear_text = wear_gradle.read_text(encoding="utf-8")
wear_text = replace_once(wear_text, "versionCode = 360168001", "versionCode = 360169001", "Wear version code")
wear_text = replace_once(
    wear_text,
    'versionName = "0.15.18-wear-private-alpha-premium-widgets"',
    'versionName = "0.15.19-wear-private-alpha-smart-online-hybrid"',
    "Wear version name",
)
wear_gradle.write_text(wear_text, encoding="utf-8")

print("Applied 0.15.19 Smart Online verified-source/on-device hybrid fix without changing other feature files.")
PY

python3 - <<'PY'
from pathlib import Path

checks = {
    Path("MyStudyCompanion/app/build.gradle.kts"): [
        "versionCode = 52",
        'versionName = "0.15.19-private-alpha-smart-online-hybrid"',
    ],
    Path("MyStudyCompanion/wear/build.gradle.kts"): [
        "versionCode = 360169001",
        'versionName = "0.15.19-wear-private-alpha-smart-online-hybrid"',
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt"): [
        "private val smartOnlineValidated = backendConfig.isConfigured",
        "smartOnlineConfigured = smartOnlineValidated || online",
        "val canUseOnlineAssistant = mode != AiAssistantMode.PRIVATE_OFFLINE && online",
        "Verified official sources were refreshed online",
        'providerLabel = "Verified Online + ${local.providerLabel}"',
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"): [
        "class CompanionDashboardWidget : GlanceAppWidget()",
        "SizeMode.Responsive",
        "loadWidgetData(context)",
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt"): [
        "detectTransformGestures",
        'Text("Reset view")',
        'Text("Reset picture")',
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
    raise SystemExit("FAIL: 0.15.19 cumulative source gate:\n- " + "\n- ".join(missing))
print("PASS: Smart Online hybrid fix and cumulative 0.15.18 widget/workbook markers are present.")
PY
