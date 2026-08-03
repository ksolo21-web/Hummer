#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/.msc-build/patch-0.15.4-web-household-parity.py" "$ROOT"
python3 "$ROOT/.msc-build/patch-0.15.4-firebase-config.py" "$ROOT"

APP="$ROOT/MyStudyCompanion/app/src/main/java/com/mystudycompanion/app"
WEB="$ROOT/MyStudyCompanionWeb"

# PWA must use the same stored professional artwork and interaction masks.
grep -Fq 'loadProfessionalWorkbookAsset' "$WEB/workbook.js"
grep -Fq 'professionalPrintData' "$WEB/workbook.js"
grep -Fq 'regionMaskData' "$WEB/workbook.js"
grep -Fq 'difference-changed.webp' "$WEB/workbook.js"
grep -Fq 'drawing-step-1.webp' "$WEB/workbook.js"
grep -Fq 'renderSavedWork' "$WEB/workbook.js"
grep -Fq 'msc-web-v0154-professional-workbook-household-v1' "$WEB/sw.js"
! grep -R -Eq 'msc-web-v0151|msc-web-v0152|msc-web-v0153-professional-workbook-assets-v1' "$WEB/sw.js"
grep -Fq '"firestore"' "$WEB/firebase.json"
grep -Fq '"rules": "firestore.rules"' "$WEB/firebase.json"

# Invitation code normalization and first-time Firestore linking must work.
grep -Fq 'householdInvitationLookupCandidates' "$APP/family/FamilyWorshipOrganizerRepository.kt"
grep -Fq 'resolvedInviteRef' "$APP/family/FamilyWorshipOrganizerRepository.kt"
grep -Fq "get(userPath(uid)).data.householdId == ''" "$ROOT/MyStudyCompanion/firestore.rules"
grep -Fq "get(userPath(uid)).data.householdId == ''" "$WEB/firestore.rules"
grep -Fq 'normalizeHouseholdInvitationCode' "$WEB/firebase-sync.js"
grep -Fq 'validateHouseholdInvitation' "$WEB/firebase-sync.js"
grep -Fq 'versionCode = 37' "$ROOT/MyStudyCompanion/app/build.gradle.kts"
grep -Fq 'versionCode = 360154001' "$ROOT/MyStudyCompanion/wear/build.gradle.kts"

while IFS= read -r file; do node --check "$file"; done < <(find "$WEB" -maxdepth 1 -type f -name '*.js' -print | sort)
mapfile -t TESTS < <(find "$WEB" -maxdepth 1 -type f -name '*.test.mjs' -print | sort)
node --test "${TESTS[@]}"

printf 'Applied My Study Companion 0.15.4 PWA parity and household invitation repair.\n'
