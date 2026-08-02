#!/usr/bin/env bash
set -euo pipefail

workdir="$(mktemp -d /tmp/msc-final-theme-scenes-0.14.2.XXXXXX)"
registry="$(mktemp /tmp/msc-theme-registry-0.14.2.XXXXXX.py)"
trap 'rm -rf "$workdir" "$registry"' EXIT

required_builder_files=(
  .msc-build/build-approved-theme-source-artwork-0.14.2-v2.py
  .msc-build/build-approved-theme-source-artwork-0.14.2-v8.py
  .msc-build/build-approved-theme-source-artwork-0.14.2-v9.py
)
for file in "${required_builder_files[@]}"; do
  [[ -s "$file" ]] || { echo "Missing approved v9 theme builder dependency: $file" >&2; exit 1; }
done

cat .msc-build/theme-registry-0.14.2.part*.pyfrag > "$registry"
python3 -m py_compile "${required_builder_files[@]}" "$registry"
python3 - <<'PY'
from PIL import Image
print(f'Pillow ready for approved theme rendering: {Image.__version__ if hasattr(Image, "__version__") else "installed"}')
PY

# Generate the exact full-scene assets that passed the v9 visual contact-sheet
# review. The rejected flat Pillow illustration fragments are deliberately not
# consulted anywhere in this release path.
python3 .msc-build/build-approved-theme-source-artwork-0.14.2-v9.py "$workdir"

test -s "$workdir/msc-0.14.2-theme-art-qc-contact-sheet.jpg"
test -s "$workdir/SOURCE-CREDITS.json"
test -s "$workdir/SHA256SUMS.txt"
grep -Fq 'no blurred crop-fill' "$workdir/SOURCE-CREDITS.json"
grep -Fq 'accepted first nine themes are untouched' "$workdir/SOURCE-CREDITS.json"
(
  cd "$workdir/scenes"
  sha256sum -c ../SHA256SUMS.txt
)

primary="MyStudyCompanion/app/src/main/res/drawable-nodpi"
mkdir -p "$primary"
required=(
  moonlit_wolf
  waterfall_serenity rainforest_harmony ocean_majesty
  celestial_wonder mountain_sunrise creation_garden
  bible_sketch_study parable_line_panels noahs_ark
  red_sea_deliverance creation_sky bible_timeline bible_map
  lion_premium_2 fox_premium_2
)
for slug in "${required[@]}"; do
  source="$workdir/scenes/theme_scene_${slug}.webp"
  [[ -s "$source" ]] || { echo "Missing approved full-scene theme: $source" >&2; exit 1; }
  cp "$source" "$primary/"
done

# Keep auditable visual-QC evidence with the reconstructed source without
# exposing it as runtime UI artwork.
mkdir -p .msc-build/theme-art-qc-0.14.2
cp "$workdir/msc-0.14.2-theme-art-qc-contact-sheet.jpg" .msc-build/theme-art-qc-0.14.2/
cp "$workdir/SOURCE-CREDITS.json" .msc-build/theme-art-qc-0.14.2/
cp "$workdir/SHA256SUMS.txt" .msc-build/theme-art-qc-0.14.2/

python3 "$registry"

node --check MyStudyCompanionWeb/appearance.js
node --test MyStudyCompanionWeb/appearance.test.mjs
grep -Fq 'drawWorkbookArt' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
grep -Fq 'renderColorByNumber' MyStudyCompanionWeb/workbook.js

echo 'PASS: 0.14.2 visually reviewed full-scene assets and 25-theme registry installed over the verified 0.14.1 workbook baseline.'
