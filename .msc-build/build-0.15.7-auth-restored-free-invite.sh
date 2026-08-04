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
for tool in "$AAPT" "$APKSIGNER" "$ZIPALIGN"; do
  test -x "$tool"
done

bash .msc-build/reconstruct-current-source-0.14.1.sh
bash .msc-build/apply-0.15.1-workbook-flip-profile.sh
bash .msc-build/apply-0.15.2-daily-profile-ministry.sh
bash .msc-build/apply-0.15.3-professional-workbook-assets.sh
bash .msc-build/apply-0.15.4-web-household-parity.sh
bash .msc-build/apply-0.15.5-household-cancellation.sh
bash .msc-build/apply-0.15.6-household-invitation-root-fix.sh

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
    (Path('MyStudyCompanion/app/build.gradle.kts'), '40', '0.15.7-private-alpha-auth-restored-free-invite'),
    (Path('MyStudyCompanion/wear/build.gradle.kts'), '360157001', '0.15.7-wear-private-alpha-auth-restored-free-invite'),
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
TESTS=MyStudyCompanion/app/src/test/java/com/mystudycompanion/app
REPO=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
WEB=MyStudyCompanionWeb/firebase-sync.js

grep -Fq 'CredentialManager' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'GetGoogleIdOption' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'GoogleIdTokenCredential' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'SCOPE_BIRTHDAY' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'SCOPE_AGE_RANGE' "$AUTH/GoogleSignInCoordinator.kt"
grep -Fq 'signInWithCredential' "$AUTH/AuthRepository.kt"
grep -Fq 'auth.addAuthStateListener' "$AUTH/AuthRepository.kt"
grep -Fq 'firebaseAuth?.currentUser' "$AUTH/AuthRepository.kt"
grep -Fq 'storeProfileHints' "$AUTH/AuthRepository.kt"
grep -Fq 'age < 10 -> AccountAgeGroup.CHILD' "$AUTH/AuthModels.kt"
grep -Fq 'age < 13 -> AccountAgeGroup.PRETEEN' "$AUTH/AuthModels.kt"
grep -Fq 'age < 18 -> AccountAgeGroup.TEEN' "$AUTH/AuthModels.kt"
grep -Fq '"LESS_THAN_EIGHTEEN" -> AccountAgeGroup.MINOR_UNKNOWN' "$AUTH/AuthModels.kt"
grep -Fq 'AccountAgeGroup.MINOR_UNKNOWN -> stored?.ageGroup?.takeIf { it != AgeGroup.ADULT } ?: AgeGroup.PRETEEN' "$COMPANION/StudyGroupAssignmentResolver.kt"
grep -Fq 'googleBirthdayMapsToYouthStudyLevel' "$TESTS/auth/AuthModelsTest.kt"
grep -Fq 'coarseGoogleMinorRangeNeverDefaultsToAdult' "$TESTS/auth/AuthModelsTest.kt"
test -s "$TESTS/companion/StudyGroupAssignmentResolverTest.kt"

grep -Fq 'suspend fun createHouseholdInvitation' "$REPO"
grep -Fq 'suspend fun joinHousehold' "$REPO"
grep -Fq 'householdInvitationLookupCandidates' "$REPO"
grep -Fq 'transaction.update(resolvedInviteRef' "$REPO"
grep -Fq 'catch (cancellation: CancellationException)' "$REPO"
! grep -Fq 'backendApi.createHouseholdInvitation' "$REPO"
! grep -Fq 'backendApi.joinHousehold' "$REPO"
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
(cd MyStudyCompanionWeb && NODE_PATH="$DEPS/node_modules" npx --yes firebase-tools@15.1.0 emulators:exec --only firestore --project demo-my-study-companion "NODE_PATH=$DEPS/node_modules node firestore-rules.test.cjs")

(
  cd MyStudyCompanion
  gradle --no-daemon --stacktrace :app:testDebugUnitTest :wear:testDebugUnitTest
  gradle --no-daemon --stacktrace \
    -PMSC_LOCAL_OWNER_MODE=true \
    -PMSC_GOOGLE_WEB_CLIENT_ID="$MSC_GOOGLE_WEB_CLIENT_ID" \
    -PMSC_BACKEND_BASE_URL='' \
    :app:assemblePrivateAlpha :wear:assemblePrivateAlpha
)

