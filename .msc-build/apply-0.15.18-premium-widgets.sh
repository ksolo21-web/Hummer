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

test -s "$PAYLOAD"
printf '0.15.18 widget payload sha256: '
sha256sum "$PAYLOAD" | cut -d' ' -f1
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
test -s "$PATCHER"
printf '0.15.18 widget patcher sha256: '
sha256sum "$PATCHER" | cut -d' ' -f1
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive real-data widget rebuild.'
