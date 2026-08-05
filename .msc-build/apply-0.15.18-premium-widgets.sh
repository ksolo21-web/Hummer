#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Reconstruct the complete accepted source through 0.15.17. This feature layer
# never signs an APK and never deploys or rewrites the working cloud service.
MSC_SMART_ONLINE_VALIDATED="${MSC_SMART_ONLINE_VALIDATED:-false}" \
  bash .msc-build/apply-0.15.17-premium-interactive-paint.sh

PAYLOAD=".msc-build/0.15.18-widget-rebuild.py.gz.b64"
PATCHER=".msc-build/apply-0.15.18-premium-widgets.py"
echo 'fb7fe0f1cadd8b993b02aaa85c77a2100ba2781d48743d0ca5ec0741cc3cefb3  '"$PAYLOAD" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
echo '94c4e67fdb7fa9a4207f52353d9c9a6de3b847cb6dc6f1ba697c6a5d7b7141c6  '"$PATCHER" | sha256sum -c -
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive widget rebuild.'
