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
echo '671bfbf88c0d59b5a7d11001eb1cbb3ca32b4e90c390f81d9f1e9e82dd3cab5c  '"$PAYLOAD" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
echo '737dd56fb1af95363d4052aa7d7f9b3d5187a6240554272ec744d34a5fb22507  '"$PATCHER" | sha256sum -c -
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive real-data widget rebuild.'
