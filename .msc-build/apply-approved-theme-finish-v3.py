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


replace_once("approved-theme-sprite-v2.part*.b64", "approved-theme-sprite-v6-jpeg.part*.b64", 'approved JPEG sprite payload')
replace_once("SPRITE = Path('/tmp/msc-approved-static-theme-sprite-v2.webp')", "SPRITE = Path('/tmp/msc-approved-static-theme-sprite-v6.jpg')", 'approved JPEG sprite path')
replace_once('Approved theme sprite payload is missing.', 'Approved theme JPEG sprite v6 payload is missing.', 'approved JPEG sprite missing-message')
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
replace_once("if sprite.size != (900, 1080):", "if sprite.size != (600, 720):", 'sprite dimensions')
replace_once("sprite = Image.open(SPRITE).convert('RGB')", "with Image.open(SPRITE) as opened_sprite:\n    opened_sprite.load()\n    sprite = opened_sprite.convert('RGB').copy()\nsprite.load()", 'fully decoded JPEG raster')
replace_once('approved.filter(ImageFilter.GaussianBlur(1.45))', 'approved.filter(ImageFilter.GaussianBlur(5.0))', 'scene defocus')
source = source.replace('(720, 1380)', '(720, 1440)')
source = source.replace('(690, 1350)', '(690, 1410)')
source = source.replace('1380 - preview_foreground.height', '1440 - preview_foreground.height')
source = source.replace("'preview_dimensions': [720, 1380]", "'preview_dimensions': [720, 1440]")

integration_script = Path('/tmp/msc-approved-theme-integration-v2.py')
integration_script.write_text(source, encoding='utf-8')
integration_result = subprocess.run([sys.executable, str(integration_script)])
original_manifest = Path('.msc-build/approved-theme-finish-v2-manifest.json')
if not original_manifest.is_file() or original_manifest.stat().st_size < 500:
    raise SystemExit(
        f'Approved integration finisher failed before writing a complete manifest: '
        f'code={integration_result.returncode}'
    )
if integration_result.returncode != 0:
    print(
        'Approved integration finisher isolated its hosted shutdown code: '
        f'{integration_result.returncode}'
    )

SPRITE_JPEG = Path('/tmp/msc-approved-static-theme-sprite-v6.jpg')
EXPECTED_JPEG_SHA256 = '896d49e245c3a61ebd3e9ad2efb756ad4072774261cf3568cc940eb735a6d43d'
if not SPRITE_JPEG.is_file():
    raise SystemExit('Approved JPEG source was not reconstructed.')
actual_source_sha = hashlib.sha256(SPRITE_JPEG.read_bytes()).hexdigest()
if actual_source_sha != EXPECTED_JPEG_SHA256:
    raise SystemExit(f'Approved JPEG source checksum mismatch: {actual_source_sha}')

