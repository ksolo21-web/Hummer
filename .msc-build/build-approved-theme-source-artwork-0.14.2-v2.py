#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFont, ImageOps

W, H = 1024, 1536
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "theme-art-qc")
SRC = OUT / "sources"
SCENES = OUT / "scenes"
SRC.mkdir(parents=True, exist_ok=True)
SCENES.mkdir(parents=True, exist_ok=True)

UNSPLASH = {
    "wolf": ("zFx3zqR6nbU", "Tristan Brave / Unsplash License"),
    "moon_forest": ("gmejHJ6k-VY", "Maciek Sulkowski / Unsplash License"),
    "waterfall": ("Duv07nzBO9g", "David Billings / Unsplash License"),
    "rainforest": ("AycIWyyCuVo", "Madeline Hogan / Unsplash License"),
    "ocean": ("Y9TUQP1WX94", "Marian May / Unsplash License"),
    "celestial": ("W_g46NO0xzM", "Scott Lord / Unsplash License"),
    "mountain": ("Svnrlh3lXZ0", "Mario Häfliger / Unsplash License"),
    "garden": ("JXTqptl5gBU", "José Noguera / Unsplash License"),
    "creation_sky": ("Knwea-mLGAg", "Felix Mittermeier / Unsplash License"),
    "lion": ("IPRFX7CVVoU", "Luke Tanis / Unsplash License"),
    "fox": ("https://live.staticflickr.com/65535/55104387112_3a62dabd3b_o.jpg", "NPS / Jacob W. Frank — public domain"),
}

MET_FIXED = {
    "sketch": (382265, "The Sower — The Metropolitan Museum of Art, Public Domain"),
    "parable_sower": (382265, "The Sower — The Metropolitan Museum of Art, Public Domain"),
    "parable_prayer": (382281, "The Pharisee and the Publican — The Metropolitan Museum of Art, Public Domain"),
    "parable_pearl": (382270, "The Pearl of Great Price — The Metropolitan Museum of Art, Public Domain"),
}

MET_SEARCH = {
    "ark": ("Noah's Ark", ("noah", "ark")),
    "red_sea": ("Crossing the Red Sea", ("red sea",)),
    "map": ("Map of Palestine", ("map", "palestine")),
}

CREDITS: dict[str, dict[str, str]] = {}


def request_bytes(url: str, *, label: str, minimum: int = 10_000) -> bytes:
    last: Exception | None = None
    for attempt in range(6):
        try:
            request = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "MyStudyCompanionThemeBuilder/0.14.2 (private visual-QC build)",
                    "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                },
            )
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            if len(data) < minimum:
                raise RuntimeError(f"{label} payload too small: {len(data)} bytes")
            return data
        except Exception as exc:
            last = exc
            time.sleep(3 * (attempt + 1))
    raise RuntimeError(f"Unable to fetch {label}: {url}: {last}")


def request_json(url: str, *, label: str) -> dict:
    return json.loads(request_bytes(url, label=label, minimum=20).decode("utf-8"))


