#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the exact signed 0.12.1 source/build stage first. The final Kotlin
# tree exists only after all archived stages and overlays have been applied.
bash .msc-build/reconstruct-build-0121-signed-pair.sh

python3 .msc-build/audit-final-link-sync-surface.py \
  MyStudyCompanion \
  dist/FINAL-LINK-SYNC-SURFACE.json

test -s dist/FINAL-LINK-SYNC-SURFACE.json
test -s dist/FINAL-LINK-SYNC-SURFACE.txt

# Preserve the actual post-overlay source used for the APK so every visible link,
# widget action, authentication path, and family synchronization path can be
# reviewed and patched against exact source rather than archive-level guesses.
tar -cJf dist/FINAL-RECONSTRUCTED-SOURCE.tar.xz \
  -C MyStudyCompanion \
  app/src/main \
  app/src/test \
  wear/src/main \
  app/build.gradle.kts \
  wear/build.gradle.kts \
  gradle/libs.versions.toml

test -s dist/FINAL-RECONSTRUCTED-SOURCE.tar.xz

cat >> dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'
DIAGNOSTIC: the fully reconstructed source link, direct ACTION_VIEW, authentication, family, and synchronization surfaces were inventoried for the complete-link hardening pass. This inventory is not itself a release pass.
TXT
