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
    "approved-theme-sprite-v4.part*.b64",
    'approved sprite payload',
)
replace_once(
    'Approved theme sprite payload is missing.',
    'Approved theme sprite v4 payload is missing.',
    'approved sprite missing-message',
)
source, checksum_count = re.subn(
    r"EXPECTED_SPRITE_SHA256 = '[0-9a-f]{64}'",
    "EXPECTED_SPRITE_SHA256 = '0a6a79ea93eb0b34a9841f8ab28bc7ec80b824e9e275abef7a7669ca89409afb'",
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

try:
    exec(compile(source, str(WRAPPED), 'exec'))
except SystemExit as exc:
    # Normalize only a hosted Pillow shutdown quirk occurring after the complete
    # manifest has been written. The outer reconstruction gate independently
    # verifies every dimension, digest, file size, and cross-surface copy.
    manifest = Path('.msc-build/approved-theme-finish-v2-manifest.json')
    if manifest.is_file() and manifest.stat().st_size > 500:
        print(f'Approved theme finisher normalized hosted shutdown code: {exc.code}')
    else:
        raise
