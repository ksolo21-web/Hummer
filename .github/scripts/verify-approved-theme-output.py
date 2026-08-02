#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path('.')
MANIFEST = ROOT / '.msc-build/approved-theme-finish-v2-manifest.json'
REQUIRED = {
    'moonlit_wolf',
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
    'lion_premium_2', 'fox_premium_2',
}
TARGETS = (
    ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanionWeb/assets',
)

if not MANIFEST.is_file() or MANIFEST.stat().st_size < 500:
    raise SystemExit('Approved theme manifest is missing or incomplete.')
payload = json.loads(MANIFEST.read_text(encoding='utf-8'))
themes = payload.get('themes', {})
if set(themes) != REQUIRED:
    raise SystemExit(f'Approved theme manifest mismatch: {sorted(themes)}')

for slug, entry in themes.items():
    if entry.get('source_mode') != 'approved_full_scene_art':
        raise SystemExit(f'{slug} did not use approved full-scene artwork.')
    if entry.get('preview_mode') != 'single_sharp_crop_no_blur':
        raise SystemExit(f'{slug} did not use the no-blur preview path.')
    if entry.get('scene_dimensions') != [1024, 1536]:
        raise SystemExit(f'Invalid scene dimensions for {slug}')
    if entry.get('preview_dimensions') != [720, 1440]:
        raise SystemExit(f'Invalid preview dimensions for {slug}')
    for kind, digest_key in (('scene', 'scene_sha256'), ('preview', 'preview_sha256')):
        expected = entry.get(digest_key, '')
        if len(expected) != 64:
            raise SystemExit(f'Invalid {kind} digest for {slug}')
        files = tuple(root / f'theme_{kind}_{slug}.webp' for root in TARGETS)
        if not all(path.is_file() and path.stat().st_size > 20_000 for path in files):
            raise SystemExit(f'Missing or undersized {kind} asset for {slug}')
        actual = {hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
        if actual != {expected}:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {actual}')

mode = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/design/AppThemeMode.kt'
wear = ROOT / 'MyStudyCompanion/wear/src/main/java/com/mystudycompanion/app/wear/WearThemeArtwork.kt'
appearance = ROOT / 'MyStudyCompanionWeb/appearance.js'
for path, markers in (
    (mode, ('LION_PREMIUM_2', 'FOX_PREMIUM_2', 'AUTOMATIC')),
    (wear, ('theme_scene_lion_premium_2', 'theme_scene_fox_premium_2')),
    (appearance, ('Lion — Premium II', 'Fox — Premium II')),
):
    source = path.read_text(encoding='utf-8')
    for marker in markers:
        if marker not in source:
            raise SystemExit(f'Missing 25-theme registry marker {marker} in {path}')

home = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt'
settings = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt'
styles = ROOT / 'MyStudyCompanionWeb/styles.css'
for path in (home, settings, appearance, styles):
    if not path.is_file():
        raise SystemExit(f'Required theme surface is missing: {path}')
    source = path.read_text(encoding='utf-8')
    for forbidden in ('rememberInfiniteTransition', 'infiniteRepeatable', 'isLiveTheme', 'liveTheme'):
        if forbidden in source:
            raise SystemExit(f'Live-theme implementation is forbidden: {forbidden} in {path}')
if 'ApprovedThemeQuickActions' not in home.read_text(encoding='utf-8'):
    raise SystemExit('Approved native quick-action surface is missing.')

auth = ROOT / 'MyStudyCompanionWeb/firebase-sync.js'
auth_source = auth.read_text(encoding='utf-8')
for required in ('browserLocalPersistence', 'getRedirectResult', 'signInWithPopup', 'auth/popup-blocked'):
    if required not in auth_source:
        raise SystemExit(f'Google sign-in repair was lost: {required}')

print('PASS: 25 permanent themes plus Automatic are registered; 16 rebuilt/new themes are sharp, static, and byte-identical across all surfaces.')
