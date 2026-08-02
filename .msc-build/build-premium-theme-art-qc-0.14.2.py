#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


THEMES = (
    ('moonlit_wolf', 'Moonlit Wolf'),
    ('waterfall_serenity', 'Waterfall Serenity'),
    ('rainforest_harmony', 'Rainforest Harmony'),
    ('ocean_majesty', 'Ocean Majesty'),
    ('celestial_wonder', 'Celestial Wonder'),
    ('mountain_sunrise', 'Mountain Sunrise'),
    ('creation_garden', 'Creation Garden'),
    ('creation_sky', 'Creation Sky'),
    ('lion_premium_2', 'Lion Premium II'),
    ('fox_premium_2', 'Fox Premium II'),
    ('bible_sketch_study', 'Bible Sketch Study'),
    ('parable_line_panels', 'Parable Line Panels'),
    ('noahs_ark', "Noah's Ark"),
    ('red_sea_deliverance', 'Red Sea Deliverance'),
    ('bible_timeline', 'Bible Timeline'),
    ('bible_map', 'Bible Map'),
)
SOURCE_DIMENSIONS = (1024, 1536)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    source_dir = Path(sys.argv[1] if len(sys.argv) > 1 else '.msc-build/premium-theme-art-0.14.2')
    output_dir = Path(sys.argv[2] if len(sys.argv) > 2 else 'theme-art-qc')
    scenes_dir = output_dir / 'scenes'
    scenes_dir.mkdir(parents=True, exist_ok=True)

    manifest: dict[str, object] = {'release': '0.14.2', 'themes': {}}
    checksum_lines: list[str] = []
    tiles: list[Image.Image] = []
    font = ImageFont.load_default(size=18)

    for slug, label in THEMES:
        filename = f'theme_scene_{slug}.webp'
        source = source_dir / filename
        if not source.is_file() or source.stat().st_size <= 100_000:
            raise SystemExit(f'Premium scene missing or undersized: {source}')
        with Image.open(source) as opened:
            opened.verify()
        with Image.open(source) as opened:
            if opened.size != SOURCE_DIMENSIONS:
                raise SystemExit(f'Premium scene dimensions changed: {filename}={opened.size}')
            scene = opened.convert('RGB')

        destination = scenes_dir / filename
        shutil.copyfile(source, destination)
        digest = sha256(destination)
        checksum_lines.append(f'{digest}  {filename}')
        manifest['themes'][slug] = {
            'label': label,
            'sha256': digest,
            'bytes': destination.stat().st_size,
            'dimensions': list(SOURCE_DIMENSIONS),
        }

        artwork = ImageOps.fit(scene, (256, 384), method=Image.Resampling.LANCZOS)
        tile = Image.new('RGB', (276, 426), '#111722')
        tile.paste(artwork, (10, 10))
        draw = ImageDraw.Draw(tile)
        text_box = draw.textbbox((0, 0), label, font=font)
        text_width = text_box[2] - text_box[0]
        draw.text(((tile.width - text_width) // 2, 398), label, fill='#F4F7FB', font=font)
        tiles.append(tile)

    sheet = Image.new('RGB', (276 * 4, 426 * 4), '#090D14')
    for index, tile in enumerate(tiles):
        sheet.paste(tile, ((index % 4) * 276, (index // 4) * 426))
    sheet.save(output_dir / 'msc-0.14.2-theme-art-qc-contact-sheet.jpg', 'JPEG', quality=94, optimize=True)

    (output_dir / 'SHA256SUMS.txt').write_text('\n'.join(checksum_lines) + '\n', encoding='utf-8')
    (output_dir / 'ASSET-MANIFEST.json').write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + '\n',
        encoding='utf-8',
    )
    source_credits = {
        'artwork': 'Original AI-assisted artwork produced specifically for My Study Companion 0.14.2.',
        'source_contract': 'Checksum-locked local full scenes; no expiring URL or third-party fetch.',
        'preview_contract': 'single sharp crop; no blurred crop-fill',
        'protected_contract': 'accepted first nine themes are untouched',
        'surface_contract': 'phone, Fold, Wear, widgets, and PWA use the same rebuilt scene bytes',
    }
    (output_dir / 'SOURCE-CREDITS.json').write_text(
        json.dumps(source_credits, indent=2, sort_keys=True) + '\n',
        encoding='utf-8',
    )
    print('PASS: built deterministic visual QC for all sixteen rebuilt themes.')


if __name__ == '__main__':
    main()
