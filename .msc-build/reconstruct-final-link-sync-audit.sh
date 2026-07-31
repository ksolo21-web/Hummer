#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the exact post-overlay 0.12.2 source without spending this
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
base64 --decode .msc-build/patch-0.12.2-complete-jw-links.py.xz.b64 | xz -dc > /tmp/patch-0.12.2-complete-jw-links.py
echo '7fbbcd2af666d519a7580b5c6287d63601b0a539489e00840518af3293c72bfe  /tmp/patch-0.12.2-complete-jw-links.py' | sha256sum -c -
python3 /tmp/patch-0.12.2-complete-jw-links.py
mkdir -p dist

python3 .msc-build/audit-final-link-sync-surface.py \
  MyStudyCompanion \
  dist/FINAL-LINK-SYNC-SURFACE.json

test -s dist/FINAL-LINK-SYNC-SURFACE.json
test -s dist/FINAL-LINK-SYNC-SURFACE.txt

tar -cJf dist/FINAL-RECONSTRUCTED-SOURCE.tar.xz \
  -C MyStudyCompanion \
  app/src/main \
  app/src/test \
  wear/src/main \
  app/build.gradle.kts \
  wear/build.gradle.kts \
  gradle/libs.versions.toml

test -s dist/FINAL-RECONSTRUCTED-SOURCE.tar.xz
