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
echo '2ba65625840cd33a741144d43982e376c93adb51a8d0270fde89836db066ffac  '"$PAYLOAD" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
echo '70bc79a69de152a04576b39c09922eafeb0789bd866f2341b5927c5370c66be9  '"$PATCHER" | sha256sum -c -
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive widget rebuild.'