def cached_image(key: str, url: str, credit: str) -> Image.Image:
    path = SRC / f"{key}.jpg"
    if not path.exists() or path.stat().st_size < 10_000:
        path.write_bytes(request_bytes(url, label=key))
    with Image.open(path) as image:
        output = ImageOps.exif_transpose(image).convert("RGB")
    CREDITS[key] = {"credit": credit, "url": url, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    return output


def unsplash(key: str) -> Image.Image:
    identifier, credit = UNSPLASH[key]
    if identifier.startswith("https://"):
        url = identifier
    else:
        url = f"https://unsplash.com/photos/{identifier}/download?force=true&w=2048"
    return cached_image(key, url, credit)


def met_object(key: str, object_id: int, credit: str | None = None) -> Image.Image:
    metadata_url = f"https://collectionapi.metmuseum.org/public/collection/v1/objects/{object_id}"
    metadata = request_json(metadata_url, label=f"Met object {object_id}")
    if not metadata.get("isPublicDomain"):
        raise RuntimeError(f"Met object {object_id} is not public domain")
    image_url = metadata.get("primaryImage") or metadata.get("primaryImageSmall")
    if not image_url:
        raise RuntimeError(f"Met object {object_id} has no primary image")
    final_credit = credit or f"{metadata.get('title', 'Artwork')} — The Metropolitan Museum of Art, Public Domain"
    return cached_image(key, image_url, final_credit)


def met_search(key: str) -> Image.Image:
    query, required = MET_SEARCH[key]
    search_url = "https://collectionapi.metmuseum.org/public/collection/v1/search?" + urllib.parse.urlencode({"hasImages": "true", "q": query})
    results = request_json(search_url, label=f"Met search {query}")
    for object_id in (results.get("objectIDs") or [])[:80]:
        try:
            metadata_url = f"https://collectionapi.metmuseum.org/public/collection/v1/objects/{object_id}"
            metadata = request_json(metadata_url, label=f"Met object {object_id}")
            title = str(metadata.get("title", "")).lower()
            if not metadata.get("isPublicDomain") or not (metadata.get("primaryImage") or metadata.get("primaryImageSmall")):
                continue
            if not all(term in title for term in required):
                continue
            return met_object(key, int(object_id))
        except Exception:
            continue
    raise RuntimeError(f"No suitable public-domain Met image found for {query}")


def source(key: str) -> Image.Image:
    if key in UNSPLASH:
        return unsplash(key)
    if key in MET_FIXED:
        object_id, credit = MET_FIXED[key]
        return met_object(key, object_id, credit)
    return met_search(key)


def fit(image: Image.Image, focal=(0.5, 0.5), size=(W, H)) -> Image.Image:
    return ImageOps.fit(image, size, method=Image.Resampling.LANCZOS, centering=focal)


def grade(image: Image.Image, *, contrast=1.06, color=1.05, brightness=1.0, cool=0.0, warm=0.0) -> Image.Image:
    output = ImageEnhance.Contrast(image).enhance(contrast)
    output = ImageEnhance.Color(output).enhance(color)
    output = ImageEnhance.Brightness(output).enhance(brightness)
    if cool or warm:
        red, green, blue = output.split()
        if cool:
            red = red.point(lambda value: max(0, min(255, int(value * (1 - cool * 0.10)))))
            blue = blue.point(lambda value: max(0, min(255, int(value * (1 + cool * 0.15)))))
        if warm:
            red = red.point(lambda value: max(0, min(255, int(value * (1 + warm * 0.15)))))
            blue = blue.point(lambda value: max(0, min(255, int(value * (1 - warm * 0.10)))))
        output = Image.merge("RGB", (red, green, blue))
    return output


def reading_zones(image: Image.Image, top=0.06, bottom=0.34) -> Image.Image:
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    for index in range(192):
        position = index / 191
        y0 = int(H * index / 192)
        y1 = int(H * (index + 1) / 192) + 1
        alpha = int(255 * (top * max(0, 1 - position / 0.32) + bottom * max(0, (position - 0.58) / 0.42) ** 1.45))
        draw.rectangle((0, y0, W, y1), fill=(2, 7, 15, min(255, alpha)))
    return Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")


def texture(image: Image.Image) -> Image.Image:
    noise = ImageOps.colorize(Image.effect_noise(image.size, 8).convert("L"), "#666666", "#b8b8b8")
    return Image.blend(image, ImageChops.soft_light(image, noise), 0.025)


def save(slug: str, image: Image.Image) -> None:
    if image.size != (W, H):
        image = fit(image)
    path = SCENES / f"theme_scene_{slug}.webp"
    texture(image).save(path, "WEBP", quality=94, method=6)
    if path.stat().st_size < 80_000:
        raise RuntimeError(f"Scene unexpectedly small: {path}")


def moonlit_wolf() -> Image.Image:
    forest = grade(fit(source("moon_forest"), (0.50, 0.48)), contrast=1.12, color=0.92, brightness=0.72, cool=0.55)
    wolf = grade(fit(source("wolf"), (0.58, 0.48)), contrast=1.18, color=0.82, brightness=0.82, cool=0.48)
    # Preserve real photographic detail while establishing one coherent midnight scene.
    composite = Image.blend(forest, ImageChops.screen(forest, wolf), 0.48)
    return reading_zones(composite, top=0.10, bottom=0.42)


def nature(key: str, focal, settings: dict, bottom=0.32) -> Image.Image:
    return reading_zones(grade(fit(source(key), focal), **settings), top=0.04, bottom=bottom)


def parchment(image: Image.Image, focal=(0.5, 0.5), size=(W, H)) -> Image.Image:
    art = fit(image, focal, size)
    art = ImageOps.colorize(ImageOps.grayscale(art), "#261a10", "#f1dfb6")
    paper = Image.new("RGB", size, "#d5bb87")
    return ImageEnhance.Contrast(Image.blend(paper, art, 0.86)).enhance(1.10)


def parable_panels() -> Image.Image:
    canvas = Image.new("RGB", (W, H), "#d4b77e")
    margin, gap = 54, 24
    panel_h = (H - margin * 2 - gap * 2) // 3
    draw = ImageDraw.Draw(canvas)
    for index, key in enumerate(("parable_sower", "parable_prayer", "parable_pearl")):
        panel = parchment(source(key), (0.5, 0.48), (W - margin * 2, panel_h))
        y = margin + index * (panel_h + gap)
        canvas.paste(panel, (margin, y))
        draw.rectangle((margin, y, W - margin, y + panel_h), outline="#5d4123", width=5)
    return reading_zones(canvas, top=0.01, bottom=0.12)


def timeline(ark: Image.Image, red_sea: Image.Image) -> Image.Image:
    canvas = Image.new("RGB", (W, H), "#d1b176")
    draw = ImageDraw.Draw(canvas)
    art = [source("creation_sky"), ark, source("sketch"), red_sea]
    boxes = [(78, 90, 946, 390), (78, 430, 946, 730), (78, 770, 946, 1070), (78, 1110, 946, 1410)]
    for index, (image, box) in enumerate(zip(art, boxes)):
        panel = parchment(image, (0.5, 0.5), (box[2] - box[0], box[3] - box[1]))
        canvas.paste(panel, box[:2])
        draw.rectangle(box, outline="#66451f", width=5)
        if index < 3:
            x = W // 2
            draw.line((x, box[3], x, boxes[index + 1][1]), fill="#7c5528", width=7)
            cy = (box[3] + boxes[index + 1][1]) // 2
            draw.ellipse((x - 12, cy - 12, x + 12, cy + 12), fill="#e1c279", outline="#5c3d1d", width=3)
    return reading_zones(canvas, top=0.0, bottom=0.08)


def contact_sheet(slugs: list[str]) -> None:
    thumb_w, thumb_h, label_h = 256, 384, 44
    sheet = Image.new("RGB", (thumb_w * 4, (thumb_h + label_h) * 4), "#10151d")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, slug in enumerate(slugs):
        with Image.open(SCENES / f"theme_scene_{slug}.webp") as image:
            thumb = image.convert("RGB").resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        x = (index % 4) * thumb_w
        y = (index // 4) * (thumb_h + label_h)
        sheet.paste(thumb, (x, y))
        draw.text((x + 9, y + thumb_h + 14), slug.replace("_", " ").title(), fill="#ffffff", font=font)
    sheet.save(OUT / "msc-0.14.2-theme-art-qc-contact-sheet.jpg", quality=95)


def main() -> None:
    ark = source("ark")
    red_sea = source("red_sea")
    bible_map = source("map")

    save("moonlit_wolf", moonlit_wolf())
    save("waterfall_serenity", nature("waterfall", (0.50, 0.50), dict(contrast=1.12, color=1.10, brightness=0.92, cool=0.12), 0.29))
    save("rainforest_harmony", nature("rainforest", (0.50, 0.50), dict(contrast=1.10, color=1.14, brightness=0.88, cool=0.06), 0.34))
    save("ocean_majesty", nature("ocean", (0.52, 0.50), dict(contrast=1.12, color=1.08, brightness=0.92, cool=0.20), 0.34))
    save("celestial_wonder", nature("celestial", (0.50, 0.50), dict(contrast=1.14, color=1.14, brightness=0.88, cool=0.18), 0.34))
    save("mountain_sunrise", nature("mountain", (0.50, 0.52), dict(contrast=1.08, color=1.10, brightness=0.93, warm=0.20), 0.30))
    save("creation_garden", nature("garden", (0.50, 0.54), dict(contrast=1.08, color=1.13, brightness=0.94, warm=0.08), 0.28))
    save("bible_sketch_study", reading_zones(parchment(source("sketch"), (0.5, 0.48)), top=0.01, bottom=0.18))
    save("parable_line_panels", parable_panels())
    save("noahs_ark", reading_zones(grade(fit(ark, (0.5, 0.50)), contrast=1.10, color=1.08, brightness=0.91, warm=0.10), top=0.04, bottom=0.28))
    save("red_sea_deliverance", reading_zones(grade(fit(red_sea, (0.5, 0.50)), contrast=1.14, color=1.08, brightness=0.90, cool=0.12), top=0.04, bottom=0.32))
    save("creation_sky", nature("creation_sky", (0.5, 0.48), dict(contrast=1.12, color=1.12, brightness=0.90, cool=0.12), 0.31))
    save("bible_timeline", timeline(ark, red_sea))
    save("bible_map", reading_zones(ImageEnhance.Contrast(parchment(bible_map, (0.5, 0.52))).enhance(1.08), top=0.01, bottom=0.16))
    save("lion_premium_2", nature("lion", (0.55, 0.48), dict(contrast=1.14, color=1.04, brightness=0.87, warm=0.18), 0.38))
    save("fox_premium_2", nature("fox", (0.50, 0.48), dict(contrast=1.12, color=1.10, brightness=0.90, cool=0.06), 0.35))

    slugs = [
        "moonlit_wolf", "waterfall_serenity", "rainforest_harmony", "ocean_majesty",
        "celestial_wonder", "mountain_sunrise", "creation_garden", "bible_sketch_study",
        "parable_line_panels", "noahs_ark", "red_sea_deliverance", "creation_sky",
        "bible_timeline", "bible_map", "lion_premium_2", "fox_premium_2",
    ]
    contact_sheet(slugs)
    manifest = {
        "canvas": [W, H],
        "themes": slugs,
        "sources": CREDITS,
        "rules": [
            "standalone full-scene artwork",
            "no phone or UI baked into artwork",
            "no blurred crop-fill",
            "accepted first nine themes are untouched",
        ],
    }
    (OUT / "SOURCE-CREDITS.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    with (OUT / "SHA256SUMS.txt").open("w", encoding="utf-8") as output:
        for path in sorted(SCENES.glob("*.webp")):
            output.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")
    print(f"PASS: generated {len(slugs)} full-scene theme assets and contact sheet")


if __name__ == "__main__":
    main()
