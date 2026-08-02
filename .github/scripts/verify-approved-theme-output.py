#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import json
import subprocess
import zlib
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
PALETTE_SOURCES = {
    'creation_garden': (ROOT / '.msc-build/approved-theme-crop-creation_garden-60x120-pal128-zlib.b64', 'c2b107d9bb606a8d4e231f66f68c84c22a8e9076e6f5dce3c54f2dcf52e8ca8b'),
    'bible_sketch_study': (ROOT / '.msc-build/approved-theme-crop-bible_sketch_study-60x120-pal128-zlib.b64', '6ff801b4b403b4df648be9a26d5046ec343e82f7ac72c8eae0b7b00b9bd8c3ec'),
    'parable_line_panels': (ROOT / '.msc-build/approved-theme-crop-parable_line_panels-60x120-pal128-zlib.b64', '5b0e8a2f43b4118c4bbee618757113a472c0d5ebd5988033e8814966ba890a79'),
    'noahs_ark': (ROOT / '.msc-build/approved-theme-crop-noahs_ark-60x120-pal128-zlib.b64', 'faa78961f9948ebd3672c5f13ae21b8562e73660b9594300a774383ecf0955c6'),
    'red_sea_deliverance': (ROOT / '.msc-build/approved-theme-crop-red_sea_deliverance-60x120-pal128-zlib.b64', '262d381bb61fae5b0c710e4d9b98d520a7c082af747f055acdf6ff47ec8c07f9'),
    'creation_sky': (ROOT / '.msc-build/approved-theme-crop-creation_sky-60x120-pal128-zlib.b64', '3e03dbe7fee30488da7bd4da4cc1c311900dccd6fe8e1b5ec253e59f5600c8ca'),
    'bible_timeline': (ROOT / '.msc-build/approved-theme-crop-bible_timeline-60x120-pal128-zlib.b64', 'e9b9a1c0a609e74c8eb8e411ce3b6f8e151954d6987dc5f677e111790daacc43'),
    'bible_map': (ROOT / '.msc-build/approved-theme-crop-bible_map-60x120-pal128-zlib.b64', '269d798f23da0aaeca37886904d7d3c4f39747edbdf25d77691b75a1d0939c7c'),
}

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

for slug, (source_path, expected_source_sha) in PALETTE_SOURCES.items():
    if themes[slug].get('source_mode') != 'palette':
        raise SystemExit(f'{slug} did not use the decoder-free palette source.')
    compressed = base64.b64decode(source_path.read_text(encoding='ascii').strip(), validate=True)
    actual_source_sha = hashlib.sha256(compressed).hexdigest()
    if actual_source_sha != expected_source_sha:
        raise SystemExit(f'{slug} palette checksum mismatch: {actual_source_sha}')
    raw = zlib.decompress(compressed)
    if len(raw) != 128 * 3 + 60 * 120:
        raise SystemExit(f'{slug} palette payload length mismatch: {len(raw)}')
    palette = raw[:384]
    colors = tuple(palette[offset:offset + 3] for offset in range(0, 384, 3))
    expected_pixels = b''.join(colors[index] for index in raw[384:])
    preview = TARGETS[2] / f'theme_preview_{slug}.webp'
    decoded = subprocess.run([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-i', str(preview),
        '-vf', 'crop=690:1380:15:30,scale=60:120:flags=area',
        '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-',
    ], check=True, stdout=subprocess.PIPE).stdout
    if len(decoded) != len(expected_pixels):
        raise SystemExit(f'{slug} independent decode length mismatch: {len(decoded)}')
    mae = sum(abs(a - b) for a, b in zip(expected_pixels, decoded)) / len(decoded)
    if mae > 12.0:
        raise SystemExit(f'{slug} visible corruption detected independently: MAE={mae:.3f}')

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

print('PASS: all 13 approved themes are static, byte-identical, and independently free of hosted block corruption.')
