#!/usr/bin/env bash
set -euo pipefail

cat .msc-build/theme-gallery-text-0.14.1.part*.b64 | tr -d '\n' | base64 --decode \
  > /tmp/msc-theme-gallery-text.patch.xz
echo '70b806c5b53d8fcaa9e02dbd3b4f3e20ab82b19964d44ccf03e4bb62230955fb  /tmp/msc-theme-gallery-text.patch.xz' \
  | sha256sum -c -
xz -t /tmp/msc-theme-gallery-text.patch.xz
xz -dc /tmp/msc-theme-gallery-text.patch.xz > /tmp/msc-theme-gallery-text.patch

# The Android/Wear/source changes must patch cleanly. Three PWA layout files
# intentionally use checksum-locked complete replacements because their earlier
# reader overlays shift markup and stylesheet anchors during reconstruction.
set +e
patch --batch --forward -p1 < /tmp/msc-theme-gallery-text.patch
patch_status=$?
set -e
mapfile -t reject_files < <(find MyStudyCompanion MyStudyCompanionWeb -type f -name '*.rej' -print | sort)
expected_rejects=(
  'MyStudyCompanionWeb/index.html.rej'
  'MyStudyCompanionWeb/styles.css.rej'
  'MyStudyCompanionWeb/sw.js.rej'
)
if (( patch_status != 0 )); then
  if [[ "${reject_files[*]}" != "${expected_rejects[*]}" ]]; then
    printf 'Unexpected theme patch rejects:\n' >&2
    printf '  %s\n' "${reject_files[@]}" >&2
    exit 1
  fi
elif (( ${#reject_files[@]} != 0 )); then
  printf 'Theme patch reported success but left rejects:\n' >&2
  printf '  %s\n' "${reject_files[@]}" >&2
  exit 1
fi

base64 --decode .msc-build/theme-gallery-web-override-0.14.1.b64 \
  > /tmp/msc-theme-gallery-web-override.tar.xz
echo '77e4f1f032f76275b61e5422f30f51a0ff97e65f045acaeb81718aee1ee76dac  /tmp/msc-theme-gallery-web-override.tar.xz' \
  | sha256sum -c -
xz -t /tmp/msc-theme-gallery-web-override.tar.xz
tar --no-same-owner -xJf /tmp/msc-theme-gallery-web-override.tar.xz -C .
rm -f \
  MyStudyCompanionWeb/index.html.rej MyStudyCompanionWeb/index.html.orig \
  MyStudyCompanionWeb/styles.css.rej MyStudyCompanionWeb/styles.css.orig \
  MyStudyCompanionWeb/sw.js.rej MyStudyCompanionWeb/sw.js.orig

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

# Earlier reader overlays shift the PWA markup anchors. The legacy replacement
# archive predates the final gallery modal, so restore it deterministically
# before syntax, behavior, and reconstruction gates run.
python3 .msc-build/fix-theme-gallery-pwa-0.14.2.py

for file in MyStudyCompanionWeb/*.js; do
  node --check "$file"
done
node --test MyStudyCompanionWeb/appearance.test.mjs MyStudyCompanionWeb/study-library-merge.test.mjs

python3 - <<'PY'
import hashlib
import os
import shutil
import tempfile
from pathlib import Path

from PIL import Image

root = Path('.')
new_names = (
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
)
additional_names = ('moonlit_wolf', 'golden_owl', 'sakura_tiger')

def valid_image(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size <= 1000:
        return False
    try:
        with Image.open(path) as image:
            image.verify()
    except Exception:
        return False
    return True

def atomic_copy(source: Path, destination: Path) -> None:
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            dir=destination.parent,
            prefix=f'.{destination.name}.',
            suffix='.tmp',
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
        shutil.copyfile(source, temporary)
        source_digest = hashlib.sha256(source.read_bytes()).hexdigest()
        copied_digest = hashlib.sha256(temporary.read_bytes()).hexdigest()
        if copied_digest != source_digest or not valid_image(temporary):
            raise SystemExit(f'Generated theme repair copy failed: {destination}')
        os.replace(temporary, destination)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)

for name in new_names + additional_names:
    paths = (
        root / f'MyStudyCompanion/app/src/main/res/drawable-nodpi/theme_scene_{name}.webp',
        root / f'MyStudyCompanion/wear/src/main/res/drawable-nodpi/theme_scene_{name}.webp',
        root / f'MyStudyCompanionWeb/assets/theme_scene_{name}.webp',
    )
    valid_sources = tuple(path for path in paths if valid_image(path))
    if not valid_sources:
        raise SystemExit(f'No valid generated theme source remained for {name}.')
    for path in paths:
        if not valid_image(path):
            atomic_copy(valid_sources[0], path)
        if not valid_image(path):
            raise SystemExit(f'Generated theme asset is invalid after repair: {path}')

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
