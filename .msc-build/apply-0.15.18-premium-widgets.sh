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
echo 'e272c677ba2e183c44a6ad935d9aa54b8cf8c164a64fd72d6497824a987fd473  '"$PAYLOAD" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
echo '8948a67c8f01c823910b9b56548d107e42954971638a8d925833a08dd1d51e6f  '"$PATCHER" | sha256sum -c -
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive widget rebuild.'
