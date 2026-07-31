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
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-exact-link-tests.py)" = 'd99c94f07cded5a3d91ed0ae89281ba1a131c145'
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-link-gate-v2.py)" = '2312026e660380dfb4c79a619ee54b9839c1a0a0'
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-link-gate-v3.py)" = 'e1462f0b268e4c350353a29b93ea992985d5acf6'
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-cloud-family-client.py)" = 'ab1fafb30fe06e82919f5d20e0ec012cb9895db7'

python3 /tmp/patch-0.12.2-complete-jw-links.py
python3 .msc-build/patch-0.12.2-exact-link-tests.py
python3 .msc-build/patch-0.12.2-link-cloud-followup.py
python3 .msc-build/patch-0.12.2-final-link-gate-v3.py
python3 .msc-build/patch-0.12.2-cloud-family-client.py

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
