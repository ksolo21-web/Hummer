#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${MSC_RELEASE_STORE_FILE:?MSC_RELEASE_STORE_FILE is required}"
: "${MSC_RELEASE_STORE_PASSWORD:?MSC_RELEASE_STORE_PASSWORD is required}"
: "${MSC_RELEASE_KEY_ALIAS:?MSC_RELEASE_KEY_ALIAS is required}"
: "${MSC_RELEASE_KEY_PASSWORD:?MSC_RELEASE_KEY_PASSWORD is required}"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT="$SDK_ROOT/build-tools/36.0.0/aapt"
APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"
ZIPALIGN="$SDK_ROOT/build-tools/36.0.0/zipalign"
for tool in "$AAPT" "$APKSIGNER" "$ZIPALIGN"; do test -x "$tool"; done

bash .msc-build/reconstruct-current-source-0.14.1.sh
bash .msc-build/apply-0.15.1-workbook-flip-profile.sh
bash .msc-build/apply-0.15.2-daily-profile-ministry.sh
bash .msc-build/apply-0.15.3-professional-workbook-assets.sh
bash .msc-build/apply-0.15.4-web-household-parity.sh
bash .msc-build/apply-0.15.5-household-cancellation.sh
bash .msc-build/apply-0.15.6-household-invitation-root-fix.sh
bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh

base64 --decode .msc-build/firebase-google-services-0.12.4.json.b64 > MyStudyCompanion/app/google-services.json
echo '5b2f85f67e6cb33e1fbe0a58b704a5c971bf2faf6f1b6e09730e904d02b91b5e  MyStudyCompanion/app/google-services.json' | sha256sum -c -

MSC_GOOGLE_WEB_CLIENT_ID="$(python3 - <<'PY'
import json
from pathlib import Path

data = json.loads(Path('MyStudyCompanion/app/google-services.json').read_text(encoding='utf-8'))
assert data['project_info']['project_id'] == 'my-study-companion-abc01'
clients = {
    item['client_info']['android_client_info']['package_name']: item
    for item in data['client']
}
assert {'com.mystudycompanion.app', 'com.mystudycompanion.app.debug'} <= set(clients)
canonical = clients['com.mystudycompanion.app']
android = [item for item in canonical['oauth_client'] if item['client_type'] == 1]
web = [item for item in canonical['oauth_client'] if item['client_type'] == 3]
assert len(android) == 1
assert android[0]['android_info']['certificate_hash'] == '1997d421d177215a44f9651ce53dbaec152fbc49'
assert len(web) == 1
assert web[0]['client_id'].endswith('.apps.googleusercontent.com')
print(web[0]['client_id'])
PY
)"
test -n "$MSC_GOOGLE_WEB_CLIENT_ID"

python3 - <<'PY'
from pathlib import Path
import re

targets = (
    (Path('MyStudyCompanion/app/build.gradle.kts'), '41', '0.15.8-private-alpha-google-age-free-invite'),
    (Path('MyStudyCompanion/wear/build.gradle.kts'), '360158001', '0.15.8-wear-private-alpha-google-age-free-invite'),
)
for path, code, name in targets:
    text = path.read_text(encoding='utf-8')
    text, count = re.subn(r'versionCode\s*=\s*\d+', f'versionCode = {code}', text, count=1)
    assert count == 1, path
    text, count = re.subn(r'versionName\s*=\s*"[^"]+"', f'versionName = "{name}"', text, count=1)
    assert count == 1, path
    path.write_text(text, encoding='utf-8')
PY

AUTH=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/auth
COMPANION=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion
FAMILY=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
UI=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui
WEB=MyStudyCompanionWeb/firebase-sync.js

grep -Fq 'CredentialManager' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'GetGoogleIdOption' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'GoogleIdTokenCredential' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'credential.email?.takeIf' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'SCOPE_BIRTHDAY' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'SCOPE_AGE_RANGE' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'people.googleapis.com/v1/people/me?personFields=birthdays,ageRanges' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'signInWithCredential' "$AUTH/AuthRepository.kt"
grep -Fq 'auth.addAuthStateListener' "$AUTH/AuthRepository.kt"
grep -Fq 'firebaseAuth?.currentUser' "$AUTH/AuthRepository.kt"
grep -Fq 'storeProfileHints' "$AUTH/AuthRepository.kt"

