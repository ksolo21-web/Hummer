#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/.msc-build/patch-0.15.5-household-cancellation.py" "$ROOT"
python3 "$ROOT/.msc-build/patch-0.15.5-pwa-release-test.py" "$ROOT"

REPO="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
UI="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt"
TEST="$ROOT/MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyCancellationSafetyTest.kt"

# Cancellation must retain structured-concurrency semantics and never become UI text.
grep -Fq 'catch (cancellation: CancellationException)' "$REPO"
grep -Fq 'throw cancellation' "$REPO"
grep -Fq 'runFamilyCatching' "$REPO"
grep -Fq 'familyErrorMessageForDisplay' "$REPO"
grep -Fq 'requestRefreshCapabilities' "$REPO"
grep -Fq 'requestCreateHouseholdInvitation' "$REPO"
grep -Fq 'requestJoinHousehold' "$REPO"
grep -Fq 'familyErrorMessageForDisplay(organizerState.errorMessage)' "$UI"
grep -Fq 'onClick = organizerRepository::requestCreateHouseholdInvitation' "$UI"
grep -Fq 'organizerRepository.requestJoinHousehold(invitationInput)' "$UI"
! grep -Fq 'rememberCoroutineScope' "$UI"
! grep -Fq 'scope.launch' "$UI"

grep -Fq 'coroutineCancellationIsRethrownInsteadOfDisplayed' "$TEST"
grep -Fq 'internalCancellationTextIsNeverRendered' "$TEST"
grep -Fq 'versionCode = 38' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq '0.15.5-private-alpha-household-cancellation-fix' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq 'versionCode = 360155001' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq '0.15.5-wear-private-alpha-household-cancellation-fix' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"
grep -Fq 'msc-web-v0155-household-cancellation-v1' "$ROOT/MyStudyCompanionWeb/sw.js"
grep -Fq 'msc-web-v0155-household-cancellation-v1' "$ROOT/MyStudyCompanionWeb/appearance.test.mjs"

printf 'Applied My Study Companion 0.15.5 household coroutine and invitation safety repair.\n'
