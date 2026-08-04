#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

ui_root = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui")
family_path = ui_root / "FamilyHubScreen.kt"
household_path = ui_root / "HouseholdScreen.kt"

family = family_path.read_text(encoding="utf-8")

imports = [
    ("import androidx.compose.foundation.layout.Column\n", "import androidx.compose.foundation.layout.Box\n"),
    ("import androidx.compose.foundation.layout.fillMaxSize\n", "import androidx.compose.foundation.layout.fillMaxWidth\n"),
    ("import androidx.compose.foundation.layout.fillMaxWidth\n", "import androidx.compose.foundation.layout.imePadding\n"),
    ("import androidx.compose.foundation.layout.padding\n", "import androidx.compose.foundation.layout.widthIn\n"),
    ("import androidx.compose.foundation.layout.weight\n", "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n"),
    ("import androidx.compose.runtime.setValue\n", "import androidx.compose.ui.Alignment\n"),
]
for anchor, addition in imports:
    if addition.strip() not in family:
        if anchor not in family:
            raise SystemExit(f"Missing FamilyHubScreen import anchor: {anchor!r}")
        family = family.replace(anchor, anchor + addition, 1)

state_anchor = "    var showAddProfile by rememberSaveable { mutableStateOf(false) }\n"
state_line = "    val householdScrollState = rememberScrollState()\n"
if state_line not in family:
    if state_anchor not in family:
        raise SystemExit("Missing FamilyHubScreen profile-state anchor")
    family = family.replace(state_anchor, state_anchor + state_line, 1)

old_household = '''            FamilyHubSection.HOUSEHOLD -> Column(Modifier.weight(1f)) {
                ProfileSwitcher(
                    state = companionState,
                    onSelected = companionRepository::switchProfile,
                    onAdd = { showAddProfile = true },
                    modifier = Modifier.padding(
                        horizontal = layoutSpec.outerPaddingDp.dp,
                        vertical = 12.dp,
                    ),
                )
                HouseholdScreen(
                    account = account,
                    organizerRepository = organizerRepository,
                    layoutSpec = layoutSpec,
                    modifier = Modifier.weight(1f),
                )
            }
'''
new_household = '''            FamilyHubSection.HOUSEHOLD -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(householdScrollState)
                    .imePadding(),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ProfileSwitcher(
                        state = companionState,
                        onSelected = companionRepository::switchProfile,
                        onAdd = { showAddProfile = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 880.dp)
                            .padding(
                                horizontal = layoutSpec.outerPaddingDp.dp,
                                vertical = 12.dp,
                            ),
                    )
                }
                HouseholdScreen(
                    account = account,
                    organizerRepository = organizerRepository,
                    layoutSpec = layoutSpec,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
'''
if old_household in family:
    family = family.replace(old_household, new_household, 1)
elif new_household not in family:
    raise SystemExit("FamilyHubScreen household block no longer matches the verified 0.15.8 source")

family_path.write_text(family, encoding="utf-8")

household = household_path.read_text(encoding="utf-8")
household = household.replace(
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.fillMaxWidth\n",
    1,
)
if "import androidx.compose.foundation.layout.fillMaxWidth\n" not in household:
    anchor = "import androidx.compose.foundation.layout.Column\n"
    if anchor not in household:
        raise SystemExit("Missing HouseholdScreen fillMaxWidth import anchor")
    household = household.replace(anchor, anchor + "import androidx.compose.foundation.layout.fillMaxWidth\n", 1)

if "Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter)" in household:
    household = household.replace(
        "Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter)",
        "Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter)",
        1,
    )
elif "Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter)" not in household:
    raise SystemExit("HouseholdScreen root container no longer matches the verified source")

household_path.write_text(household, encoding="utf-8")

assert "val householdScrollState = rememberScrollState()" in family
assert ".verticalScroll(householdScrollState)" in family
assert ".imePadding()" in family
assert ".widthIn(max = 880.dp)" in family
assert "modifier = Modifier.fillMaxWidth(),\n                )\n            }" in family
assert "modifier = Modifier.weight(1f),\n                )\n            }" not in family
assert "Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter)" in household
PY

FAMILY=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt
HOUSEHOLD=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt

grep -Fq 'val householdScrollState = rememberScrollState()' "$FAMILY"
grep -Fq '.verticalScroll(householdScrollState)' "$FAMILY"
grep -Fq '.imePadding()' "$FAMILY"
grep -Fq '.widthIn(max = 880.dp)' "$FAMILY"
grep -Fq 'Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter)' "$HOUSEHOLD"

printf 'Applied My Study Companion 0.15.9 adaptive household scrolling and invitation-code visibility repair.\n'
