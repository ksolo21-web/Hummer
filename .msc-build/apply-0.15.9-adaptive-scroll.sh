#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import re

root = Path('MyStudyCompanion')
ui = root / 'app/src/main/java/com/mystudycompanion/app/ui'
family = ui / 'FamilyHubScreen.kt'
household = ui / 'HouseholdScreen.kt'
app_gradle = root / 'app/build.gradle.kts'
wear_gradle = root / 'wear/build.gradle.kts'


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'{label} target not found in {path}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


def set_identity(path: Path, code: int, name: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    text, code_count = re.subn(
        r'versionCode\s*=\s*\d+',
        f'versionCode = {code}',
        text,
        count=1,
    )
    text, name_count = re.subn(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{name}"',
        text,
        count=1,
    )
    if code_count != 1 or name_count != 1:
        raise SystemExit(
            f'{label} identity target mismatch in {path}: '
            f'versionCode={code_count}, versionName={name_count}'
        )
    path.write_text(text, encoding='utf-8')

# Family Hub: one real scroll owner for the complete Profiles & household page.
replace_exact(
    family,
    'import androidx.compose.foundation.layout.Column\n',
    'import androidx.compose.foundation.layout.Column\n'
    'import androidx.compose.foundation.layout.Spacer\n'
    'import androidx.compose.foundation.layout.fillMaxWidth\n'
    'import androidx.compose.foundation.layout.height\n'
    'import androidx.compose.foundation.layout.imePadding\n'
    'import androidx.compose.foundation.rememberScrollState\n'
    'import androidx.compose.foundation.verticalScroll\n',
    'Family Hub adaptive-scroll imports',
)
replace_exact(
    family,
    'import androidx.compose.runtime.setValue\n',
    'import androidx.compose.runtime.setValue\n'
    'import androidx.compose.runtime.withFrameNanos\n',
    'Family Hub frame synchronization import',
)
replace_exact(
    family,
    '    val companionState by companionRepository.state.collectAsStateWithLifecycle()\n'
    '    val initialIndex = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }.coerceAtLeast(0)\n'
    '    var sectionIndex by rememberSaveable(initialSection) { mutableIntStateOf(initialIndex) }\n'
    '    var showAddProfile by rememberSaveable { mutableStateOf(false) }\n',
    '    val companionState by companionRepository.state.collectAsStateWithLifecycle()\n'
    '    val organizerState by organizerRepository.state.collectAsStateWithLifecycle()\n'
    '    val initialIndex = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }.coerceAtLeast(0)\n'
    '    var sectionIndex by rememberSaveable(initialSection) { mutableIntStateOf(initialIndex) }\n'
    '    var showAddProfile by rememberSaveable { mutableStateOf(false) }\n'
    '    val householdScrollState = rememberScrollState()\n',
    'Family Hub scroll and invitation state',
)
replace_exact(
    family,
    '    LaunchedEffect(initialSection) {\n'
    '        val requested = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }\n'
    '        if (requested >= 0) sectionIndex = requested\n'
    '    }\n\n'
    '    Column(modifier.fillMaxSize()) {\n',
    '    LaunchedEffect(initialSection) {\n'
    '        val requested = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }\n'
    '        if (requested >= 0) sectionIndex = requested\n'
    '    }\n'
    '    LaunchedEffect(\n'
    '        sectionIndex,\n'
    '        organizerState.invitationCode,\n'
    '        layoutSpec.widthClass,\n'
    '        layoutSpec.isTabletop,\n'
    '        layoutSpec.hasSeparatingVerticalHinge,\n'
    '    ) {\n'
    '        if (\n'
    '            FamilyHubSection.entries[sectionIndex] == FamilyHubSection.HOUSEHOLD &&\n'
    '            organizerState.invitationCode != null\n'
    '        ) {\n'
    '            // Wait for the invitation card and any fold/unfold remeasure to finish,\n'
    '            // then reveal the complete code instead of leaving it below the viewport.\n'
    '            withFrameNanos { }\n'
    '            withFrameNanos { }\n'
    '            householdScrollState.animateScrollTo(householdScrollState.maxValue)\n'
    '        }\n'
    '    }\n\n'
    '    Column(modifier.fillMaxSize()) {\n',
    'Family Hub live-size and invite reveal effect',
)
replace_exact(
    family,
    '            FamilyHubSection.HOUSEHOLD -> Column(Modifier.weight(1f)) {\n'
    '                ProfileSwitcher(\n'
    '                    state = companionState,\n'
    '                    onSelected = companionRepository::switchProfile,\n'
    '                    onAdd = { showAddProfile = true },\n'
    '                    modifier = Modifier.padding(\n'
    '                        horizontal = layoutSpec.outerPaddingDp.dp,\n'
    '                        vertical = 12.dp,\n'
    '                    ),\n'
    '                )\n'
    '                HouseholdScreen(\n'
    '                    account = account,\n'
    '                    organizerRepository = organizerRepository,\n'
    '                    layoutSpec = layoutSpec,\n'
    '                    modifier = Modifier.weight(1f),\n'
    '                )\n'
    '            }\n',
    '            FamilyHubSection.HOUSEHOLD -> Column(\n'
    '                Modifier\n'
    '                    .weight(1f)\n'
    '                    .fillMaxWidth()\n'
    '                    .verticalScroll(householdScrollState)\n'
    '                    .imePadding(),\n'
    '            ) {\n'
    '                ProfileSwitcher(\n'
    '                    state = companionState,\n'
    '                    onSelected = companionRepository::switchProfile,\n'
    '                    onAdd = { showAddProfile = true },\n'
    '                    modifier = Modifier.padding(\n'
    '                        horizontal = layoutSpec.outerPaddingDp.dp,\n'
    '                        vertical = 12.dp,\n'
    '                    ),\n'
    '                )\n'
    '                HouseholdScreen(\n'
    '                    account = account,\n'
    '                    organizerRepository = organizerRepository,\n'
    '                    layoutSpec = layoutSpec,\n'
    '                    modifier = Modifier.fillMaxWidth(),\n'
    '                )\n'
    '                Spacer(Modifier.height((layoutSpec.outerPaddingDp + 12).dp))\n'
    '            }\n',
    'Family Hub household scroll container',
)

# Household content must measure to its natural height inside the parent scroll owner.
replace_exact(
    household,
    'import androidx.compose.foundation.layout.fillMaxSize\n',
    '',
    'Household remove fixed-height import',
)
replace_exact(
    household,
    '    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {\n'
    '        Column(\n'
    '            modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(layoutSpec.outerPaddingDp.dp),\n',
    '    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {\n'
    '        Column(\n'
    '            modifier = Modifier\n'
    '                .fillMaxWidth()\n'
    '                .widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)\n'
    '                .padding(horizontal = layoutSpec.outerPaddingDp.dp)\n'
    '                .padding(top = layoutSpec.outerPaddingDp.dp, bottom = 24.dp),\n',
    'Household natural-height adaptive width',
)

# New canonical update identity. Match the reconstructed Gradle formatting rather
# than assuming one literal value or whitespace layout.
set_identity(
    app_gradle,
    42,
    '0.15.9-private-alpha-adaptive-scroll',
    'phone',
)
set_identity(
    wear_gradle,
    360159001,
    '0.15.9-wear-private-alpha-adaptive-scroll',
    'Wear',
)

# Static contract: the screen owns exactly one vertical scroll region and does not
# constrain HouseholdScreen with a second weight/fillMaxSize combination.
family_text = family.read_text(encoding='utf-8')
household_text = household.read_text(encoding='utf-8')
assert '.verticalScroll(householdScrollState)' in family_text
assert 'modifier = Modifier.weight(1f),' not in family_text[family_text.index('FamilyHubSection.HOUSEHOLD'):]
assert 'Box(modifier.fillMaxSize()' not in household_text
assert 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' in household_text
assert 'organizerState.invitationCode' in family_text
assert 'withFrameNanos' in family_text

print('Applied My Study Companion 0.15.9 adaptive scrolling and live fold/window resize repair.')
PY
