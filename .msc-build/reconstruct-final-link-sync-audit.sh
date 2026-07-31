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

cat >> dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'
DIAGNOSTIC: the fully reconstructed source link, direct ACTION_VIEW, authentication, family, and synchronization surfaces were inventoried for the complete-link hardening pass. This inventory is not itself a release pass.
TXT
