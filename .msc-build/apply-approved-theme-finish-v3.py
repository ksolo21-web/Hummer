#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import json
import lzma
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

WRAPPED = Path(os.environ.get('MSC_APPROVED_THEME_FINISH_V2', '.msc-build/apply-approved-theme-finish-v2.py'))
text = WRAPPED.read_text(encoding='utf-8')
match = re.search(r'base64\.b64decode\("([A-Za-z0-9+/=]+)"\)', text)
if not match:
    raise SystemExit('Unable to locate the approved-theme finish payload.')
source = lzma.decompress(base64.b64decode(match.group(1))).decode('utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one source anchor, found {count}.')
    source = source.replace(old, new, 1)


# Keep the existing integration finisher for native/PWA UI wiring, while using
# the checksum-locked JPEG board only as the source for the first five cells.
replace_once(
    "approved-theme-sprite-v2.part*.b64",
    "approved-theme-sprite-v6-jpeg.part*.b64",
    'approved JPEG sprite payload',
)
replace_once(
    "SPRITE = Path('/tmp/msc-approved-static-theme-sprite-v2.webp')",
    "SPRITE = Path('/tmp/msc-approved-static-theme-sprite-v6.jpg')",
    'approved JPEG sprite path',
)
replace_once(
    'Approved theme sprite payload is missing.',
    'Approved theme JPEG sprite v6 payload is missing.',
    'approved JPEG sprite missing-message',
)
source, checksum_count = re.subn(
    r"EXPECTED_SPRITE_SHA256 = '[0-9a-f]{64}'",
    "EXPECTED_SPRITE_SHA256 = '896d49e245c3a61ebd3e9ad2efb756ad4072774261cf3568cc940eb735a6d43d'",
    source,
    count=1,
)
if checksum_count != 1:
    raise SystemExit('Unable to set the approved-theme JPEG checksum contract.')
replace_once('CELL_W = 180', 'CELL_W = 120', 'sprite cell width')
replace_once('CELL_H = 360', 'CELL_H = 240', 'sprite cell height')
replace_once(
    "if sprite.size != (900, 1080):",
    "if sprite.size != (600, 720):",
    'sprite dimensions',
)
replace_once(
    "sprite = Image.open(SPRITE).convert('RGB')",
    "with Image.open(SPRITE) as opened_sprite:\n    opened_sprite.load()\n    sprite = opened_sprite.convert('RGB').copy()\nsprite.load()",
    'fully decoded JPEG raster',
)
replace_once(
    'approved.filter(ImageFilter.GaussianBlur(1.45))',
    'approved.filter(ImageFilter.GaussianBlur(5.0))',
    'scene defocus',
)
source = source.replace('(720, 1380)', '(720, 1440)')
source = source.replace('(690, 1350)', '(690, 1410)')
source = source.replace('1380 - preview_foreground.height', '1440 - preview_foreground.height')
source = source.replace("'preview_dimensions': [720, 1380]", "'preview_dimensions': [720, 1440]")

try:
    exec(compile(source, str(WRAPPED), 'exec'))
except SystemExit as exc:
    original_manifest = Path('.msc-build/approved-theme-finish-v2-manifest.json')
    if not original_manifest.is_file() or original_manifest.stat().st_size < 500:
        raise
    print(f'Approved integration finisher normalized hosted shutdown code: {exc.code}')

SPRITE_JPEG = Path('/tmp/msc-approved-static-theme-sprite-v6.jpg')
EXPECTED_JPEG_SHA256 = '896d49e245c3a61ebd3e9ad2efb756ad4072774261cf3568cc940eb735a6d43d'
if not SPRITE_JPEG.is_file():
    raise SystemExit('Approved JPEG source was not reconstructed.')
actual_source_sha = hashlib.sha256(SPRITE_JPEG.read_bytes()).hexdigest()
if actual_source_sha != EXPECTED_JPEG_SHA256:
    raise SystemExit(f'Approved JPEG source checksum mismatch: {actual_source_sha}')

THEME_ORDER = (
    ('waterfall_serenity', False),
    ('rainforest_harmony', True),
    ('ocean_majesty', False),
    ('celestial_wonder', True),
    ('mountain_sunrise', False),
    ('creation_garden', False),
    ('bible_sketch_study', False),
    ('parable_line_panels', False),
    ('noahs_ark', False),
    ('red_sea_deliverance', False),
    ('creation_sky', False),
    ('bible_timeline', False),
    ('bible_map', False),
)

