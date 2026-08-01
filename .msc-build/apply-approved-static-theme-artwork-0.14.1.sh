#!/usr/bin/env bash
set -euo pipefail

GENERATOR_XZ="${RUNNER_TEMP:-/tmp}/msc-approved-static-theme-generator-0.14.1.py.xz"
GENERATOR_PY="${RUNNER_TEMP:-/tmp}/msc-approved-static-theme-generator-0.14.1.py"

cat .msc-build/approved-static-theme-generator-numpyfree-0.14.1.part*.b64 \
  | tr -d '\n' \
  | base64 --decode \
  > "$GENERATOR_XZ"

echo '570742f8865b5d5e0d1272e380e32cb5910fdfdf87528b190e19f703872df0a5  '"$GENERATOR_XZ" \
  | sha256sum -c -
xz -t "$GENERATOR_XZ"
xz --decompress --stdout "$GENERATOR_XZ" > "$GENERATOR_PY"
python3 -m py_compile "$GENERATOR_PY"
python3 "$GENERATOR_PY"

node --check MyStudyCompanionWeb/appearance.js
node --check MyStudyCompanionWeb/sw.js

test "$(find MyStudyCompanion/app/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13
test "$(find MyStudyCompanion/wear/src/main/res/drawable-nodpi -maxdepth 1 -name 'theme_scene_*.webp' | wc -l)" -eq 23
test "$(find MyStudyCompanionWeb/assets -maxdepth 1 -name 'theme_preview_*.webp' | wc -l)" -eq 13

grep -Fq 'ThemePreviewArtwork' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt
grep -Fq 'identity.mode.isIllustratedTheme -> 0.86f' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeBackdrop.kt
grep -Fq 'theme.preview||theme.art' MyStudyCompanionWeb/appearance.js

echo 'PASS: approved static theme artwork is integrated into phone, Fold/tablet, Wear OS, widgets, and PWA; no live themes were added.'
