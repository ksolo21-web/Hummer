#!/usr/bin/env python3
from __future__ import annotations

import runpy

from PIL import Image, ImageFilter

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v8.py",
    run_name="msc_theme_art_v8",
)
g = module.get("g") or module["source"].__globals__
transparent_object = module["transparent_object"]

# Separate premium male-lion source from the accepted original Lion theme.
g["UNSPLASH"]["lion"] = (
    "https://www.fws.gov/sites/default/files/images/2024-03-2/3586.JPG",
    "African lion — Ken Stansell/U.S. Fish and Wildlife Service, public domain",
)

# Force the changed source to replace v8's cached lioness.
lion_cache = g["SRC"] / "lion.jpg"
if lion_cache.exists():
    lion_cache.unlink()


def moonlit_wolf_final() -> Image.Image:
    width, height = g["W"], g["H"]
    background = g["fit"](g["source"]("moon_forest"), (0.50, 0.42))
    background = g["grade"](
        background,
        contrast=1.16,
        color=0.76,
        brightness=0.64,
        cool=0.82,
    ).convert("RGBA")

    wolf_source = g["source"]("wolf")
    source_w, source_h = wolf_source.size
    # Tighter crop makes the wolf the clear focal subject instead of a small
    # detail while retaining the actual snowy boulders as a coherent foreground.
    crop = wolf_source.crop((
        int(source_w * 0.22),
        int(source_h * 0.20),
        int(source_w * 0.59),
        int(source_h * 0.91),
    ))
    foreground = crop.resize((width, 1110), Image.Resampling.LANCZOS)
    foreground = g["grade"](
        foreground,
        contrast=1.22,
        color=0.62,
        brightness=0.61,
        cool=0.92,
    ).convert("RGBA")

    alpha = Image.new("L", foreground.size, 255)
    pixels = alpha.load()
    for y in range(245):
        value = int(255 * (y / 245) ** 1.65)
        for x in range(width):
            pixels[x, y] = value
    foreground.putalpha(alpha.filter(ImageFilter.GaussianBlur(1.2)))
    background.alpha_composite(foreground, (0, height - foreground.height))

    real_moon = transparent_object(g["source"]("moon"), (325, 325), (0.88, 0.98, 1.12))
    background.alpha_composite(real_moon, (105, 125))
    return g["reading_zones"](background.convert("RGB"), top=0.09, bottom=0.43)


g["moonlit_wolf"] = moonlit_wolf_final

# Replace v8 provisional outputs with the final corrected set.
for path in g["SCENES"].glob("theme_scene_*.webp"):
    path.unlink()
for name in (
    "msc-0.14.2-theme-art-qc-contact-sheet.jpg",
    "SOURCE-CREDITS.json",
    "SHA256SUMS.txt",
):
    target = g["OUT"] / name
    if target.exists():
        target.unlink()
g["CREDITS"].clear()
g["main"]()
