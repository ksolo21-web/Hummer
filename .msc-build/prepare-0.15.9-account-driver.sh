#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/build-0.15.9-adaptive-scroll.sh')
text = path.read_text(encoding='utf-8')

# Add the Account overlay immediately after the already-proven Family Hub overlay.
# This is intentionally idempotent because the workflow prepares a temporary driver
# more than once during troubleshooting and verification.
if 'apply-0.15.9-account-scroll.sh' not in text:
    needle = "    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\\n',"
    replacement = (
        "    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\\n'\n"
        "    'bash .msc-build/apply-0.15.9-account-scroll.sh\\n',"
    )
    if text.count(needle) != 1:
        raise SystemExit(
            'Could not locate the single adaptive overlay line while adding AccountScreen.'
        )
    text = text.replace(needle, replacement, 1)

# Insert Account APK-source gates next to the Household adaptive-width gate without
# depending on quote escaping or surrounding line formatting.
if "grep -Fq '.verticalScroll(scrollState)'" not in text:
    lines = text.splitlines()
    target_index = next(
        (
            index
            for index, line in enumerate(lines)
            if 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' in line
            and 'HouseholdScreen.kt' in line
        ),
        None,
    )
    if target_index is None:
        raise SystemExit('Could not locate the Household adaptive-width source gate.')
    account_gates = [
        "grep -Fq '.verticalScroll(scrollState)' \\\"$UI/AccountScreen.kt\\\"",
        "grep -Fq '.imePadding()' \\\"$UI/AccountScreen.kt\\\"",
        "grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 760).dp)' \\\"$UI/AccountScreen.kt\\\"",
    ]
    lines[target_index + 1:target_index + 1] = account_gates
    text = '\n'.join(lines) + '\n'

# Keep the verification report explicit, but do not make report wording a build
# dependency. Functional source and APK gates above remain the release blockers.
if 'PASS: Account uses a bounded adaptive scroll owner and IME-safe padding.' not in text:
    marker = "    'PASS: Profiles & household uses one live constraint-aware vertical scroll owner.\\n'"
    if marker in text:
        text = text.replace(
            marker,
            marker
            + "\n    'PASS: Account uses a bounded adaptive scroll owner and IME-safe padding.\\n'"
            + "\n    'PASS: the ordinary-screen audit has no unresolved static full-page findings.\\n'",
            1,
        )

path.write_text(text, encoding='utf-8')

final = path.read_text(encoding='utf-8')
assert 'apply-0.15.9-account-scroll.sh' in final
assert "grep -Fq '.verticalScroll(scrollState)'" in final
assert 'AccountScreen.kt' in final
print('Included AccountScreen adaptive scrolling in the 0.15.9 release driver.')
PY
