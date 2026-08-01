#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/theme-gallery-text-0.14.1.part*.b64 | tr -d '\n' | base64 --decode \
  > /tmp/msc-theme-gallery-text.patch.xz
echo '70b806c5b53d8fcaa9e02dbd3b4f3e20ab82b19964d44ccf03e4bb62230955fb  /tmp/msc-theme-gallery-text.patch.xz' \
  | sha256sum -c -
xz -t /tmp/msc-theme-gallery-text.patch.xz
xz -dc /tmp/msc-theme-gallery-text.patch.xz > /tmp/msc-theme-gallery-text.patch
patch --batch --forward -p1 < /tmp/msc-theme-gallery-text.patch

cat .msc-build/theme-gallery-generator-0.14.1.part*.b64 | tr -d '\n' | base64 --decode \
  > /tmp/msc-theme-gallery-generator.py.xz
echo '26513baf293b7f09feee19c5ed13bfb84182f137291488741de3fa0c3102619a  /tmp/msc-theme-gallery-generator.py.xz' \
  | sha256sum -c -
xz -t /tmp/msc-theme-gallery-generator.py.xz
xz -dc /tmp/msc-theme-gallery-generator.py.xz > /tmp/msc-theme-gallery-generator.py
python3 -c 'import PIL' || {
  echo 'Pillow is required to generate the original theme scenery.' >&2
  exit 1
}
python3 /tmp/msc-theme-gallery-generator.py

for file in MyStudyCompanionWeb/*.js; do
  node --check "$file"
done
node --test MyStudyCompanionWeb/appearance.test.mjs MyStudyCompanionWeb/study-library-merge.test.mjs

python3 - <<'PY'
from pathlib import Path

root = Path('.')
new_names = (
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
)
additional_names = ('moonlit_wolf', 'golden_owl', 'sakura_tiger')
for name in new_names + additional_names:
    for path in (
        root / f'MyStudyCompanion/app/src/main/res/drawable-nodpi/theme_scene_{name}.webp',
        root / f'MyStudyCompanion/wear/src/main/res/drawable-nodpi/theme_scene_{name}.webp',
        root / f'MyStudyCompanionWeb/assets/theme_scene_{name}.webp',
    ):
        assert path.is_file() and path.stat().st_size > 1000, path

mode = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt').read_text()
assert mode.count('isIllustratedTheme = true') == 13
assert 'BIBLE_MAP("Bible Map"' in mode
art = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/ThemeArtwork.kt').read_text()
for name in new_names:
    assert f'theme_scene_{name}' in art, name
widget = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt').read_text()
for enum_name in (
    'WATERFALL_SERENITY', 'RAINFOREST_HARMONY', 'OCEAN_MAJESTY',
    'CELESTIAL_WONDER', 'MOUNTAIN_SUNRISE', 'CREATION_GARDEN',
    'BIBLE_SKETCH_STUDY', 'PARABLE_LINE_PANELS', 'NOAHS_ARK',
    'RED_SEA_DELIVERANCE', 'CREATION_SKY', 'BIBLE_TIMELINE', 'BIBLE_MAP',
):
    assert f'AppThemeMode.{enum_name} -> WidgetRawPalette' in widget, enum_name
settings = (root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt').read_text()
assert 'ColorWheelDialog' in settings
assert 'No color code is required.' in settings
assert 'Exact hex' not in settings
html = (root / 'MyStudyCompanionWeb/index.html').read_text()
for marker in ('appearanceButton', 'appearanceThemeGrid', 'appearanceColorWheel', 'appearanceBrightness', 'FAMILY HUB', 'readerModal'):
    assert marker in html, marker
assert 'type="text" data-color-role' not in html
assert 'msc-web-v0143-theme-gallery' in (root / 'MyStudyCompanionWeb/sw.js').read_text()

# Extend the legacy final audit so the theme gallery cannot be omitted silently.
gate = Path('.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py')
source = gate.read_text(encoding='utf-8')
source = source.replace('msc-web-v0142-complete-reader', 'msc-web-v0143-theme-gallery')
needle = "  grep -Fq 'my-study-companion-private' MyStudyCompanionWeb/firebase.json\n"
extra = '''  test -s MyStudyCompanionWeb/appearance.js
  test -s MyStudyCompanionWeb/appearance.test.mjs
  grep -Fq 'appearanceColorWheel' MyStudyCompanionWeb/index.html
  grep -Fq 'appearanceThemeGrid' MyStudyCompanionWeb/index.html
  grep -Fq 'Waterfall Serenity' MyStudyCompanionWeb/appearance.js
  grep -Fq 'Bible Map' MyStudyCompanionWeb/appearance.js
  grep -Fq 'BIBLE_MAP("Bible Map"' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt
  grep -Fq 'ColorWheelDialog' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt
  ! grep -Fq 'Exact hex' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt
'''
if extra not in source:
    if needle not in source:
        raise SystemExit('Could not find final web gate insertion point.')
    source = source.replace(needle, needle + extra, 1)
source = source.replace(
    '    "my-study-companion-private",\n',
    '    "my-study-companion-private",\n    "msc-web-v0143-theme-gallery",\n    "appearanceColorWheel",\n    "Waterfall Serenity",\n    "BIBLE_MAP",\n    "ColorWheelDialog",\n',
)
source = source.replace(
    '    "msc-web-v0141-unified-study-reader",\n',
    '    "msc-web-v0141-unified-study-reader",\n    "msc-web-v0142-complete-reader",\n',
)
gate.write_text(source, encoding='utf-8')
print('PASS: 23 permanent themes, 13 new illustrated themes, Android/Web color wheels, Wear scenery, widgets, and PWA cache are present.')
PY

echo 'Applied My Study Companion 0.14.1 expanded theme gallery and visual color-wheel layer.'
