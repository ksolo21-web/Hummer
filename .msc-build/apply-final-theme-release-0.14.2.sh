#!/usr/bin/env bash
set -euo pipefail

workdir="$(mktemp -d /tmp/msc-final-theme-scenes-0.14.2.XXXXXX)"
generator="$(mktemp /tmp/msc-theme-generator-0.14.2.XXXXXX.py)"
registry="$(mktemp /tmp/msc-theme-registry-0.14.2.XXXXXX.py)"

cat .msc-build/theme-generator-0.14.2.part*.pyfrag > "$generator"
cat .msc-build/theme-registry-0.14.2.part*.pyfrag > "$registry"
python3 -m py_compile "$generator" "$registry"
python3 "$generator" "$workdir"

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
  source="$workdir/theme_scene_${slug}.webp"
  [[ -s "$source" ]] || { echo "Missing final scene: $source" >&2; exit 1; }
  cp "$source" "$primary/"
done

python3 "$registry"

node --check MyStudyCompanionWeb/appearance.js
node --test MyStudyCompanionWeb/appearance.test.mjs
grep -Fq 'drawWorkbookArt' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt
grep -Fq 'renderColorByNumber' MyStudyCompanionWeb/workbook.js

echo 'PASS: 0.14.2 full-scene theme assets and 25-theme registry installed over the verified 0.14.1 workbook baseline.'
