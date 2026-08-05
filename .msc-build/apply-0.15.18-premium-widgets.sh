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
echo '4afa9d5b6f628afdbc4c116e3480f2bff249b142b2bbc84daa30c538c8455182  '"$PAYLOAD" | sha256sum -c -
base64 --decode "$PAYLOAD" | gzip -dc > "$PATCHER"
echo '708050bed229100d9fd7ca9c496c72fa22212bf69efa60cefa3309588bc3daf6  '"$PATCHER" | sha256sum -c -
python3 -m py_compile "$PATCHER"
python3 "$PATCHER"

echo 'Applied My Study Companion 0.15.18 premium responsive real-data widget rebuild.'