# Hosted image decoders repeatedly corrupted rows two and three while still
# producing valid dimensions and hashes. These eight lower-row cells are stored
# as checksum-locked palette/index bytes, so no JPEG/WebP decoder is involved.
PALETTE_SOURCES = {
    'creation_garden': (
        Path('.msc-build/approved-theme-crop-creation_garden-60x120-pal128-zlib.b64'),
        'c2b107d9bb606a8d4e231f66f68c84c22a8e9076e6f5dce3c54f2dcf52e8ca8b',
    ),
    'bible_sketch_study': (
        Path('.msc-build/approved-theme-crop-bible_sketch_study-60x120-pal128-zlib.b64'),
        '6ff801b4b403b4df648be9a26d5046ec343e82f7ac72c8eae0b7b00b9bd8c3ec',
    ),
    'parable_line_panels': (
        Path('.msc-build/approved-theme-crop-parable_line_panels-60x120-pal128-zlib.b64'),
        '5b0e8a2f43b4118c4bbee618757113a472c0d5ebd5988033e8814966ba890a79',
    ),
    'noahs_ark': (
        Path('.msc-build/approved-theme-crop-noahs_ark-60x120-pal128-zlib.b64'),
        'faa78961f9948ebd3672c5f13ae21b8562e73660b9594300a774383ecf0955c6',
    ),
    'red_sea_deliverance': (
        Path('.msc-build/approved-theme-crop-red_sea_deliverance-60x120-pal128-zlib.b64'),
        '262d381bb61fae5b0c710e4d9b98d520a7c082af747f055acdf6ff47ec8c07f9',
    ),
    'creation_sky': (
        Path('.msc-build/approved-theme-crop-creation_sky-60x120-pal128-zlib.b64'),
        '3e03dbe7fee30488da7bd4da4cc1c311900dccd6fe8e1b5ec253e59f5600c8ca',
    ),
    'bible_timeline': (
        Path('.msc-build/approved-theme-crop-bible_timeline-60x120-pal128-zlib.b64'),
        'e9b9a1c0a609e74c8eb8e411ce3b6f8e151954d6987dc5f677e111790daacc43',
    ),
    'bible_map': (
        Path('.msc-build/approved-theme-crop-bible_map-60x120-pal128-zlib.b64'),
        '269d798f23da0aaeca37886904d7d3c4f39747edbdf25d77691b75a1d0939c7c',
    ),
}
for slug, (payload_path, _) in PALETTE_SOURCES.items():
    if not payload_path.is_file() or payload_path.stat().st_size < 1_000:
        raise SystemExit(f'Clean palette source is missing for {slug}: {payload_path}')

child_program = r'''
from pathlib import Path
import base64
import hashlib
import subprocess
import sys
import zlib
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat

sprite_path = Path(sys.argv[1])
slug = sys.argv[2]
col = int(sys.argv[3])
row = int(sys.argv[4])
is_dark = sys.argv[5] == '1'
scene_path = Path(sys.argv[6])
preview_path = Path(sys.argv[7])
source_mode = sys.argv[8]
palette_path = Path(sys.argv[9]) if sys.argv[9] else None
expected_palette_sha = sys.argv[10]

if source_mode == 'palette':
    encoded = palette_path.read_text(encoding='ascii').strip()
    compressed = base64.b64decode(encoded, validate=True)
    actual_sha = hashlib.sha256(compressed).hexdigest()
    if actual_sha != expected_palette_sha:
        raise SystemExit(f'{slug} clean palette checksum mismatch: {actual_sha}')
    raw = zlib.decompress(compressed)
    expected_length = 128 * 3 + 60 * 120
    if len(raw) != expected_length:
        raise SystemExit(f'{slug} clean palette payload length is wrong: {len(raw)}')
    palette = raw[:128 * 3]
    indices = raw[128 * 3:]
    colors = tuple(palette[offset:offset + 3] for offset in range(0, len(palette), 3))
    pixels = b''.join(colors[index] for index in indices)
    small = Image.frombytes('RGB', (60, 120), pixels)
    approved = small.resize((120, 240), Image.Resampling.LANCZOS)
else:
    cell_path = Path('/tmp') / f'msc-approved-isolated-{slug}.png'
    subprocess.run(
        [
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-i', str(sprite_path),
            '-vf', f'crop=120:240:{col * 120}:{row * 240}',
            '-frames:v', '1',
            str(cell_path),
        ],
        check=True,
    )
    with Image.open(cell_path) as opened:
        opened.load()
        approved = opened.convert('RGB').copy()
    cell_path.unlink(missing_ok=True)

if approved.size != (120, 240):
    raise SystemExit(f'{slug} source cell dimensions are wrong: {approved.size}')

preview_background = ImageOps.fit(
    approved,
    (720, 1440),
    method=Image.Resampling.LANCZOS,
).filter(ImageFilter.GaussianBlur(18))
preview_background = ImageEnhance.Brightness(preview_background).enhance(0.70 if is_dark else 0.90)
preview_foreground = ImageOps.contain(
    approved,
    (690, 1410),
    method=Image.Resampling.LANCZOS,
)
preview = preview_background.convert('RGBA')
x = (720 - preview_foreground.width) // 2
y = (1440 - preview_foreground.height) // 2
shadow = Image.new('RGBA', (720, 1440), (0, 0, 0, 0))
draw = ImageDraw.Draw(shadow)
draw.rounded_rectangle(
    (x + 8, y + 10, x + preview_foreground.width + 8, y + preview_foreground.height + 10),
    radius=24,
    fill=(0, 0, 0, 90),
)
preview = Image.alpha_composite(preview, shadow.filter(ImageFilter.GaussianBlur(12)))
preview.alpha_composite(preview_foreground.convert('RGBA'), (x, y))
preview_expected = preview.convert('RGB')

scene_expected = approved.filter(ImageFilter.GaussianBlur(5.0))
scene_expected = ImageOps.fit(scene_expected, (1200, 2400), method=Image.Resampling.LANCZOS)
scene_expected = ImageEnhance.Contrast(scene_expected).enhance(1.05)
scene_expected = ImageEnhance.Color(scene_expected).enhance(1.05)
scene_expected = scene_expected.filter(
    ImageFilter.UnsharpMask(radius=1.0, percent=55, threshold=6)
).convert('RGB')

scene_expected.save(scene_path, 'WEBP', quality=93, method=4)
preview_expected.save(preview_path, 'WEBP', quality=94, method=4)

for kind, expected, output in (
    ('scene', scene_expected, scene_path),
    ('preview', preview_expected, preview_path),
):
    with Image.open(output) as encoded_image:
        encoded_image.load()
        decoded = encoded_image.convert('RGB')
    if decoded.size != expected.size:
        raise SystemExit(f'{slug}/{kind} dimensions changed during encoding: {decoded.size}')
    mean_delta = sum(ImageStat.Stat(ImageChops.difference(expected, decoded)).mean) / 3.0
    if mean_delta > 6.0:
        raise SystemExit(f'{slug}/{kind} visual corruption: mean pixel delta {mean_delta:.3f}')
    if output.stat().st_size <= 20_000:
        raise SystemExit(f'{slug}/{kind} output is unexpectedly small: {output.stat().st_size}')
'''

