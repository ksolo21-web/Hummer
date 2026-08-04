#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_GLOB="$ROOT/.msc-build/msc-0.15.2-daily-profile-ministry.part*"
ENCODED="$(mktemp --suffix=.b64)"
ARCHIVE="$(mktemp --suffix=.tar.xz)"
trap 'rm -f "$ENCODED" "$ARCHIVE"' EXIT

EXPECTED_SHA256='de6fa85394e3179fda86d4fcabf18d0527e73a56249f2d920ebef966b8103010'
compgen -G "$PAYLOAD_GLOB" >/dev/null
cat $PAYLOAD_GLOB > "$ENCODED"
base64 --decode "$ENCODED" > "$ARCHIVE"
echo "$EXPECTED_SHA256  $ARCHIVE" | sha256sum -c -
tar -xJf "$ARCHIVE" -C "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
TESTS="$ROOT/MyStudyCompanion/app/src/test/java/com/mystudycompanion/app"

# The child-facing manual study-level chooser is no longer part of the release path.
rm -f "$APP/ui/ProfileAgeSetupScreen.kt"

# Option A Daily Text hierarchy: dedicated scripture card, full article immediately
# below it, and every existing reflection/action section preserved.
grep -Fq 'TODAY’S SCRIPTURE' "$APP/ui/HomeScreen.kt"
grep -Fq 'FULL DAILY TEXT' "$APP/ui/HomeScreen.kt"
grep -Fq 'officialContent.scriptureReference' "$APP/ui/HomeScreen.kt"
grep -Fq 'officialContent.commentary' "$APP/ui/HomeScreen.kt"
grep -Fq 'STUDY COMPANION REFLECTION' "$APP/ui/HomeScreen.kt"
grep -Fq 'Text("Consider"' "$APP/ui/HomeScreen.kt"
grep -Fq 'Open in JW Library' "$APP/ui/HomeScreen.kt"
grep -Fq 'Discuss with Study Assistant' "$APP/ui/HomeScreen.kt"

# Automatic, non-blocking study-group assignment and profile/progress migration.
grep -Fq 'internal object StudyGroupAssignmentResolver' "$APP/companion/StudyGroupAssignmentResolver.kt"
grep -Fq 'matchStoredIdentity' "$APP/companion/StudyGroupAssignmentResolver.kt"
grep -Fq 'resolveAge' "$APP/companion/StudyGroupAssignmentResolver.kt"
grep -Fq 'HOUSEHOLD_PROFILE' "$APP/companion/CompanionModels.kt"
grep -Fq 'migrateProfileUid' "$APP/companion/CompanionHubRepository.kt"
grep -Fq 'FIELD_AGE_SOURCE' "$APP/family/FamilyWorshipOrganizerRepository.kt"
grep -Fq 'StudyGroupAssignmentResolverTest' "$TESTS/companion/StudyGroupAssignmentResolverTest.kt"
if grep -R -n -E 'ProfileAgeSetupScreen\(|Google did not provide enough age information' "$APP"; then
  echo 'A child-facing manual study-level chooser remains in the active app source.' >&2
  exit 1
fi

# Field-service coaching must provide real preparation and follow-up help.
grep -Fq 'val scriptureReferences: List<String>' "$APP/companion/DailyMinistryPointerCatalog.kt"
grep -Fq 'val actions: List<String>' "$APP/companion/DailyMinistryPointerCatalog.kt"
grep -Fq 'val suggestedWords: String' "$APP/companion/DailyMinistryPointerCatalog.kt"
grep -Fq 'val returnVisitQuestion: String' "$APP/companion/DailyMinistryPointerCatalog.kt"
grep -Fq 'Why this helps' "$APP/ui/DailyFieldServicePointerCard.kt"
grep -Fq 'Try this today' "$APP/ui/DailyFieldServicePointerCard.kt"
grep -Fq 'Suggested words' "$APP/ui/DailyFieldServicePointerCard.kt"
grep -Fq 'Return-visit bridge' "$APP/ui/DailyFieldServicePointerCard.kt"
grep -Fq 'Scriptures to prepare' "$APP/ui/DailyFieldServicePointerCard.kt"
grep -Fq 'scriptureReferences.size >= 2' "$TESTS/companion/FinalMajorContentTest.kt"
grep -Fq 'actions.size >= 3' "$TESTS/companion/FinalMajorContentTest.kt"

printf 'Applied My Study Companion 0.15.2 Daily Text, automatic study-group, and ministry-coaching repairs.\n'
