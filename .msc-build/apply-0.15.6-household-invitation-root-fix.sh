#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_GLOB="$ROOT/.msc-build/msc-0.15.6-household-invitation-root-fix.part*"
ENCODED="$(mktemp --suffix=.b64)"
PATCH_XZ="$(mktemp --suffix=.patch.xz)"
PATCH_FILE="$(mktemp --suffix=.patch)"
trap 'rm -f "$ENCODED" "$PATCH_XZ" "$PATCH_FILE"' EXIT

EXPECTED_XZ_SHA256='4eeb536f928b28e847b25c20457df934764a43e28a5a2579d1da0a1e99d433e3'
compgen -G "$PAYLOAD_GLOB" >/dev/null
cat $PAYLOAD_GLOB > "$ENCODED"
base64 --decode "$ENCODED" > "$PATCH_XZ"
echo "$EXPECTED_XZ_SHA256  $PATCH_XZ" | sha256sum -c -
xz --decompress --stdout "$PATCH_XZ" > "$PATCH_FILE"
(
  cd "$ROOT"
  patch -p1 --forward --batch < "$PATCH_FILE"
)

# Correct the backend error-detail parser after the binary overlay is applied.
# The initial patch accidentally emitted JavaScript-style regex escapes into a
# Kotlin string literal, which prevented Android compilation. Replace the whole
# method deterministically so all release workflows reconstruct identical code.
python3 - "$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/network/BackendApi.kt" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')
start = text.index('    private fun requireSuccess(')
end = text.index('\n    companion object', start)
method = '''    private fun requireSuccess(response: BackendResponse, operation: String) {
        if (response.statusCode !in 200..299) {
            val detail = Regex("\\\"detail\\\"\\\\s*:\\\\s*\\\"([^\\\"]+)\\\"")
                .find(response.body)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\\\n", " ")
                ?.replace("\\\\\\\"", "\\\"")
                ?.trim()
                .orEmpty()
            val safeDetail = detail.ifBlank {
                response.body.take(240).replace(Regex("[\\\\r\\\\n]+"), " ").trim()
            }
            throw BackendProtocolException(
                safeDetail.ifBlank { "The $operation failed with status ${response.statusCode}." },
            )
        }
    }
'''
path.write_text(text[:start] + method + text[end:], encoding='utf-8')

fixed = path.read_text(encoding='utf-8')
assert 'Regex("\\\"detail\\\"\\\\s*:\\\\s*\\\"([^\\\"]+)\\\"")' in fixed
assert '?.replace("\\\\\\\"", "\\\"")' in fixed
assert 'Regex("[\\\\r\\\\n]+")' in fixed
assert 'Regex("\\\"detail\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")' not in fixed
print('PASS: corrected Kotlin backend error-detail parser escaping.')
PY

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
BACKEND="$ROOT/MyStudyCompanion/backend"
WEB="$ROOT/MyStudyCompanionWeb"
RULES="$ROOT/MyStudyCompanion/firestore.rules"

# Root architecture: only the authenticated server can create, validate, or redeem codes.
grep -Fq 'class HouseholdInvitationService' "$BACKEND/app/services/household_invitation_service.py"
grep -Fq 'Server-authoritative one-time household invitation transactions' "$BACKEND/app/services/household_invitation_service.py"
grep -Fq '@app.post("/v1/household/invitations"' "$BACKEND/app/main.py"
grep -Fq '@app.post("/v1/household/invitations/validate"' "$BACKEND/app/main.py"
grep -Fq '@app.post("/v1/household/join"' "$BACKEND/app/main.py"
grep -Fq 'require_authenticated_user' "$BACKEND/app/security/dependencies.py"
grep -Fq 'google-cloud-firestore==2.28.0' "$BACKEND/pyproject.toml"

# Android and web clients must call the server rather than writing invite documents.
grep -Fq 'backendApi.createHouseholdInvitation' "$APP/family/FamilyWorshipOrganizerRepository.kt"
grep -Fq 'backendApi.joinHousehold' "$APP/family/FamilyWorshipOrganizerRepository.kt"
grep -Fq '/v1/household/invitations' "$APP/network/BackendApi.kt"
grep -Fq '/v1/household/join' "$APP/network/BackendApi.kt"
grep -Fq '/v1/household/invitations/validate' "$WEB/firebase-sync.js"
grep -Fq '/v1/household/join' "$WEB/firebase-sync.js"

python3 - "$APP/family/FamilyWorshipOrganizerRepository.kt" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(encoding='utf-8')
for name in ('createHouseholdInvitation', 'joinHousehold'):
    start = text.index(f'suspend fun {name}')
    next_start = text.find('\n    suspend fun ', start + 1)
    block = text[start: next_start if next_start >= 0 else len(text)]
    assert 'backendApi.' in block, name
    assert 'householdInvites' not in block, name
    assert 'runTransaction' not in block, name
print('PASS: Android invitation methods contain no direct Firestore invitation transaction.')
PY

# Client rules must deny all invitation-secret reads and writes; Admin SDK owns the transaction.
python3 - "$RULES" "$WEB/firestore.rules" <<'PY'
from pathlib import Path
import re, sys
for arg in sys.argv[1:]:
    text = Path(arg).read_text(encoding='utf-8')
    block = re.search(r'match /householdInvites/\{code\} \{(.*?)\n    \}', text, re.S)
    assert block, arg
    assert 'allow read, write: if false;' in block.group(1), arg
print('PASS: client invitation reads and writes are denied in Android and PWA rules.')
PY

# Transaction behavior has explicit tests for organizer, blank existing user, reuse,
# cross-household conflict, expiry, malformed codes, and idempotent same-user retry.
grep -Fq 'test_create_and_redeem_existing_google_user_without_household' "$BACKEND/tests/test_household_invitation_root_fix.py"
grep -Fq 'test_used_code_cannot_join_a_second_user' "$BACKEND/tests/test_household_invitation_root_fix.py"
grep -Fq 'test_cross_household_user_is_rejected' "$BACKEND/tests/test_household_invitation_root_fix.py"
grep -Fq 'test_expired_code_is_rejected' "$BACKEND/tests/test_household_invitation_root_fix.py"
grep -Fq 'assert retried.already_joined is True' "$BACKEND/tests/test_household_invitation_root_fix.py"

# Synchronized release identities and PWA cache.
grep -Fq 'versionCode = 39' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq '0.15.6-private-alpha-household-invitation-root-fix' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq 'versionCode = 360156001' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq '0.15.6-wear-private-alpha-household-invitation-root-fix' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq 'msc-web-v0156-household-invitation-root-v1' "$WEB/sw.js"

printf 'Applied My Study Companion 0.15.6 server-authoritative household invitation root fix.\n'
