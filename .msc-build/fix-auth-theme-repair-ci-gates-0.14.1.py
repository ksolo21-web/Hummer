#!/usr/bin/env python3
from pathlib import Path

current_marker = 'msc-web-v0144-auth-theme-repair'
# Split literals keep apply-auth-theme-repair from rewriting this normalizer's
# own stale-marker list while it updates reconstructed workflow text.
legacy_markers = (
    'msc-web-v0140-' + 'interactive-workbooks',
    'msc-web-v0141-' + 'unified-study-reader',
    'msc-web-v0142-' + 'complete-reader',
    'msc-web-v0143-' + 'theme-gallery',
)

# The expanded-gallery overlay runs before the approved-theme repair and adds
# positive checks for themes that are deliberately removed later. Normalize
# those checks in the validator before it edits the final build runner.
gate = Path('.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py')
gate_source = gate.read_text(encoding='utf-8')
gate_replacements = {
    "grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js":
        "grep -Fq 'Owl' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js":
        "grep -Fq 'Sakura Tiger' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'BIBLE_MAP(\"Bible Map\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt":
        "grep -Fq 'SAKURA_TIGER(\"Sakura Tiger\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt",
    '    "BIBLE_MAP",\n': '    "SAKURA_TIGER",\n',
}
for old, new in gate_replacements.items():
    gate_source = gate_source.replace(old, new)

# Any cache marker injected by reconstructed layers must converge on the one
# repaired PWA identity. The split legacy definitions in the validator remain
# untouched because the complete literal does not occur in their source text.
for marker in legacy_markers:
    gate_source = gate_source.replace(marker, current_marker)

gate.write_text(gate_source, encoding='utf-8')

# Reconstruction can also restore an older service-worker gate directly in the
# shared build runner. Normalize it now; the validator then appends and checks
# the complete auth/theme/live-stack release contract.
runner = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = runner.read_text(encoding='utf-8')
for marker in legacy_markers:
    source = source.replace(marker, current_marker)
source = source.replace(
    "grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'Owl' MyStudyCompanionWeb/appearance.js",
)
source = source.replace(
    "grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'Sakura Tiger' MyStudyCompanionWeb/appearance.js",
)
source = source.replace(
    "grep -Fq 'BIBLE_MAP(\"Bible Map\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt",
    "grep -Fq 'SAKURA_TIGER(\"Sakura Tiger\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt",
)
runner.write_text(source, encoding='utf-8')

for marker in legacy_markers:
    if marker in gate_source or marker in source:
        raise SystemExit(f'Stale service-worker marker remains after auth/theme normalization: {marker}')
for rejected_positive_check in (
    "grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js",
    "grep -Fq 'BIBLE_MAP(\"Bible Map\"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt",
):
    if rejected_positive_check in gate_source or rejected_positive_check in source:
        raise SystemExit(f'Rejected-theme positive CI check remains: {rejected_positive_check}')

print('Normalized final build gates for the restored Google login, approved themes, unified reader, and production live stack.')