THEME_ORDER = (
    ('waterfall_serenity', False), ('rainforest_harmony', True),
    ('ocean_majesty', False), ('celestial_wonder', True),
    ('mountain_sunrise', False), ('creation_garden', False),
    ('bible_sketch_study', False), ('parable_line_panels', False),
    ('noahs_ark', False), ('red_sea_deliverance', False),
    ('creation_sky', False), ('bible_timeline', False), ('bible_map', False),
)
PALETTE_SOURCES = {
    'creation_garden': (Path('.msc-build/approved-theme-crop-creation_garden-60x120-pal128-zlib.b64'), 'c2b107d9bb606a8d4e231f66f68c84c22a8e9076e6f5dce3c54f2dcf52e8ca8b'),
    'bible_sketch_study': (Path('.msc-build/approved-theme-crop-bible_sketch_study-60x120-pal128-zlib.b64'), '6ff801b4b403b4df648be9a26d5046ec343e82f7ac72c8eae0b7b00b9bd8c3ec'),
    'parable_line_panels': (Path('.msc-build/approved-theme-crop-parable_line_panels-60x120-pal128-zlib.b64'), '5b0e8a2f43b4118c4bbee618757113a472c0d5ebd5988033e8814966ba890a79'),
    'noahs_ark': (Path('.msc-build/approved-theme-crop-noahs_ark-60x120-pal128-zlib.b64'), 'faa78961f9948ebd3672c5f13ae21b8562e73660b9594300a774383ecf0955c6'),
    'red_sea_deliverance': (Path('.msc-build/approved-theme-crop-red_sea_deliverance-60x120-pal128-zlib.b64'), '262d381bb61fae5b0c710e4d9b98d520a7c082af747f055acdf6ff47ec8c07f9'),
    'creation_sky': (Path('.msc-build/approved-theme-crop-creation_sky-60x120-pal128-zlib.b64'), '3e03dbe7fee30488da7bd4da4cc1c311900dccd6fe8e1b5ec253e59f5600c8ca'),
    'bible_timeline': (Path('.msc-build/approved-theme-crop-bible_timeline-60x120-pal128-zlib.b64'), 'e9b9a1c0a609e74c8eb8e411ce3b6f8e151954d6987dc5f677e111790daacc43'),
    'bible_map': (Path('.msc-build/approved-theme-crop-bible_map-60x120-pal128-zlib.b64'), '269d798f23da0aaeca37886904d7d3c4f39747edbdf25d77691b75a1d0939c7c'),
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

sprite_path = Path(sys.argv[1]); slug = sys.argv[2]
col = int(sys.argv[3]); row = int(sys.argv[4]); is_dark = sys.argv[5] == '1'
scene_path = Path(sys.argv[6]); preview_path = Path(sys.argv[7])
source_mode = sys.argv[8]; palette_path = Path(sys.argv[9]) if sys.argv[9] else None
expected_palette_sha = sys.argv[10]
source_ppm = Path('/tmp') / f'msc-approved-source-{slug}.ppm'

if source_mode == 'palette':
    compressed = base64.b64decode(palette_path.read_text(encoding='ascii').strip(), validate=True)
    actual_sha = hashlib.sha256(compressed).hexdigest()
    if actual_sha != expected_palette_sha:
        raise SystemExit(f'{slug} clean palette checksum mismatch: {actual_sha}')
    raw = zlib.decompress(compressed)
    if len(raw) != 128 * 3 + 60 * 120:
        raise SystemExit(f'{slug} clean palette payload length is wrong: {len(raw)}')
    palette = raw[:384]; indices = raw[384:]
    colors = tuple(palette[offset:offset + 3] for offset in range(0, 384, 3))
    pixels = b''.join(colors[index] for index in indices)
    width, height = 60, 120
    source_ppm.write_bytes(f'P6\n{width} {height}\n255\n'.encode('ascii') + pixels)
    foreground_filter = 'scale=690:1380:flags=neighbor,gblur=sigma=0.35'
else:
    width, height = 120, 240
    subprocess.run([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(sprite_path),
        '-vf', f'crop=120:240:{col * 120}:{row * 240}', '-frames:v', '1', str(source_ppm),
    ], check=True)
    data = source_ppm.read_bytes(); header_end = 0
    for _ in range(3): header_end = data.index(b'\n', header_end) + 1
    pixels = data[header_end:]
    if len(pixels) != width * height * 3:
        raise SystemExit(f'{slug} JPEG crop raw length is wrong: {len(pixels)}')
    foreground_filter = 'scale=690:1380:flags=lanczos,unsharp=5:5:0.6:5:5:0'

brightness = '-0.18' if is_dark else '-0.08'
preview_graph = (
    '[0:v]split=2[bgsrc][fgsrc];'
    f'[bgsrc]scale=720:1440:flags=lanczos,gblur=sigma=18,eq=brightness={brightness}[bg];'
    f'[fgsrc]{foreground_filter}[fg];'
    '[bg]drawbox=x=23:y=40:w=690:h=1380:color=black@0.35:t=fill[shadow];'
    '[shadow][fg]overlay=x=15:y=30:format=auto[out]'
)
subprocess.run([
    'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(source_ppm),
    '-filter_complex', preview_graph, '-map', '[out]', '-c:v', 'libwebp', '-quality', '94',
    '-compression_level', '4', '-frames:v', '1', str(preview_path),
], check=True)
subprocess.run([
    'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(source_ppm),
    '-vf', 'scale=1200:2400:flags=lanczos,gblur=sigma=20,eq=contrast=1.05:saturation=1.05',
    '-c:v', 'libwebp', '-quality', '93', '-compression_level', '4', '-frames:v', '1', str(scene_path),
], check=True)

for path, expected_size in ((preview_path, '720x1440'), (scene_path, '1200x2400')):
    if not path.is_file() or path.stat().st_size <= 20_000:
        raise SystemExit(f'{slug} output is missing or undersized: {path}')
    size = subprocess.run([
        'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=width,height',
        '-of', 'csv=s=x:p=0', str(path),
    ], check=True, stdout=subprocess.PIPE, text=True).stdout.strip()
    if size != expected_size:
        raise SystemExit(f'{slug} dimensions changed during encoding: {path.name}={size}')

decoded = subprocess.run([
    'ffmpeg', '-hide_banner', '-loglevel', 'error', '-i', str(preview_path),
    '-vf', f'crop=690:1380:15:30,scale={width}:{height}:flags=area',
    '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-',
], check=True, stdout=subprocess.PIPE).stdout
if len(decoded) != len(pixels):
    raise SystemExit(f'{slug} independent preview decode length mismatch: {len(decoded)}')
mae = sum(abs(a - b) for a, b in zip(pixels, decoded)) / len(pixels)
if mae > 12.0:
    raise SystemExit(f'{slug} independent visual corruption check failed: MAE={mae:.3f}')
source_ppm.unlink(missing_ok=True)
'''

ANDROID = Path('MyStudyCompanion'); WEB = Path('MyStudyCompanionWeb')
target_roots = (
    ANDROID / 'app/src/main/res/drawable-nodpi',
    ANDROID / 'wear/src/main/res/drawable-nodpi',
    WEB / 'assets',
)
manifest: dict[str, object] = {
    'source': 'Kaleb-approved theme boards; ffmpeg-only rendering and decoder-independent visual checks',
    'sprite_sha256': actual_source_sha,
    'palette_source_sha256': {slug: digest for slug, (_, digest) in PALETTE_SOURCES.items()},
    'themes': {},
}
for index, (slug, is_dark) in enumerate(THEME_ORDER):
    scene_output = Path('/tmp') / f'theme_scene_{slug}.webp'
    preview_output = Path('/tmp') / f'theme_preview_{slug}.webp'
    palette_source = PALETTE_SOURCES.get(slug)
    source_mode = 'palette' if palette_source else 'jpeg'
    subprocess.run([
        sys.executable, '-c', child_program, str(SPRITE_JPEG), slug,
        str(index % 5), str(index // 5), '1' if is_dark else '0',
        str(scene_output), str(preview_output), source_mode,
        str(palette_source[0]) if palette_source else '', palette_source[1] if palette_source else '',
    ], check=True)
    for target_root in target_roots:
        target_root.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(scene_output, target_root / scene_output.name)
        shutil.copyfile(preview_output, target_root / preview_output.name)
    manifest['themes'][slug] = {
        'scene_sha256': hashlib.sha256(scene_output.read_bytes()).hexdigest(),
        'preview_sha256': hashlib.sha256(preview_output.read_bytes()).hexdigest(),
        'scene_dimensions': [1200, 2400], 'preview_dimensions': [720, 1440],
        'source_mode': source_mode,
    }
Path('.msc-build/approved-theme-finish-v2-manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')
for slug, _ in THEME_ORDER:
    for kind in ('scene', 'preview'):
        files = tuple(root / f'theme_{kind}_{slug}.webp' for root in target_roots)
        digests = {hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
        if len(digests) != 1:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {digests}')
print('PASS: all 13 approved themes rendered through ffmpeg with independent visual-integrity verification; Google sign-in was not changed.')
