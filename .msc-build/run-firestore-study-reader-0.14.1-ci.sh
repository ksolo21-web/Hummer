#!/usr/bin/env bash
set -euo pipefail

cd .msc-build/firebase-rules-tests
npm ci

set +e
npx --yes firebase-tools@15.1.0 emulators:exec \
  --only firestore \
  --project demo-my-study-companion \
  "node rules.test.cjs" 2>&1 | tee FIRESTORE-RULES-TEST-RESULTS.txt
firebase_status=${PIPESTATUS[0]}
set -e

if [[ $firebase_status -ne 0 ]]; then
  echo "Firebase emulator test command failed with status $firebase_status." >&2
  exit "$firebase_status"
fi

grep -Fq 'PASS: 26 Firestore authorization, integrity, and abuse tests completed.' \
  FIRESTORE-RULES-TEST-RESULTS.txt

echo 'PASS: Captured and verified all 26 Firestore study-reader and workbook tests.'
