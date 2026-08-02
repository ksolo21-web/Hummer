#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path('.')
MANIFEST = ROOT / '.msc-build/approved-theme-finish-v2-manifest.json'
THEMES = (
    'moonlit_wolf',
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
    'lion_premium_2', 'fox_premium_2',
)
TARGETS = (
    ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanionWeb/assets',
)

def dimensions(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        return image.size

def create_preview(scene: Path, preview: Path) -> None:
    preview.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(scene) as opened:
        source = opened.convert('RGB')
    # Selector previews use one deliberate high-quality crop from the full scene.
    # No blurred enlargement, mockup frame, duplicate foreground, or filler layer.
    fitted = ImageOps.fit(
        source,
        (720, 1440),
        method=Image.Resampling.LANCZOS,
        centering=(0.5, 0.47),
    )
    fitted.save(preview, 'WEBP', quality=94, method=6)

primary = TARGETS[0]
for target in TARGETS:
    target.mkdir(parents=True, exist_ok=True)

manifest: dict[str, object] = {
    'source': 'Kaleb-approved 0.14.2 full-scene artwork; one deterministic final stage',
    'themes': {},
}

for slug in THEMES:
    primary_scene = primary / f'theme_scene_{slug}.webp'
    if not primary_scene.is_file() or primary_scene.stat().st_size <= 20_000:
        raise SystemExit(f'Approved scene is missing or undersized: {primary_scene}')

    scene_size = dimensions(primary_scene)
    if scene_size != (1024, 1536):
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
        'scene_dimensions': [1024, 1536],
        'preview_dimensions': [720, 1440],
        'source_mode': 'approved_full_scene_art',
        'preview_mode': 'single_sharp_crop_no_blur',
    }

    for kind, expected in (('scene', scene_digest), ('preview', preview_digest)):
        copies = tuple(target / f'theme_{kind}_{slug}.webp' for target in TARGETS)
        actual = {hashlib.sha256(path.read_bytes()).hexdigest() for path in copies}
        if actual != {expected}:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {actual}')

MANIFEST.parent.mkdir(parents=True, exist_ok=True)
MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')

auth = ROOT / 'MyStudyCompanionWeb/firebase-sync.js'
auth_source = auth.read_text(encoding='utf-8')
for required in ('browserLocalPersistence', 'getRedirectResult', 'signInWithPopup', 'auth/popup-blocked'):
    if required not in auth_source:
        raise SystemExit(f'Google sign-in repair was lost: {required}')

print('PASS: 16 rebuilt/new themes finalized as sharp full-scene art with no blurred preview filler.')
