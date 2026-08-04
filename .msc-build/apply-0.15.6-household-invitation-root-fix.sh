#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
REPO="$APP/family/FamilyWorshipOrganizerRepository.kt"
WEB="$ROOT/MyStudyCompanionWeb"
PHONE_GRADLE="$ROOT/MyStudyCompanion/app/build.gradle.kts"
WEAR_GRADLE="$ROOT/MyStudyCompanion/wear/build.gradle.kts"

# 0.15.5 already contains the working Firebase Spark client transaction,
# copied-code normalization, first-time account linking, and cancellation-safe UI.
# Do not apply the abandoned 0.15.6 Cloud Run/Admin SDK overlay: it introduced a
# billing dependency that is outside this app's permanent free-only requirement.

python3 - "$PHONE_GRADLE" "$WEAR_GRADLE" "$WEB/sw.js" "$WEB/appearance.test.mjs" <<'PY'
from pathlib import Path
import re
import sys

phone = Path(sys.argv[1])
wear = Path(sys.argv[2])
sw = Path(sys.argv[3])
appearance = Path(sys.argv[4])

text = phone.read_text(encoding="utf-8")
text, count = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 39", text, count=1)
assert count == 1
text, count = re.subn(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "0.15.6-private-alpha-free-household-invitation"',
    text,
    count=1,
)
assert count == 1
phone.write_text(text, encoding="utf-8")

text = wear.read_text(encoding="utf-8")
text, count = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 360156001", text, count=1)
assert count == 1
text, count = re.subn(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "0.15.6-wear-private-alpha-free-household-invitation"',
    text,
    count=1,
)
assert count == 1
wear.write_text(text, encoding="utf-8")

cache = "msc-web-v0156-free-household-invitation-v1"
text = sw.read_text(encoding="utf-8")
text, count = re.subn(r"msc-web-v015[0-9][^\"']*", cache, text, count=1)
assert count == 1
sw.write_text(text, encoding="utf-8")

text = appearance.read_text(encoding="utf-8")
text = re.sub(r"msc-web-v015[0-9][^\"']*", cache, text)
appearance.write_text(text, encoding="utf-8")
PY

# Android must retain the known-working, cancellation-safe Firestore transaction.
grep -Fq 'suspend fun createHouseholdInvitation' "$REPO"
grep -Fq 'suspend fun joinHousehold' "$REPO"
grep -Fq 'householdInvitationLookupCandidates' "$REPO"
grep -Fq 'resolvedInviteRef' "$REPO"
grep -Fq 'db.collection(INVITATIONS).document' "$REPO"
grep -Fq 'transaction.update(resolvedInviteRef' "$REPO"
grep -Fq 'catch (cancellation: CancellationException)' "$REPO"
grep -Fq 'requestCreateHouseholdInvitation' "$REPO"
grep -Fq 'requestJoinHousehold' "$REPO"
! grep -Fq 'backendApi.createHouseholdInvitation' "$REPO"
! grep -Fq 'backendApi.joinHousehold' "$REPO"

# Firebase Spark rules must permit the tightly scoped one-time invitation flow
# and first-time household linking, rather than denying all client access.
python3 - "$ROOT/MyStudyCompanion/firestore.rules" "$WEB/firestore.rules" <<'PY'
from pathlib import Path
import re
import sys

for value in sys.argv[1:]:
    path = Path(value)
    text = path.read_text(encoding="utf-8")
    block = re.search(r"match /householdInvites/\{code\} \{(.*?)\n    \}", text, re.S)
    assert block, path
    assert "allow read, write: if false;" not in block.group(1), path
    assert "get(userPath(uid)).data.householdId == ''" in text, path
    assert "validUserDocument(uid)" in text, path
print("PASS: Firebase Spark invitation and first-time linking rules are active.")
PY

# The PWA validates invitation documents directly through Firebase on Spark.
grep -Fq 'export function normalizeHouseholdInvitationCode' "$WEB/firebase-sync.js"
grep -Fq 'export async function validateHouseholdInvitation' "$WEB/firebase-sync.js"
grep -Fq 'modules.doc(db,"householdInvites",code)' "$WEB/firebase-sync.js"
! grep -Fq '/v1/household/invitations' "$WEB/firebase-sync.js"
! grep -Fq '/v1/household/join' "$WEB/firebase-sync.js"

grep -Fq 'versionCode = 39' "$PHONE_GRADLE"
grep -Fq '0.15.6-private-alpha-free-household-invitation' "$PHONE_GRADLE"
grep -Fq 'versionCode = 360156001' "$WEAR_GRADLE"
grep -Fq '0.15.6-wear-private-alpha-free-household-invitation' "$WEAR_GRADLE"
grep -Fq 'msc-web-v0156-free-household-invitation-v1' "$WEB/sw.js"
grep -Fq 'msc-web-v0156-free-household-invitation-v1' "$WEB/appearance.test.mjs"

while IFS= read -r file; do node --check "$file"; done < <(find "$WEB" -maxdepth 1 -type f -name '*.js' -print | sort)
mapfile -t TESTS < <(find "$WEB" -maxdepth 1 -type f -name '*.test.mjs' -print | sort)
node --test "${TESTS[@]}"

printf 'Applied My Study Companion 0.15.6 free Firebase Spark household invitation restoration.\n'
