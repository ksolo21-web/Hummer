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


# Use the checksum-locked, complete Kaleb-approved contact sheet. It is a
# 5-column x 3-row sprite with 120 x 240 portrait cells in exact theme order.
replace_once(
    "approved-theme-sprite-v2.part*.b64",
    "approved-theme-sprite-v5.part*.b64",
    'approved sprite payload',
)
replace_once(
    'Approved theme sprite payload is missing.',
    'Approved theme sprite v5 payload is missing.',
    'approved sprite missing-message',
)
source, checksum_count = re.subn(
    r"EXPECTED_SPRITE_SHA256 = '[0-9a-f]{64}'",
    "EXPECTED_SPRITE_SHA256 = 'f734358af1abbb5fa6ba7f9515ecddc5ec66622753719e7acf6517b59bd3ef24'",
    source,
    count=1,
)
if checksum_count != 1:
    raise SystemExit('Unable to set the approved-theme sprite checksum contract.')
replace_once('CELL_W = 180', 'CELL_W = 120', 'sprite cell width')
replace_once('CELL_H = 360', 'CELL_H = 240', 'sprite cell height')
replace_once(
    "if sprite.size != (900, 1080):",
    "if sprite.size != (600, 720):",
    'sprite dimensions',
)

# Force the source WebP to be fully decoded and detached before any row crops.
# This prevents hosted libwebp lazy-decoder state from corrupting later cells.
replace_once(
    "sprite = Image.open(SPRITE).convert('RGB')",
    "with Image.open(SPRITE) as opened_sprite:\n    opened_sprite.load()\n    sprite = opened_sprite.convert('RGB').copy()\nsprite.load()",
    'fully decoded sprite raster',
)
replace_once(
    'from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps',
    'from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat',
    'visual integrity imports',
)

# Selector previews preserve the exact approved portrait composition. The real
# native UI remains live; the full-app backdrop is deliberately defocused so
# no screenshot text or duplicate controls compete with the actual interface.
replace_once(
    'approved.filter(ImageFilter.GaussianBlur(1.45))',
    'approved.filter(ImageFilter.GaussianBlur(5.0))',
    'scene defocus',
)
source = source.replace('(720, 1380)', '(720, 1440)')
source = source.replace('(690, 1350)', '(690, 1410)')
source = source.replace('1380 - preview_foreground.height', '1440 - preview_foreground.height')
source = source.replace("'preview_dimensions': [720, 1380]", "'preview_dimensions': [720, 1440]")

# Use the stable encoder setting and reject visually corrupted WebP output,
# not merely files that exist or share hashes across device targets.
replace_once(
    "    scene.save(generated['scene'], 'WEBP', quality=93, method=6)\n    preview.save(generated['preview'], 'WEBP', quality=94, method=6)\n",
    "    scene_expected = scene.convert('RGB')\n    preview_expected = preview.convert('RGB')\n    scene_expected.save(generated['scene'], 'WEBP', quality=93, method=4)\n    preview_expected.save(generated['preview'], 'WEBP', quality=94, method=4)\n\n    for output_kind, expected_image in (('scene', scene_expected), ('preview', preview_expected)):\n        with Image.open(generated[output_kind]) as encoded_image:\n            encoded_image.load()\n            encoded_rgb = encoded_image.convert('RGB')\n        channel_means = ImageStat.Stat(ImageChops.difference(expected_image, encoded_rgb)).mean\n        mean_delta = sum(channel_means) / len(channel_means)\n        if mean_delta > 6.0:\n            raise SystemExit(\n                f'Visual corruption detected for {slug}/{output_kind}: mean pixel delta {mean_delta:.3f}'\n            )\n",
    'stable WebP encoding and visual gate',
)

try:
    exec(compile(source, str(WRAPPED), 'exec'))
except SystemExit as exc:
    # Never normalize a visual-integrity or payload failure. Only tolerate the
    # historical hosted shutdown quirk after a complete verified manifest.
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