grep -Fq 'ProfileAgeSetupScreen(' "$UI/MyStudyCompanionApp.kt"
grep -Fq 'Check Google age again' "$UI/ProfileAgeSetupScreen.kt"
grep -Fq 'refreshGoogleAgeFromAccount' "$UI/MyStudyCompanionApp.kt"
grep -Fq 'get() = ageSource == ProfileAgeSource.UNCONFIRMED' "$COMPANION/CompanionModels.kt"
grep -Fq 'return AgeGroup.PRETEEN to ProfileAgeSource.UNCONFIRMED' "$COMPANION/StudyGroupAssignmentResolver.kt"
! sed -n '/fun resolveAge(/,/private fun FamilyMemberProfile/p' "$COMPANION/StudyGroupAssignmentResolver.kt" | grep -Fq 'householdRole == HouseholdRole.OWNER'

grep -Fq 'suspend fun createHouseholdInvitation' "$FAMILY"
grep -Fq 'suspend fun joinHousehold' "$FAMILY"
grep -Fq 'householdInvitationLookupCandidates' "$FAMILY"
grep -Fq 'transaction.update(resolvedInviteRef' "$FAMILY"
grep -Fq 'catch (cancellation: CancellationException)' "$FAMILY"
grep -Fq 'if (profile.needsAgeConfirmation) return' "$FAMILY"
! sed -n '/private suspend fun syncSignedInMember/,/private suspend fun syncIdeas/p' "$FAMILY" | grep -Fq 'FIELD_AGE_SOURCE'
! sed -n '/private fun memberDocument/,/private fun ideaDocument/p' "$FAMILY" | grep -Fq 'FIELD_AGE_SOURCE'
! grep -Fq 'backendApi.createHouseholdInvitation' "$FAMILY"
! grep -Fq 'backendApi.joinHousehold' "$FAMILY"
grep -Fq 'export async function validateHouseholdInvitation' "$WEB"
grep -Fq 'modules.doc(db,"householdInvites",code)' "$WEB"
! grep -Fq '/v1/household/invitations' "$WEB"
! grep -Fq '/v1/household/join' "$WEB"

(
  cd MyStudyCompanion/backend
  python -m pip install --disable-pip-version-check -r requirements-dev.txt
  python -m compileall -q app tests
  python -m pytest -q
)

while IFS= read -r file; do node --check "$file"; done < <(find MyStudyCompanionWeb -maxdepth 1 -type f -name '*.js' -print | sort)
mapfile -t PWA_TESTS < <(find MyStudyCompanionWeb -maxdepth 1 -type f -name '*.test.mjs' -print | sort)
node --test "${PWA_TESTS[@]}"

DEPS="$RUNNER_TEMP/msc-rules-deps"
npm install --prefix "$DEPS" --no-save firebase @firebase/rules-unit-testing
(
  cd MyStudyCompanionWeb
  NODE_PATH="$DEPS/node_modules" npx --yes firebase-tools@15.1.0 emulators:exec \
    --only firestore \
    --project demo-my-study-companion \
    "NODE_PATH=$DEPS/node_modules node firestore-rules.test.cjs"
)

(
  cd MyStudyCompanion
  gradle --no-daemon --stacktrace :app:testDebugUnitTest :wear:testDebugUnitTest
  gradle --no-daemon --stacktrace \
    -PMSC_LOCAL_OWNER_MODE=true \
    -PMSC_GOOGLE_WEB_CLIENT_ID="$MSC_GOOGLE_WEB_CLIENT_ID" \
    -PMSC_BACKEND_BASE_URL='' \
    :app:assemblePrivateAlpha :wear:assemblePrivateAlpha
)

