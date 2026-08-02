#!/usr/bin/env python3
from __future__ import annotations

import runpy

from PIL import Image, ImageEnhance, ImageFilter, ImageOps

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v2.py",
    run_name="msc_theme_art_v2",
)
g = module["source"].__globals__

# Stable direct sources. v8 loads v2 without executing it, patches every
# source before the first download, and therefore cannot reuse the rejected
# cached Noah, lion, moon, or planet imagery.
g["UNSPLASH"].update({
    "wolf": (
        "https://npgallery.nps.gov/GetAsset/F7344FE1-1DD8-B71B-0B9FC4428C013BA0/proxyhires.jpg",
        "Wolf howling on glacial erratic — NPS/Jim Peaco, public domain",
    ),
    "moon_forest": (
        "https://npgallery.nps.gov/GetAsset/6BD9A8C7-155D-451F-6704CDBE39BAAD12/proxyhires.jpg",
        "Night Sky at Cedar Breaks — National Park Service, public domain",
    ),
    "waterfall": (
        "https://npgallery.nps.gov/GetAsset/69F47B39-25A5-4AB0-8C79-D8CA8584FD19/proxyhires.jpg",
        "Waterfall along Little River Trail — National Park Service, public domain",
    ),
    "rainforest": (
        "https://npgallery.nps.gov/GetAsset/CEC4CAF2-1DD8-B71B-0BECB443C4025160/proxyhires.jpg",
        "Hoh Rain Forest — NPS/M. Juran, public domain",
    ),
    "ocean": (
        "https://npgallery.nps.gov/GetAsset/66B4306D-1DD8-B71B-0BF19CD1903E31F7/proxyhires.jpg",
        "Point Reyes Beach and Pacific Ocean — National Park Service, public domain",
    ),
    "celestial": (
        "https://npgallery.nps.gov/GetAsset/8626EF17-8163-4B4E-A742-D7439506F6D6/proxyhires.jpg",
        "Milky Way over Rocky Mountain peaks — P. Gaines/NPS, public domain",
    ),
    "mountain": (
        "https://npgallery.nps.gov/GetAsset/3C95838731D24C2B91CEDAFCF3E0C6F3/proxyhires.jpg",
        "Sunrise from Sahale Glacier Camp — North Cascades NPS, public domain",
    ),
    "garden": (
        "https://npgallery.nps.gov/GetAsset/F263FF84-155D-4519-3E8E5FBE172568ED/proxyhires.jpg",
        "Summer wildflowers in Olympic National Park — NPS, public domain",
    ),
    "creation_sky": (
        "https://npgallery.nps.gov/GetAsset/1A4E617C-155D-451F-6752E10E365AC5CF/proxyhires.jpg",
        "Sunrise over an ocean of fog — National Park Service, public domain",
    ),
    "fox": (
        "https://npgallery.nps.gov/GetAsset/815590B6E5B34917819D19421B996381/proxyhires.jpg",
        "Red fox, Gates of the Arctic — National Park Service, public domain",
    ),
    "map": (
        "https://tile.loc.gov/image-services/iiif/service:gmd:gmd7:g7480:g7480:ct011919/full/pct:50/0/default.jpg",
        "A general map of Bible Lands (1913) — Library of Congress",
    ),
    "lion": (
        "https://www.fws.gov/sites/default/files/images/2024-03-3/5960.JPG",
        "African lion — Ken Stansell/U.S. Fish and Wildlife Service, public domain",
    ),
    "moon": (
        "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/releases/1999/04/STScI-01EVTA4B0CT67AJW6WYMQRF0MY.tif?w=2000",
        "Earth's Moon — Lick Observatory/NASA Hubble, public domain",
    ),
    "saturn": (
        "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/releases/1990/11/STScI-01EVTBDACAJS4TJP8SKB5KW3X6.tif?w=2000",
        "Saturn — NASA/ESA/STScI Hubble, public domain",
    ),
})

g["MET_FIXED"]["ark"] = (
    371039,
    "Noah's Ark, Currier & Ives — The Metropolitan Museum of Art, Public Domain",
)
g["MET_SEARCH"].pop("ark", None)
g["MET_SEARCH"].pop("lion", None)


