#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path('.')
MANIFEST = ROOT / '.msc-build/approved-theme-finish-v2-manifest.json'
REQUIRED = {
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
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
    if entry.get('scene_dimensions') != [1200, 2400]:
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

home = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HomeScreen.kt'
settings = ROOT / 'MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/SettingsScreen.kt'
appearance = ROOT / 'MyStudyCompanionWeb/appearance.js'
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

print('PASS: all 13 approved themes are complete, static, and byte-identical across phone, Wear OS, and PWA.')
