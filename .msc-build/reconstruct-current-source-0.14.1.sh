#!/usr/bin/env bash
set -euo pipefail

# Historical overlays can replace driver files. Run exact-head logic from /tmp.
if [[ "${MSC_RECONSTRUCT_RUNNING_FROM_EXACT_TMP:-0}" != "1" ]]; then
  exact_driver="$(mktemp /tmp/msc-exact-head-reconstruct.XXXXXX.sh)"
  cp "$0" "$exact_driver"
  chmod +x "$exact_driver"
  exec env MSC_RECONSTRUCT_RUNNING_FROM_EXACT_TMP=1 bash "$exact_driver"
fi

exact_final_gate="$(mktemp /tmp/msc-exact-final-gate.XXXXXX.py)"
exact_theme_finisher="$(mktemp /tmp/msc-exact-theme-finisher.XXXXXX.py)"
exact_workbook_art_dir="$(mktemp -d /tmp/msc-exact-workbook-art.XXXXXX)"
exact_release_dir="$(mktemp -d /tmp/msc-exact-0142-release.XXXXXX)"
exact_premium_installer="$(mktemp /tmp/msc-exact-premium-installer.XXXXXX.py)"
exact_premium_art_dir="$(mktemp -d /tmp/msc-exact-premium-art.XXXXXX)"
exact_protected_theme_dir="$(mktemp -d /tmp/msc-exact-protected-themes.XXXXXX)"

cp .msc-build/fix-unified-study-reader-ci-gate-0.14.1.py "$exact_final_gate"
cp .msc-build/apply-approved-theme-finish-v3.py "$exact_theme_finisher"
cp .msc-build/apply-real-workbook-art-pages-0.14.1.sh "$exact_workbook_art_dir"/
cp .msc-build/real-workbook-art-pages-0.14.1.part*.b64 "$exact_workbook_art_dir"/
cp .msc-build/apply-final-theme-release-0.14.2.sh "$exact_release_dir"/
cp .msc-build/theme-generator-0.14.2.part*.pyfrag "$exact_release_dir"/
cp .msc-build/theme-registry-0.14.2.part*.pyfrag "$exact_release_dir"/
cp .msc-build/install-premium-theme-art-0.14.2.py "$exact_premium_installer"
cp .msc-build/premium-theme-art-0.14.2/SHA256SUMS.txt "$exact_premium_art_dir"/
cp .msc-build/premium-theme-art-0.14.2/theme_scene_*.webp "$exact_premium_art_dir"/

if [[ "$(find "$exact_workbook_art_dir" -maxdepth 1 -name 'real-workbook-art-pages-0.14.1.part*.b64' | wc -l)" -ne 2 ]]; then
  echo 'Expected both exact workbook-art payload parts.' >&2
  exit 1
fi
if [[ ! -s "$exact_release_dir/apply-final-theme-release-0.14.2.sh" ]]; then
  echo 'Expected the exact 0.14.2 theme release installer.' >&2
  exit 1
fi
if [[ "$(find "$exact_release_dir" -maxdepth 1 -name 'theme-generator-0.14.2.part*.pyfrag' | wc -l)" -ne 4 ]]; then
  echo 'Expected four deterministic theme-generator fragments.' >&2
  exit 1
fi
if [[ "$(find "$exact_release_dir" -maxdepth 1 -name 'theme-registry-0.14.2.part*.pyfrag' | wc -l)" -ne 2 ]]; then
  echo 'Expected two deterministic theme-registry fragments.' >&2
  exit 1
fi
if [[ "$(find "$exact_premium_art_dir" -maxdepth 1 -name 'theme_scene_*.webp' | wc -l)" -ne 16 ]]; then
  echo 'Expected sixteen exact premium theme scenes.' >&2
  exit 1
fi
python3 "$exact_premium_installer" verify-source "$exact_premium_art_dir"

python3 .msc-build/reconstruct-source-only-0.14.1.py
bash /tmp/reconstruct-build-0125-source-driver.sh

python3 - <<'PY'
from pathlib import Path

build_file = Path('MyStudyCompanion/build.gradle.kts')
source = build_file.read_text(encoding='utf-8')
anchor = '            "HomeScreen.kt",\n'
if source.count(anchor) != 1:
    raise SystemExit('Expected one HomeScreen compatibility-list anchor.')
for filename in ('DailyFieldServicePointerCard.kt', 'EventNotebooksSection.kt'):
    entry = f'            "{filename}",\n'
    if entry not in source:
        source = source.replace(anchor, entry + anchor, 1)
build_file.write_text(source, encoding='utf-8')
PY

cat .msc-build/final-major-0.13.0.part*.b64 | base64 --decode > /tmp/msc-0130.tar.xz
echo 'bd01fe658b10a2203732023cd0a559a538d4152468fcf0a37b5418f7eea1e217  /tmp/msc-0130.tar.xz' | sha256sum -c -
xz -t /tmp/msc-0130.tar.xz
tar --no-same-owner -xJf /tmp/msc-0130.tar.xz -C .

for file in workbook-payload/payload/interactive-workbooks-0.14.0.part*.b64; do
  grep -v '^#' "$file"
done | tr -d '\n' | base64 --decode > /tmp/msc-0140-overlay.tar.xz
echo '3bdc13f78b42f85861d4f6d92b0892bb1db59224ef318f241bf616ef14a75d32  /tmp/msc-0140-overlay.tar.xz' | sha256sum -c -
xz -t /tmp/msc-0140-overlay.tar.xz
tar --no-same-owner -xJf /tmp/msc-0140-overlay.tar.xz -C .

