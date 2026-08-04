#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/AccountScreen.kt')
text = path.read_text(encoding='utf-8')


def replace_exact(old: str, new: str, label: str) -> None:
    global text
    if text.count(old) != 1:
        raise SystemExit(f'{label} expected exactly one target, found {text.count(old)}')
    text = text.replace(old, new, 1)

replace_exact(
    'import androidx.compose.foundation.layout.height\n',
    'import androidx.compose.foundation.layout.height\n'
    'import androidx.compose.foundation.layout.imePadding\n',
    'Account IME-padding import',
)
replace_exact(
    'import androidx.compose.foundation.layout.widthIn\n',
    'import androidx.compose.foundation.layout.widthIn\n'
    'import androidx.compose.foundation.rememberScrollState\n'
    'import androidx.compose.foundation.verticalScroll\n',
    'Account scroll imports',
)
replace_exact(
    '    val capabilities = authRepository.capabilities\n\n'
    '    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {\n',
    '    val capabilities = authRepository.capabilities\n'
    '    val scrollState = rememberScrollState()\n\n'
    '    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {\n',
    'Account scroll state',
)
replace_exact(
    '            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(layoutSpec.outerPaddingDp.dp),\n',
    '            modifier = Modifier\n'
    '                .fillMaxWidth()\n'
    '                .widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 760).dp)\n'
    '                .verticalScroll(scrollState)\n'
    '                .imePadding()\n'
    '                .padding(layoutSpec.outerPaddingDp.dp),\n',
    'Account adaptive scrolling column',
)

path.write_text(text, encoding='utf-8')

final = path.read_text(encoding='utf-8')
assert 'val scrollState = rememberScrollState()' in final
assert '.verticalScroll(scrollState)' in final
assert '.imePadding()' in final
assert 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 760).dp)' in final
print('Applied adaptive scrolling to AccountScreen.')
PY
