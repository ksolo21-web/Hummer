#!/usr/bin/env python3
from __future__ import annotations

import html
import re
import runpy
import urllib.request

module = runpy.run_path(
    ".msc-build/build-approved-theme-source-artwork-0.14.2-v2.py",
    run_name="msc_theme_art_v2",
)

# Patch the actual globals dictionary used by the imported functions. Updating
# only the dictionary returned by runpy does not reliably replace references
# resolved by source() during the later main() call.
globals_dict = module["source"].__globals__

globals_dict["UNSPLASH"]["map"] = (
    "https://tile.loc.gov/image-services/iiif/service:gmd:gmd7:g7480:g7480:ct011919/full/pct:50/0/default.jpg",
    "A general map of Bible Lands (1913) — Library of Congress Geography and Map Division",
)


def public_unsplash(key: str):
    identifier, credit = globals_dict["UNSPLASH"][key]
    if identifier.startswith("https://"):
        return globals_dict["cached_image"](key, identifier, credit)

    page_url = f"https://unsplash.com/photos/{identifier}"
    request = urllib.request.Request(
        page_url,
        headers={
            "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        page = html.unescape(response.read().decode("utf-8", errors="replace"))

    candidates = re.findall(
        r"https://images\.unsplash\.com/photo-[^?&\"'<>\\]+",
        page,
    )
    if not candidates:
        raise RuntimeError(f"No public image CDN URL found on {page_url}")

    # Prefer the most frequently referenced full-resolution source in the page
    # metadata rather than a UI thumbnail.
    image_base = max(set(candidates), key=candidates.count)
    image_url = image_base + "?auto=format&fit=crop&w=2048&q=92"
    return globals_dict["cached_image"](key, image_url, credit)


globals_dict["unsplash"] = public_unsplash
globals_dict["main"]()
