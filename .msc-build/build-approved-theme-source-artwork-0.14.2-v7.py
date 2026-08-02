#!/usr/bin/env python3
from __future__ import annotations

import math
import runpy

from PIL import Image, ImageDraw

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v6.py",
    run_name="msc_theme_art_v6",
)

# v6 executes immediately when loaded, so use the underlying v2 function
# globals to replace the rejected visual treatments, clear the generated
# outputs, and render the corrected set deterministically.
g = module.get("g") or module["source"].__globals__

# Correct Noah's Ark to the actual Currier & Ives public-domain artwork.
g["MET_FIXED"]["ark"] = (
    371039,
    "Noah's Ark, Currier & Ives — The Metropolitan Museum of Art, Public Domain",
)
g["MET_SEARCH"].pop("ark", None)

# Correct the additional Lion theme to a real African lion photograph rather
# than the heraldic lion artwork selected by a broad museum search.
g["UNSPLASH"]["lion"] = (
    "https://www.public-domain-image.com/public-domain-images-pictures-free-stock-photos/fauna-animals-public-domain-images-pictures/lion-public-domain-images-pictures/african-lion-close-up.jpg",
    "African lion close-up — Ken Stansell / U.S. Fish and Wildlife Service, public domain",
)
g["MET_SEARCH"].pop("lion", None)


def add_moon(image: Image.Image, center: tuple[int, int], radius: int) -> Image.Image:
    output = image.convert("RGBA")
    glow = Image.new("RGBA", output.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow, "RGBA")
    cx, cy = center
    for extra in range(130, 0, -4):
        alpha = int(42 * (1 - extra / 130) ** 2)
        r = radius + extra
        draw.ellipse((cx-r, cy-r, cx+r, cy+r), fill=(164, 205, 235, alpha))
    draw.ellipse((cx-radius, cy-radius, cx+radius, cy+radius), fill=(225, 238, 247, 244))
    for ox, oy, rr, alpha in [(-42, -30, 25, 38), (34, 8, 30, 34), (-8, 48, 19, 30)]:
        draw.ellipse((cx+ox-rr, cy+oy-rr, cx+ox+rr, cy+oy+rr), fill=(151, 170, 187, alpha))
    return Image.alpha_composite(output, glow).convert("RGB")


def moonlit_wolf_fixed() -> Image.Image:
    # Use the real howling-wolf landscape as one coherent scene; do not overlay
    # two full photographs, which created the rejected ghosted composite.
    wolf = g["fit"](g["source"]("wolf"), (0.55, 0.47))
    wolf = g["grade"](
        wolf,
        contrast=1.18,
        color=0.72,
        brightness=0.72,
        cool=0.78,
    )
    wolf = add_moon(wolf, (760, 250), 148)
    return g["reading_zones"](wolf, top=0.11, bottom=0.43)


def add_planets(image: Image.Image) -> Image.Image:
    output = image.convert("RGBA")
    planets = Image.new("RGBA", output.size, (0, 0, 0, 0))
    pixels = planets.load()
    specs = [
        (790, 280, 108, (33, 59, 125), (138, 180, 255)),
        (210, 590, 58, (66, 34, 92), (218, 128, 255)),
    ]
    for cx, cy, radius, shadow, light in specs:
        light_x, light_y = cx - radius * 0.45, cy - radius * 0.55
        for y in range(max(0, cy-radius-4), min(output.height, cy+radius+5)):
            for x in range(max(0, cx-radius-4), min(output.width, cx+radius+5)):
                dx, dy = x-cx, y-cy
                distance = math.sqrt(dx*dx + dy*dy)
                if distance > radius:
                    continue
                nx = (x - light_x) / (radius * 1.65)
                ny = (y - light_y) / (radius * 1.65)
                illumination = max(0.0, min(1.0, 1.05 - math.sqrt(nx*nx + ny*ny)))
                edge = max(0.0, min(1.0, (radius-distance) / 8.0))
                red = int(shadow[0] + (light[0]-shadow[0]) * illumination)
                green = int(shadow[1] + (light[1]-shadow[1]) * illumination)
                blue = int(shadow[2] + (light[2]-shadow[2]) * illumination)
                pixels[x, y] = (red, green, blue, int(235 * edge))
        draw = ImageDraw.Draw(planets, "RGBA")
        draw.ellipse(
            (cx-radius-5, cy-radius-5, cx+radius+5, cy+radius+5),
            outline=(165, 205, 255, 120),
            width=5,
        )
    return Image.alpha_composite(output, planets).convert("RGB")


original_nature = g["nature"]


def nature_fixed(key: str, focal, settings: dict, bottom=0.32) -> Image.Image:
    scene = original_nature(key, focal, settings, bottom)
    if key == "celestial":
        scene = add_planets(scene)
        scene = g["reading_zones"](scene, top=0.04, bottom=bottom)
    return scene


g["moonlit_wolf"] = moonlit_wolf_fixed
g["nature"] = nature_fixed

# Remove v6's provisional output before re-rendering the corrected set.
for path in g["SCENES"].glob("theme_scene_*.webp"):
    path.unlink()
for name in (
    "msc-0.14.2-theme-art-qc-contact-sheet.jpg",
    "SOURCE-CREDITS.json",
    "SHA256SUMS.txt",
):
    candidate = g["OUT"] / name
    if candidate.exists():
        candidate.unlink()

g["CREDITS"].clear()
g["main"]()
