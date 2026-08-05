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
export SMART_ONLINE_VALIDATED

# Reconstruct the complete accepted 0.15.15 feature set first.
bash .msc-build/apply-0.15.15-ai-activity-rebuild.sh

python3 - <<'PY'
from __future__ import annotations

import os
from pathlib import Path

validated = os.environ["SMART_ONLINE_VALIDATED"] == "true"
literal = "true" if validated else "false"

# Give this truth-gated package its own upgrade identity.
app_gradle = Path("MyStudyCompanion/app/build.gradle.kts")
app_text = app_gradle.read_text(encoding="utf-8")
app_replacements = {
    'versionCode = 48': 'versionCode = 49',
    'versionName = "0.15.15-private-alpha-smart-ai-activity-rebuild"':
        'versionName = "0.15.16-private-alpha-truth-gated-ai-activities"',
}
for old, new in app_replacements.items():
    if old not in app_text:
        raise SystemExit(f"expected Android release marker was not found: {old}")
    app_text = app_text.replace(old, new, 1)
app_gradle.write_text(app_text, encoding="utf-8")

wear_gradle = Path("MyStudyCompanion/wear/build.gradle.kts")
wear_text = wear_gradle.read_text(encoding="utf-8")
wear_replacements = {
    'versionCode = 360165001': 'versionCode = 360166001',
    'versionName = "0.15.15-wear-private-alpha-smart-ai-activity-rebuild"':
        'versionName = "0.15.16-wear-private-alpha-truth-gated-ai-activities"',
}
for old, new in wear_replacements.items():
    if old not in wear_text:
        raise SystemExit(f"expected Wear release marker was not found: {old}")
    wear_text = wear_text.replace(old, new, 1)
wear_gradle.write_text(wear_text, encoding="utf-8")

# The app must never advertise Smart Online merely because an HTTPS string was packaged.
repo_path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt")
repo_text = repo_path.read_text(encoding="utf-8")
preference_marker = (
    "    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)\n"
)
if preference_marker not in repo_text:
    raise SystemExit("AiStudyRepository preferences marker was not found")
truth_line = (
    preference_marker
    + f"    private val smartOnlineValidated = backendConfig.isConfigured && {literal}\n"
)
repo_text = repo_text.replace(preference_marker, truth_line, 1)
repo_text = repo_text.replace(
    "smartOnlineConfigured = backendConfig.isConfigured,",
    "smartOnlineConfigured = smartOnlineValidated,",
)
repo_text = repo_text.replace(
    "mode != AiAssistantMode.PRIVATE_OFFLINE && online && backendConfig.isConfigured",
    "mode != AiAssistantMode.PRIVATE_OFFLINE && online && smartOnlineValidated",
)
repo_text = repo_text.replace(
    "!backendConfig.isConfigured -> \"The secure AI service is not configured in this build.\"",
    "!smartOnlineValidated -> \"The secure AI service was not verified for this build.\"",
)
required_repo_markers = (
    f"private val smartOnlineValidated = backendConfig.isConfigured && {literal}",
    "smartOnlineConfigured = smartOnlineValidated",
    "online && smartOnlineValidated",
    "!smartOnlineValidated ->",
)
for marker in required_repo_markers:
    if marker not in repo_text:
        raise SystemExit(f"truth-gated AI marker is missing after patching: {marker}")
if "smartOnlineConfigured = backendConfig.isConfigured" in repo_text:
    raise SystemExit("an endpoint string can still falsely mark Smart Online as configured")
repo_path.write_text(repo_text, encoding="utf-8")

# Move backend and backend tests together to the new service version.
backend = Path("MyStudyCompanion/backend")
updated = 0
for candidate in backend.rglob("*"):
    if not candidate.is_file():
        continue
    try:
        text = candidate.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if "0.15.15" not in text:
        continue
    candidate.write_text(text.replace("0.15.15", "0.15.16"), encoding="utf-8")
    updated += 1
if updated < 2:
    raise SystemExit(f"expected backend version markers in multiple files; updated {updated}")

print(
    "Applied 0.15.16 release truth gate: Smart Online validated="
    + str(validated).lower()
)
PY

python3 - <<'PY'
from pathlib import Path

checks = {
    Path("MyStudyCompanion/app/build.gradle.kts"): [
        "versionCode = 49",
        "0.15.16-private-alpha-truth-gated-ai-activities",
    ],
    Path("MyStudyCompanion/wear/build.gradle.kts"): [
        "versionCode = 360166001",
        "0.15.16-wear-private-alpha-truth-gated-ai-activities",
    ],
    Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ai/AiStudyRepository.kt"): [
        "private val smartOnlineValidated = backendConfig.isConfigured &&",
        "smartOnlineConfigured = smartOnlineValidated",
        "online && smartOnlineValidated",
        "The secure AI service was not verified for this build.",
    ],
    Path("MyStudyCompanion/backend/app/config.py"): [
        'openai_model: str = "gpt-5.6"',
    ],
    Path("MyStudyCompanion/backend/app/main.py"): [
        'version="0.15.16"',
        '"version": "0.15.16"',
        'serviceVersion="0.15.16"',
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
    raise SystemExit("FAIL: 0.15.16 source gate:\n- " + "\n- ".join(missing))
print("PASS: 0.15.16 version, GPT-5.6, and Smart Online truth gates are present.")
PY
