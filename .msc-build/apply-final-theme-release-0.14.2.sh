#!/usr/bin/env bash
set -euo pipefail

registry="$(mktemp /tmp/msc-theme-registry-0.14.2.XXXXXX.py)"
premium_installer="${MSC_PREMIUM_THEME_INSTALLER:-.msc-build/install-premium-theme-art-0.14.2.py}"
premium_art_dir="${MSC_PREMIUM_THEME_ART_DIR:-.msc-build/premium-theme-art-0.14.2}"
trap 'rm -f "$registry"' EXIT

[[ -s "$premium_installer" ]] || { echo "Missing premium theme installer: $premium_installer" >&2; exit 1; }
[[ -s "$premium_art_dir/SHA256SUMS.txt" ]] || { echo "Missing premium theme checksum manifest." >&2; exit 1; }

cat .msc-build/theme-registry-0.14.2.part*.pyfrag > "$registry"
python3 -m py_compile "$premium_installer" "$registry"

# Install the reviewed, checksum-locked scenes directly. No network fetch,
# proxy, or expiring export URL is part of the production release path.
python3 "$premium_installer" install-rebuilt "$premium_art_dir"

python3 "$registry"

node --check MyStudyCompanionWeb/appearance.js
node --test MyStudyCompanionWeb/appearance.test.mjs
grep -Fq 'drawWorkbookArt' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
grep -Fq 'renderColorByNumber' MyStudyCompanionWeb/workbook.js

echo 'PASS: 0.14.2 checksum-locked full-scene assets and 25-theme registry installed over the verified 0.14.1 workbook baseline.'
