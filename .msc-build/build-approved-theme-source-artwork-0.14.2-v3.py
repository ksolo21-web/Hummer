#!/usr/bin/env python3
from __future__ import annotations

import runpy

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v2.py",
    run_name="msc_theme_art_v2",
)

module["UNSPLASH"]["map"] = (
    "https://tile.loc.gov/image-services/iiif/service:gmd:gmd7:g7480:g7480:ct011919/full/pct:50/0/default.jpg",
    "A general map of Bible Lands (1913) — Library of Congress Geography and Map Division",
)

module["main"]()
