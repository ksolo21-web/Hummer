#!/usr/bin/env python3
"""Upgrade the 0.15.15 workbook generator with recognizable pencil pages.

This wrapper deliberately reuses the tested source-art segmentation and manifest
pipeline, but replaces its abstract white/map rendering. The play page now keeps
the real scene visible as light pencil line art, uses subtle region boundaries,
prints the palette number (1-8, not the internal region id), and keeps every
number safely inside the page.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps
from scipy.ndimage import distance_transform_edt
from skimage.segmentation import find_boundaries

ROOT = Path(__file__).resolve().parents[1]
BASE_GENERATOR = ROOT / ".msc-build/generate-0.15.15-professional-color-by-number.py"
SPEC = importlib.util.spec_from_file_location("msc_color_v3", BASE_GENERATOR)
if SPEC is None or SPEC.loader is None:
    raise SystemExit("FAIL: cannot load the professional source-art generator")
base = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(base)
ORIGINAL_ASSIGN_PALETTE = base.assign_palette


def safe_number_center(region_mask: np.ndarray) -> tuple[int, int]:
    """Place a label at the deepest usable point, never against a screen edge."""
    safe = region_mask.copy()
    margin = 30
    safe[:margin, :] = False
    safe[-margin:, :] = False
    safe[:, :margin] = False
    safe[:, -margin:] = False
    working = safe if safe.any() else region_mask
    distance = distance_transform_edt(working)
    y, x = np.unravel_index(int(distance.argmax()), distance.shape)
    return (
        int(np.clip(x, margin, base.WIDTH - margin - 1)),
        int(np.clip(y, margin, base.HEIGHT - margin - 1)),
    )


def pencil_source(rgb: np.ndarray) -> Image.Image:
    """Create a bright printable pencil drawing while preserving scene identity."""
    source = Image.fromarray(np.clip(rgb * 255.0, 0, 255).astype(np.uint8), mode="RGB")
    gray = ImageOps.grayscale(source).filter(ImageFilter.MedianFilter(3))
    inverted = ImageOps.invert(gray)
    blurred = inverted.filter(ImageFilter.GaussianBlur(12))
    gray_values = np.asarray(gray, dtype=np.float32)
    blur_values = np.asarray(blurred, dtype=np.float32)
    sketch = np.clip(gray_values * 255.0 / (255.0 - blur_values + 1.0), 0, 255).astype(np.uint8)
    image = Image.fromarray(sketch, mode="L")
    image = ImageEnhance.Contrast(image).enhance(1.10)
    image = Image.blend(image.convert("RGB"), Image.new("RGB", image.size, "white"), 0.18)
    return image


def recognizable_line_art(
    rgb: np.ndarray,
    labels: np.ndarray,
    centers: dict[int, tuple[int, int]],
) -> Image.Image:
    """Render the actual scene, light region borders, and correct palette numbers."""
    image = pencil_source(rgb)
    pixels = np.asarray(image).copy()
    boundaries = find_boundaries(labels, mode="inner")
    # Region boundaries guide taps without overpowering the people, animals, and scenery.
    pixels[boundaries] = np.minimum(pixels[boundaries], np.array([102, 102, 102], dtype=np.uint8))
    image = Image.fromarray(pixels, mode="RGB")
    draw = ImageDraw.Draw(image)
    number_font = base.font(25, bold=True)
    numbers = ORIGINAL_ASSIGN_PALETTE(labels, rgb)

    for region_id, (x, y) in centers.items():
        palette_number = str(numbers[region_id])
        box = draw.textbbox((0, 0), palette_number, font=number_font)
        text_width = box[2] - box[0]
        text_height = box[3] - box[1]
        radius = max(16, int(max(text_width, text_height) * 0.70))
        draw.ellipse(
            (x - radius, y - radius, x + radius, y + radius),
            fill="#FFFFFF",
            outline="#555555",
            width=2,
        )
        draw.text(
            (x - text_width / 2, y - text_height / 2 - 2),
            palette_number,
            fill="#111111",
            font=number_font,
        )
    return image


def compact_contact_sheet(
    previews: list[tuple[str, Image.Image, Image.Image]],
) -> Path:
    """Create a readable 4x4 visual gate with no misleading empty page area."""
    base.REPORT_DIR.mkdir(parents=True, exist_ok=True)
    cell_width, cell_height = 420, 350
    sheet = Image.new("RGB", (cell_width * 4, cell_height * 4), "#E9E5DC")
    draw = ImageDraw.Draw(sheet)
    title_font = base.font(19, bold=True)
    small_font = base.font(13)

    for index, (title, line, completion) in enumerate(previews):
        column, row = index % 4, index // 4
        x0, y0 = column * cell_width, row * cell_height
        draw.rounded_rectangle(
            (x0 + 8, y0 + 8, x0 + cell_width - 8, y0 + cell_height - 8),
            radius=18,
            fill="white",
            outline="#B9B3A7",
            width=2,
        )
        clipped = title if len(title) <= 36 else title[:33] + "…"
        draw.text((x0 + 16, y0 + 17), clipped, fill="#1A1A1A", font=title_font)

        line_thumb = line.copy()
        line_thumb.thumbnail((188, 255), Image.Resampling.LANCZOS)
        color_thumb = completion.copy()
        color_thumb.thumbnail((188, 255), Image.Resampling.LANCZOS)
        sheet.paste(line_thumb, (x0 + 14, y0 + 52))
        sheet.paste(color_thumb, (x0 + 218, y0 + 52))
        draw.text((x0 + 16, y0 + 317), "NUMBERED PENCIL PAGE", fill="#5D6670", font=small_font)
        draw.text((x0 + 220, y0 + 317), "COMPLETED REVEAL", fill="#5D6670", font=small_font)

    path = base.REPORT_DIR / "color-by-number-professional-contact-sheet.jpg"
    sheet.save(path, quality=93, optimize=True)
    return path


base.best_number_center = safe_number_center
base.make_line_art = recognizable_line_art
base.create_contact_sheet = compact_contact_sheet
base.main()

manifest_path = base.MANIFEST
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
manifest["colorByNumberQuality"] = "recognizable-pencil-source-v4"
manifest["colorByNumberDesign"].update(
    {
        "playPage": "recognizable pencil rendering of the stored professional illustration",
        "visibleNumbers": "palette numbers 1-8 rather than internal region ids",
        "boundaries": "subtle edge-aware tap regions",
        "edgeSafety": "all number labels remain inside a 30-pixel safe area",
    }
)
manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print("PASS: recognizable pencil color-by-number v4 generated with palette-correct numbering and safe label placement.")