def transparent_object(image: Image.Image, size: tuple[int, int], tint: tuple[float, float, float] | None = None) -> Image.Image:
    object_image = ImageOps.contain(image.convert("RGB"), size, Image.Resampling.LANCZOS)
    if tint:
        red, green, blue = object_image.split()
        red = red.point(lambda value: max(0, min(255, int(value * tint[0]))))
        green = green.point(lambda value: max(0, min(255, int(value * tint[1]))))
        blue = blue.point(lambda value: max(0, min(255, int(value * tint[2]))))
        object_image = Image.merge("RGB", (red, green, blue))
    luminance = ImageOps.grayscale(object_image)
    mask = luminance.point(lambda value: 0 if value < 8 else min(255, int((value - 8) * 2.8)))
    mask = mask.filter(ImageFilter.GaussianBlur(1.3))
    rgba = object_image.convert("RGBA")
    rgba.putalpha(mask)
    return rgba


def moonlit_wolf_fixed() -> Image.Image:
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
    crop = wolf_source.crop((
        int(source_w * 0.13),
        int(source_h * 0.20),
        int(source_w * 0.62),
        int(source_h * 0.98),
    ))
    foreground = crop.resize((width, 1060), Image.Resampling.LANCZOS)
    foreground = g["grade"](
        foreground,
        contrast=1.20,
        color=0.62,
        brightness=0.62,
        cool=0.90,
    ).convert("RGBA")

    alpha = Image.new("L", foreground.size, 255)
    alpha_pixels = alpha.load()
    for y in range(220):
        value = int(255 * (y / 220) ** 1.6)
        for x in range(width):
            alpha_pixels[x, y] = value
    foreground.putalpha(alpha)
    background.alpha_composite(foreground, (0, height - foreground.height))

    real_moon = transparent_object(g["source"]("moon"), (330, 330), (0.88, 0.98, 1.12))
    halo = Image.new("RGBA", background.size, (0, 0, 0, 0))
    halo_circle = Image.new("RGBA", (470, 470), (155, 205, 242, 0))
    halo_alpha = Image.new("L", halo_circle.size, 0)
    halo_pixels = halo_alpha.load()
    center = 235
    for y in range(470):
        for x in range(470):
            distance = ((x - center) ** 2 + (y - center) ** 2) ** 0.5
            if distance < 225:
                halo_pixels[x, y] = int(70 * max(0.0, 1 - distance / 225) ** 2)
    halo_circle.putalpha(halo_alpha.filter(ImageFilter.GaussianBlur(18)))
    halo.alpha_composite(halo_circle, (50, 70))
    background = Image.alpha_composite(background, halo)
    background.alpha_composite(real_moon, (120, 140))
    return g["reading_zones"](background.convert("RGB"), top=0.09, bottom=0.43)


def celestial_fixed() -> Image.Image:
    background = g["fit"](g["source"]("celestial"), (0.50, 0.47))
    background = g["grade"](
        background,
        contrast=1.18,
        color=1.16,
        brightness=0.78,
        cool=0.25,
    ).convert("RGBA")

    saturn = transparent_object(g["source"]("saturn"), (480, 350), (0.90, 0.98, 1.12))
    moon = transparent_object(g["source"]("moon"), (190, 190), (0.48, 0.66, 1.12))
    saturn = ImageEnhance.Contrast(saturn).enhance(1.12)
    moon = ImageEnhance.Contrast(moon).enhance(1.18)
    background.alpha_composite(saturn, (500, 130))
    background.alpha_composite(moon, (95, 525))
    return g["reading_zones"](background.convert("RGB"), top=0.04, bottom=0.34)


original_nature = g["nature"]


def nature_fixed(key: str, focal, settings: dict, bottom=0.32) -> Image.Image:
    if key == "celestial":
        return celestial_fixed()
    return original_nature(key, focal, settings, bottom)


g["moonlit_wolf"] = moonlit_wolf_fixed
g["nature"] = nature_fixed

g["main"]()
