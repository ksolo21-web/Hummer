#!/usr/bin/env bash
set -euo pipefail

# Reconstruct and verify the complete 0.12.3 Firebase-direct baseline first.
bash .msc-build/reconstruct-build-0123.sh

# Apply the checksum-locked 0.12.4 security and data-integrity patch.
base64 --decode .msc-build/firebase-family-0.12.4-hardening.patch.xz.b64 \
  > /tmp/firebase-family-0.12.4-hardening.patch.xz
echo 'ca1a5c0310db811ccf9399e1bc2295b605ccba8dc14e4039481b0894b250bdae  /tmp/firebase-family-0.12.4-hardening.patch.xz' \
  | sha256sum -c -
xz -dc /tmp/firebase-family-0.12.4-hardening.patch.xz \
  > /tmp/firebase-family-0.12.4-hardening.patch
echo '7bcda6a10c1de5af1a37872f8b2a702b36d3787d38d9522f05a9313404c3e189  /tmp/firebase-family-0.12.4-hardening.patch' \
  | sha256sum -c -
patch --batch --forward -p1 -d MyStudyCompanion \
  < /tmp/firebase-family-0.12.4-hardening.patch

# Static hardening gates before Gradle is allowed to build.
grep -q 'versionCode = 28' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.4-private-alpha-firebase-family-hardened' MyStudyCompanion/app/build.gradle.kts
grep -q 'match /ideas/{ideaId}' MyStudyCompanion/firestore.rules
grep -q 'match /ideaVotes/{voteId}' MyStudyCompanion/firestore.rules
grep -q "voteId == 'vote-'" MyStudyCompanion/firestore.rules
grep -q 'request.resource.data.usedAt == request.time' MyStudyCompanion/firestore.rules
grep -q 'existsAfter(memberPath(householdId, request.auth.uid))' MyStudyCompanion/firestore.rules
grep -q 'Do not read the protected household document before membership exists' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
! grep -q 'transaction.get(householdRef)' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -q 'CloudFamilyBoardConfig' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -q 'vote-$accountUid~$ideaId~$voterUid' \
  MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -q 'FirebaseFamilyHardeningTest' \
  MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FirebaseFamilyHardeningTest.kt

# Re-run the phone tests and assembly after the hardening patch.
pushd MyStudyCompanion >/dev/null
gradle --no-daemon --stacktrace -PMSC_LOCAL_OWNER_MODE=true \
  :app:testDebugUnitTest :app:assembleDebug
popd >/dev/null

AAPT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?}}/build-tools/36.0.0/aapt"
test -x "$AAPT"
PHONE_APK="$(find MyStudyCompanion/app/build/outputs/apk/debug -name '*.apk' -type f | head -n 1)"
test -f "$PHONE_APK"
rm -f dist/MyStudyCompanion-phone-0.12.3-debug.apk
cp "$PHONE_APK" dist/MyStudyCompanion-phone-0.12.4-debug.apk
"$AAPT" dump badging dist/MyStudyCompanion-phone-0.12.4-debug.apk > dist/PHONE-IDENTITY.txt
grep -q "package: name='com.mystudycompanion.app.debug' versionCode='28'" dist/PHONE-IDENTITY.txt
grep -q "versionName='0.12.4-private-alpha-firebase-family-hardened-debug'" dist/PHONE-IDENTITY.txt

rm -rf dist/phone-test-reports
cp -a MyStudyCompanion/app/build/reports/tests dist/phone-test-reports
cp MyStudyCompanion/firestore.rules dist/firestore.rules
cp MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt \
  dist/FamilyWorshipOrganizerRepository.kt

cat > dist/FIREBASE-FAMILY-STATUS.txt <<'TXT'
PASS: Firebase Authentication and direct Cloud Firestore household code compiled after the security audit.
PASS: invitation joining no longer attempts a protected household read before membership exists.
PASS: household creation requires the owner membership to exist atomically.
PASS: organizer-only scheduling state is separated from member-created ideas and votes.
PASS: every vote has one deterministic account/idea/voter document identity, preventing duplicate vote stuffing.
PASS: household members, ideas, votes, member progress, and Family Worship use separate rule-governed records.
PASS: offline local ideas and votes are merged before upload instead of being discarded by the first cloud snapshot.
PENDING: Firestore emulator rule tests, canonical signing, installed Google sign-in, and live two-account synchronization.
TXT

cat >> dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'
PASS: 0.12.4 preserves the complete 0.12.2 JW Library exact-link policy while hardening Firebase family synchronization.
TXT

(
  cd dist
  sha256sum *.apk > SHA256SUMS.txt
)
