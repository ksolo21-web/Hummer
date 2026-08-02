#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

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
    with Image.open(path) as image:
        return image.size


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_width, target_height = size
    source_width, source_height = image.size
    scale = max(target_width / source_width, target_height / source_height)
    resized = image.resize(
        (round(source_width * scale), round(source_height * scale)),
        Image.Resampling.LANCZOS,
    )
    left = (resized.width - target_width) // 2
    top = (resized.height - target_height) // 2
    return resized.crop((left, top, left + target_width, top + target_height))


def contain(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    copy = image.copy()
    copy.thumbnail(size, Image.Resampling.LANCZOS)
    return copy


def create_preview(scene: Path, preview: Path) -> None:
    preview.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(scene) as opened:
        source = opened.convert('RGB')

    background = cover(source, (720, 1440))
    background = background.filter(ImageFilter.GaussianBlur(radius=20))
    background = ImageEnhance.Brightness(background).enhance(0.90).convert('RGBA')

    foreground = contain(source, (690, 1380)).convert('RGBA')
    x = (720 - foreground.width) // 2
    y = (1440 - foreground.height) // 2

    shadow = Image.new('RGBA', background.size, (0, 0, 0, 0))
    drawer = ImageDraw.Draw(shadow)
    drawer.rounded_rectangle(
        (max(0, x - 8), max(0, y + 8), min(719, x + foreground.width + 8), min(1439, y + foreground.height + 18)),
        radius=28,
        fill=(0, 0, 0, 88),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=10))
    composed = Image.alpha_composite(background, shadow)
    composed.alpha_composite(foreground, (x, y))
    composed.convert('RGB').save(preview, 'WEBP', quality=94, method=4)


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

print('PASS: approved static artwork finalized once with Pillow; previews and manifest are byte-identical across phone, Wear OS, and PWA.')