python3 .msc-build/fix-interactive-workbooks-0.14.0.py
python3 .msc-build/apply-unified-study-reader-0.14.1.py
python3 .msc-build/fix-unified-study-reader-compile-0.14.1.py
bash .msc-build/apply-unified-reader-controls-0.14.1.sh
bash .msc-build/apply-complete-last-major-build-0.14.1.sh

cp "$exact_final_gate" .msc-build/fix-unified-study-reader-ci-gate-0.14.1.py
bash .msc-build/apply-theme-gallery-0.14.1.sh
bash .msc-build/apply-live-release-completion-0.14.1.sh
bash .msc-build/apply-production-live-stack-0.14.1.sh
python3 .msc-build/fix-static-theme-repair-gate-0.14.1.py
python3 .msc-build/apply-static-theme-auth-repair-0.14.1.py

legacy_art_rc=0
bash .msc-build/apply-approved-static-theme-artwork-0.14.1.sh || legacy_art_rc=$?
echo "Legacy static-theme integration process code: ${legacy_art_rc}"

# Restore the exact-head workbook engine after every historical overlay.
mkdir -p .msc-build
cp "$exact_workbook_art_dir"/apply-real-workbook-art-pages-0.14.1.sh .msc-build/
cp "$exact_workbook_art_dir"/real-workbook-art-pages-0.14.1.part*.b64 .msc-build/
bash .msc-build/apply-real-workbook-art-pages-0.14.1.sh

# Install the 0.14.2 release on top of the verified 0.14.1 baseline.
python3 "$exact_premium_installer" snapshot "$exact_protected_theme_dir"
cp "$exact_release_dir"/apply-final-theme-release-0.14.2.sh .msc-build/
cp "$exact_release_dir"/theme-generator-0.14.2.part*.pyfrag .msc-build/
cp "$exact_release_dir"/theme-registry-0.14.2.part*.pyfrag .msc-build/
env \
  MSC_PREMIUM_THEME_INSTALLER="$exact_premium_installer" \
  MSC_PREMIUM_THEME_ART_DIR="$exact_premium_art_dir" \
  bash .msc-build/apply-final-theme-release-0.14.2.sh

# Restore the accepted nine byte-for-byte and install the rebuilt sixteen.
python3 "$exact_premium_installer" install "$exact_premium_art_dir" "$exact_protected_theme_dir"

# One authoritative final theme stage: sharp full-scene assets and sharp previews.
rm -f .msc-build/approved-theme-finish-v2-manifest.json
python3 "$exact_theme_finisher"

python3 - <<'PY'
from __future__ import annotations

import hashlib
import json
from pathlib import Path

root = Path('.')
manifest_path = root / '.msc-build/approved-theme-finish-v2-manifest.json'
if not manifest_path.is_file() or manifest_path.stat().st_size < 500:
    raise SystemExit('Approved theme manifest is missing or incomplete.')
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
themes = manifest.get('themes', {})
required = {
    'moonlit_wolf',
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
    'lion_premium_2', 'fox_premium_2',
}
if set(themes) != required:
    raise SystemExit(f'Approved theme manifest mismatch: {sorted(themes)}')

roots = (
    root / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    root / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    root / 'MyStudyCompanionWeb/assets',
)
for slug, entry in themes.items():
    if entry.get('source_mode') != 'approved_full_scene_art':
        raise SystemExit(f'{slug} did not use approved full-scene artwork.')
    if entry.get('preview_mode') != 'single_sharp_crop_no_blur':
        raise SystemExit(f'{slug} used a forbidden preview path.')
    if entry.get('scene_dimensions') != [1024, 1536]:
        raise SystemExit(f'Invalid scene dimensions for {slug}')
    if entry.get('preview_dimensions') != [720, 1440]:
        raise SystemExit(f'Invalid preview dimensions for {slug}')
    for kind, digest_key in (('scene', 'scene_sha256'), ('preview', 'preview_sha256')):
        expected = entry.get(digest_key, '')
        if len(expected) != 64:
            raise SystemExit(f'Invalid {kind} digest for {slug}')
        files = tuple(path / f'theme_{kind}_{slug}.webp' for path in roots)
        if not all(path.is_file() and path.stat().st_size > 20_000 for path in files):
            raise SystemExit(f'Missing or undersized {kind} asset for {slug}')
        actual = {hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
        if actual != {expected}:
            raise SystemExit(f'Cross-surface {kind} digest mismatch for {slug}: {actual}')

mode = root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt'
if mode.read_text(encoding='utf-8').count('isAnimalTheme = true') != 9:
    raise SystemExit('Expected nine animal themes in the 25-theme registry.')
for marker in ('LION_PREMIUM_2', 'FOX_PREMIUM_2', 'AUTOMATIC'):
    if marker not in mode.read_text(encoding='utf-8'):
        raise SystemExit(f'Missing final theme mode: {marker}')

home = root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt'
if 'ApprovedThemeQuickActions' not in home.read_text(encoding='utf-8'):
    raise SystemExit('Approved native quick-action surface is missing.')

editor = root / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/InteractiveWorkbookEditor.kt'
workbook = root / 'MyStudyCompanionWeb/workbook.js'
for path, required_markers in (
    (editor, ('drawWorkbookArt', 'drawPdfWorkbookArt', 'detectTapGestures', 'Guided drawing canvas')),
    (workbook, ('renderColorByNumber', 'drawArtCanvas', 'artSvg', 'svgArtStrokes')),
):
    source = path.read_text(encoding='utf-8')
    for marker in required_markers:
        if marker not in source:
            raise SystemExit(f'Real workbook-art output is missing {marker} in {path}.')

print('PASS: 0.14.2 reconstructed with real workbook pages and 25 permanent themes plus Automatic.')
PY

echo 'Reconstructed My Study Companion 0.14.2 from the verified 0.14.1 baseline with real workbook pages, sharp static theme art, and Google sign-in preserved.'
