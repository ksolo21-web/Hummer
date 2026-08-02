#!/usr/bin/env bash
set -euo pipefail

# Preserve the final release-theme restriction outside the historical overlay
# stack before reconstructing the exact application source.
release_theme_pruner="$(mktemp /tmp/msc-original-theme-set.XXXXXX.py)"
cp .msc-build/apply-original-theme-set-0.14.1.py "$release_theme_pruner"

bash .msc-build/reconstruct-current-source-0.14.1.sh
python3 "$release_theme_pruner"

# Run the focused web regression tests after the release gallery is restricted.
node --check MyStudyCompanionWeb/appearance.js
node --check MyStudyCompanionWeb/firebase-sync.js
node --test \
  MyStudyCompanionWeb/appearance.test.mjs \
  MyStudyCompanionWeb/study-library-merge.test.mjs

echo 'PASS: final 0.14.1 source reconstructed with only the original seven themes plus Golden Owl, Sakura Tiger, and Automatic exposed.'
