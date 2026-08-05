#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path

path = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/StudyCompanionWidgets.kt")
text = path.read_text(encoding="utf-8")
text = text.replace(
    "private const val FLAG_IMMUTABLE_UPDATE =",
    "private val FLAG_IMMUTABLE_UPDATE =",
    1,
)
text = text.replace(
    "private abstract class SummaryWidgetProvider(",
    "abstract class SummaryWidgetProvider(",
    1,
)
path.write_text(text, encoding="utf-8")
PY
