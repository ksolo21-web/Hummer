#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path('.')
MANIFEST = ROOT / '.msc-build/approved-theme-finish-v2-manifest.json'
THEMES = (
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
)
TARGETS = (
    ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanionWeb/assets',
)


def dimensions(path: Path) -> tuple[int, int]:
    result = subprocess.run(
        [
            'ffprobe', '-v', 'error', '-select_streams', 'v:0',
            '-show_entries', 'stream=width,height', '-of', 'csv=s=x:p=0',
            str(path),
        ],
        check=True,
        stdout=subprocess.PIPE,
        text=True,
    ).stdout.strip()
    width, height = result.split('x', 1)
    return int(width), int(height)


def create_preview(scene: Path, preview: Path) -> None:
    preview.parent.mkdir(parents=True, exist_ok=True)
    graph = (
        '[0:v]split=2[background_source][foreground_source];'
        '[background_source]scale=720:1440:force_original_aspect_ratio=increase,'
        'crop=720:1440,gblur=sigma=20,eq=brightness=-0.10[background];'
        '[foreground_source]scale=690:1380:force_original_aspect_ratio=decrease[foreground];'
        '[background]drawbox=x=23:y=40:w=690:h=1380:color=black@0.34:t=fill[shadow];'
        '[shadow][foreground]overlay=(W-w)/2:(H-h)/2:format=auto[out]'
    )
    subprocess.run(
        [
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-i', str(scene), '-filter_complex', graph, '-map', '[out]',
            '-frames:v', '1', '-c:v', 'libwebp', '-quality', '94',
            '-compression_level', '4', str(preview),
        ],
        check=True,
    )


primary = TARGETS[0]
for target in TARGETS:
    target.mkdir(parents=True, exist_ok=True)

manifest: dict[str, object] = {
    'source': 'Kaleb-approved static artwork archive; single deterministic final stage',
    'themes': {},
}

for slug in THEMES:
    primary_scene = primary / f'theme_scene_{slug}.webp'
    if not primary_scene.is_file() or primary_scene.stat().st_size <= 20_000:
        raise SystemExit(f'Approved scene is missing or undersized: {primary_scene}')

    scene_size = dimensions(primary_scene)
    if scene_size != (1200, 2400):
        raise SystemExit(f'Approved scene dimensions changed: {slug}={scene_size}')

    primary_preview = primary / f'theme_preview_{slug}.webp'
    create_preview(primary_scene, primary_preview)
    preview_size = dimensions(primary_preview)
    if preview_size != (720, 1440):
        raise SystemExit(f'Approved preview dimensions changed: {slug}={preview_size}')
    if primary_preview.stat().st_size <= 20_000:
        raise SystemExit(f'Approved preview is undersized: {primary_preview}')

    for target in TARGETS[1:]:
        shutil.copyfile(primary_scene, target / primary_scene.name)
        shutil.copyfile(primary_preview, target / primary_preview.name)

    scene_digest = hashlib.sha256(primary_scene.read_bytes()).hexdigest()
    preview_digest = hashlib.sha256(primary_preview.read_bytes()).hexdigest()
    manifest['themes'][slug] = {
        'scene_sha256': scene_digest,
        'preview_sha256': preview_digest,
        'scene_dimensions': [1200, 2400],
        'preview_dimensions': [720, 1440],
        'source_mode': 'approved_static_archive',
    }

    for kind, expected in (('scene', scene_digest), ('preview', preview_digest)):
        copies = tuple(target / f'theme_{kind}_{slug}.webp' for target in TARGETS)
        actual = {hashlib.sha256(path.read_bytes()).hexdigest() for path in copies}
        if actual != {expected}:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {actual}')

MANIFEST.parent.mkdir(parents=True, exist_ok=True)
MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')

# Theme finishing must never rewrite the working sign-in lifecycle.
auth = ROOT / 'MyStudyCompanionWeb/firebase-sync.js'
auth_source = auth.read_text(encoding='utf-8')
for required in ('browserLocalPersistence', 'getRedirectResult', 'signInWithPopup', 'auth/popup-blocked'):
    if required not in auth_source:
        raise SystemExit(f'Google sign-in repair was lost: {required}')

print('PASS: approved static artwork finalized once; previews and manifest are byte-identical across phone, Wear OS, and PWA.')
