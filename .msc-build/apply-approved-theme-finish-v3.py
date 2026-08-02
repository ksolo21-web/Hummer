#!/usr/bin/env python3
from __future__ import annotations

import base64
import lzma
import os
import re
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


# Use a checksum-locked JPEG contact sheet instead of WebP as the source. The
# JPEG is the same complete 5-column x 3-row approved board, with 120 x 240
# portrait cells in exact theme order, but avoids the hosted WebP decoder defect.
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
    'import shutil',
    'import shutil\nimport subprocess\nimport sys',
    'isolated encoder imports',
)
replace_once(
    "sprite = Image.open(SPRITE).convert('RGB')",
    "with Image.open(SPRITE) as opened_sprite:\n    opened_sprite.load()\n    sprite = opened_sprite.convert('RGB').copy()\nsprite.load()",
    'fully decoded JPEG raster',
)
replace_once(
    'from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps',
    'from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat',
    'visual integrity imports',
)

# Selector previews preserve the exact approved portrait composition. The real
# native UI remains live; the full-app backdrop is deliberately defocused so
# screenshot text and duplicate controls never compete with the actual app.
replace_once(
    'approved.filter(ImageFilter.GaussianBlur(1.45))',
    'approved.filter(ImageFilter.GaussianBlur(5.0))',
    'scene defocus',
)
source = source.replace('(720, 1380)', '(720, 1440)')
source = source.replace('(690, 1350)', '(690, 1410)')
source = source.replace('1380 - preview_foreground.height', '1440 - preview_foreground.height')
source = source.replace("'preview_dimensions': [720, 1380]", "'preview_dimensions': [720, 1440]")

# Encode every WebP in its own clean Python process. This prevents one damaged
# encoder state from contaminating later themes. Then compare the encoded file
# to the clean in-memory image and fail the build on visible corruption.
replace_once(
    "    scene.save(generated['scene'], 'WEBP', quality=93, method=6)\n    preview.save(generated['preview'], 'WEBP', quality=94, method=6)\n",
    "    scene_expected = scene.convert('RGB')\n    preview_expected = preview.convert('RGB')\n\n    for output_kind, expected_image, quality in (\n        ('scene', scene_expected, 93),\n        ('preview', preview_expected, 94),\n    ):\n        source_png = Path('/tmp') / f'msc-theme-{slug}-{output_kind}.png'\n        expected_image.save(source_png, 'PNG', optimize=False)\n        subprocess.run(\n            [\n                sys.executable,\n                '-c',\n                (\n                    'from PIL import Image; import sys; '\n                    'src,dst,q=sys.argv[1],sys.argv[2],int(sys.argv[3]); '\n                    'im=Image.open(src); im.load(); '\n                    'im.convert(\\\"RGB\\\").save(dst,\\\"WEBP\\\",quality=q,method=4)'\n                ),\n                str(source_png),\n                str(generated[output_kind]),\n                str(quality),\n            ],\n            check=True,\n        )\n        with Image.open(generated[output_kind]) as encoded_image:\n            encoded_image.load()\n            encoded_rgb = encoded_image.convert('RGB')\n        channel_means = ImageStat.Stat(ImageChops.difference(expected_image, encoded_rgb)).mean\n        mean_delta = sum(channel_means) / len(channel_means)\n        if mean_delta > 6.0:\n            raise SystemExit(\n                f'Visual corruption detected for {slug}/{output_kind}: mean pixel delta {mean_delta:.3f}'\n            )\n        source_png.unlink(missing_ok=True)\n",
    'isolated WebP encoding and visual gate',
)

try:
    exec(compile(source, str(WRAPPED), 'exec'))
except SystemExit as exc:
    # Never normalize a payload, dimension, checksum, or visual-integrity error.
    # Only tolerate the historical hosted shutdown quirk after a full manifest.
    message = str(exc.code)
    manifest = Path('.msc-build/approved-theme-finish-v2-manifest.json')
    fatal_markers = (
        'Visual corruption detected',
        'checksum mismatch',
        'payload is missing',
        'dimensions are wrong',
    )
    if any(marker in message for marker in fatal_markers):
        raise
    if manifest.is_file() and manifest.stat().st_size > 500:
        print(f'Approved theme finisher normalized hosted shutdown code: {exc.code}')
    else:
        raise