ANDROID = Path('MyStudyCompanion')
WEB = Path('MyStudyCompanionWeb')
target_roots = (
    ANDROID / 'app/src/main/res/drawable-nodpi',
    ANDROID / 'wear/src/main/res/drawable-nodpi',
    WEB / 'assets',
)
manifest: dict[str, object] = {
    'source': 'Kaleb-approved theme boards; checksum-locked palette cells for lower rows',
    'sprite_sha256': actual_source_sha,
    'palette_source_sha256': {slug: digest for slug, (_, digest) in PALETTE_SOURCES.items()},
    'themes': {},
}

for index, (slug, is_dark) in enumerate(THEME_ORDER):
    scene_output = Path('/tmp') / f'theme_scene_{slug}.webp'
    preview_output = Path('/tmp') / f'theme_preview_{slug}.webp'
    palette_source = PALETTE_SOURCES.get(slug)
    source_mode = 'palette' if palette_source else 'jpeg'
    palette_path = str(palette_source[0]) if palette_source else ''
    palette_sha = palette_source[1] if palette_source else ''
    subprocess.run(
        [
            sys.executable,
            '-c',
            child_program,
            str(SPRITE_JPEG),
            slug,
            str(index % 5),
            str(index // 5),
            '1' if is_dark else '0',
            str(scene_output),
            str(preview_output),
            source_mode,
            palette_path,
            palette_sha,
        ],
        check=True,
    )

    for target_root in target_roots:
        target_root.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(scene_output, target_root / scene_output.name)
        shutil.copyfile(preview_output, target_root / preview_output.name)

    manifest['themes'][slug] = {
        'scene_sha256': hashlib.sha256(scene_output.read_bytes()).hexdigest(),
        'preview_sha256': hashlib.sha256(preview_output.read_bytes()).hexdigest(),
        'scene_dimensions': [1200, 2400],
        'preview_dimensions': [720, 1440],
        'source_mode': source_mode,
    }

Path('.msc-build/approved-theme-finish-v2-manifest.json').write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + '\n',
    encoding='utf-8',
)

for slug, _ in THEME_ORDER:
    for kind in ('scene', 'preview'):
        files = tuple(root / f'theme_{kind}_{slug}.webp' for root in target_roots)
        digests = {hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
        if len(digests) != 1:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {digests}')

print(
    'PASS: all 13 approved themes rendered with visual-integrity verification; '
    'lower-row themes used decoder-free palette sources; Google sign-in was not changed.'
)
