#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the exact post-overlay 0.12.1 source without spending this
# diagnostic job on APK compilation. The normal build and installed workflows
# remain the authoritative compilation and device gates.
python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0120.sh').read_text(encoding='utf-8')
marker = "\nWEEK_URL="
if source.count(marker) != 1:
    raise SystemExit('Expected exactly one final online/build boundary.')
Path('/tmp/reconstruct-final-source-only.sh').write_text(
    source.split(marker, 1)[0] + "\n",
    encoding='utf-8',
)
PY
bash /tmp/reconstruct-final-source-only.sh

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