rm -rf release-0.15.7
mkdir -p release-0.15.7/phone release-0.15.7/wear release-0.15.7/metadata
PHONE_SOURCE="$(find MyStudyCompanion/app/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
WEAR_SOURCE="$(find MyStudyCompanion/wear/build/outputs/apk/privateAlpha -name '*.apk' -type f -print -quit)"
test -s "$PHONE_SOURCE"
test -s "$WEAR_SOURCE"
PHONE_APK=release-0.15.7/phone/MyStudyCompanion-phone-0.15.7-configured-ci.apk
WEAR_APK=release-0.15.7/wear/MyStudyCompanion-wear-0.15.7-configured-ci.apk
cp "$PHONE_SOURCE" "$PHONE_APK"
cp "$WEAR_SOURCE" "$WEAR_APK"

"$AAPT" dump badging "$PHONE_APK" > release-0.15.7/metadata/PHONE-IDENTITY.txt
"$AAPT" dump badging "$WEAR_APK" > release-0.15.7/metadata/WEAR-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='40'" release-0.15.7/metadata/PHONE-IDENTITY.txt
grep -q "versionName='0.15.7-private-alpha-auth-restored-free-invite'" release-0.15.7/metadata/PHONE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app' versionCode='360157001'" release-0.15.7/metadata/WEAR-IDENTITY.txt
grep -q "versionName='0.15.7-wear-private-alpha-auth-restored-free-invite'" release-0.15.7/metadata/WEAR-IDENTITY.txt

"$AAPT" dump resources "$PHONE_APK" > release-0.15.7/metadata/PHONE-RESOURCES.txt
grep -Fq 'default_web_client_id' release-0.15.7/metadata/PHONE-RESOURCES.txt
grep -Fq 'google_app_id' release-0.15.7/metadata/PHONE-RESOURCES.txt
grep -Fq 'google_api_key' release-0.15.7/metadata/PHONE-RESOURCES.txt
grep -R -Fq 'FIREBASE_CONFIGURED = true' MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha
grep -R -E 'GOOGLE_WEB_CLIENT_ID = ".+\.apps\.googleusercontent\.com"' MyStudyCompanion/app/build/generated/source/buildConfig/privateAlpha

"$APKSIGNER" verify --verbose --print-certs "$PHONE_APK" > release-0.15.7/metadata/PHONE-CI-SIGNATURE.txt
"$APKSIGNER" verify --verbose --print-certs "$WEAR_APK" > release-0.15.7/metadata/WEAR-CI-SIGNATURE.txt
"$ZIPALIGN" -c -P 16 -v 4 "$PHONE_APK" > release-0.15.7/metadata/PHONE-ZIPALIGN.txt
"$ZIPALIGN" -c -P 16 -v 4 "$WEAR_APK" > release-0.15.7/metadata/WEAR-ZIPALIGN.txt
sha256sum "$PHONE_APK" "$WEAR_APK" > release-0.15.7/metadata/CI-SHA256SUMS.txt

cat > release-0.15.7/metadata/RELEASE-GATES.txt <<'TXT'
PASS: Firebase Android resources are packaged in the finished phone APK.
PASS: Google web client ID is compiled into the private-alpha BuildConfig.
PASS: Firebase session restoration and Google credential-to-Firebase authentication gates passed.
PASS: Google birthday and age-range mapping tests passed.
PASS: an under-18 Google range cannot default to Adult.
PASS: stored Child, Preteen, or Teen assignment is protected from an unknown/minor Google result.
PASS: free Firebase Spark household invitation and join flow remains active without paid Cloud Run invitation endpoints.
PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.
NOTE: CI APKs use a disposable packaging certificate and must be re-signed offline with the established Firebase-registered private-test certificate before device delivery.
TXT

printf 'PASS: configured 0.15.7 phone and Wear APKs built and verified.\n'
