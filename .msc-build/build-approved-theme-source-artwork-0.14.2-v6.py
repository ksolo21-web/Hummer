#!/usr/bin/env python3
from __future__ import annotations

import runpy

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v2.py",
    run_name="msc_theme_art_v2",
)
g = module["source"].__globals__

# Stable, direct public-domain sources. These avoid login-gated photo services
# and remain fully attributable in the generated source manifest.
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
})

# Use a public-domain museum artwork for the second premium lion rather than
# another generic photo service result.
del g["UNSPLASH"]["lion"]
g["MET_SEARCH"]["lion"] = ("lion", ("lion",))

g["main"]()