rm -rf release-0.15.8
mkdir -p release-0.15.8/phone release-0.15.8/wear release-0.15.8/metadata
PHONE_SOURCE="$(find MyStudyCompanion/app/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
WEAR_SOURCE="$(find MyStudyCompanion/wear/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
test -s "$PHONE_SOURCE"
test -s "$WEAR_SOURCE"
PHONE_APK=release-0.15.8/phone/MyStudyCompanion-phone-0.15.8-configured-ci.apk
WEAR_APK=release-0.15.8/wear/MyStudyCompanion-wear-0.15.8-configured-ci.apk
cp "$PHONE_SOURCE" "$PHONE_APK"
cp "$WEAR_SOURCE" "$WEAR_APK"

"$AAPT" dump badging "$PHONE_APK" > release-0.15.8/metadata/PHONE-IDENTITY.txt
"$AAPT" dump badging "$WEAR_APK" > release-0.15.8/metadata/WEAR-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='41'" release-0.15.8/metadata/PHONE-IDENTITY.txt
grep -q "versionName='0.15.8-private-alpha-google-age-free-invite'" release-0.15.8/metadata/PHONE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='360158001'" release-0.15.8/metadata/WEAR-IDENTITY.txt
grep -q "versionName='0.15.8-wear-private-alpha-google-age-free-invite'" release-0.15.8/metadata/WEAR-IDENTITY.txt

"$AAPT" dump resources "$PHONE_APK" > release-0.15.8/metadata/PHONE-RESOURCES.txt
grep -Fq 'default_web_client_id' release-0.15.8/metadata/PHONE-RESOURCES.txt
grep -Fq 'google_app_id' release-0.15.8/metadata/PHONE-RESOURCES.txt
grep -Fq 'google_api_key' release-0.15.8/metadata/PHONE-RESOURCES.txt
grep -R -Fq 'FIREBASE_CONFIGURED = true' MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha
grep -R -E 'GOOGLE_WEB_CLIENT_ID = ".+\.apps\.googleusercontent\.com"' MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha

# Verify the finished APK contains the restored age-screen text, not just source.
unzip -p "$PHONE_APK" classes.dex > release-0.15.8/metadata/classes.dex
strings release-0.15.8/metadata/classes.dex > release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'ProfileAgeSetupScreen' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Check Google age again' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Google did not return a birthday or age range' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
rm release-0.15.8/metadata/classes.dex

"$APKSIGNER" verify --verbose --print-certs "$PHONE_APK" > release-0.15.8/metadata/PHONE-CI-SIGNATURE.txt
"$APKSIGNER" verify --verbose --print-certs "$WEAR_APK" > release-0.15.8/metadata/WEAR-CI-SIGNATURE.txt
"$ZIPALIGN" -c -P 16 -v 4 "$PHONE_APK" > release-0.15.8/metadata/PHONE-ZIPALIGN.txt
"$ZIPALIGN" -c -P 16 -v 4 "$WEAR_APK" > release-0.15.8/metadata/WEAR-ZIPALIGN.txt
sha256sum "$PHONE_APK" "$WEAR_APK" > release-0.15.8/metadata/CI-SHA256SUMS.txt

cat > release-0.15.8/metadata/RELEASE-GATES.txt <<'TXT'
PASS: Firebase Android resources and Google OAuth web client are packaged in the finished phone APK.
PASS: persisted Firebase sessions automatically retry Google birthday/age-range authorization once when age is unresolved.
PASS: unresolved Google age opens the restored verification screen instead of silently assigning Preteen or Adult.
PASS: Google-confirmed under-18 accounts cannot select Adult.
PASS: concrete Google birthday/age-range data overrides stale unconfirmed placeholders.
PASS: household role is never treated as age evidence.
PASS: unresolved member profiles are not uploaded to Firestore.
PASS: household member writes omit ageSource and are compatible with the already-live free Spark rules.
PASS: free Firebase Spark invitation and join transactions remain active without paid Cloud Run endpoints.
PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.
NOTE: CI APKs use a disposable packaging certificate and must be re-signed offline with the established Firebase-registered private-test certificate before device delivery.
TXT

printf 'PASS: configured 0.15.8 phone and Wear APKs built and verified.\n'
