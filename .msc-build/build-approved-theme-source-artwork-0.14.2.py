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

SOURCES = {
    "wolf": ("Gray Wolf in Grand Teton NP-NPS.jpg", "Public domain — U.S. National Park Service"),
    "moon_forest": ("Night in a Moonlit Forest (51144052194).jpg", "CC BY 2.0 — Tero Karppinen; modified"),
    "waterfall": ("Waterfall in a rainforest (Unsplash).jpg", "CC0"),
    "rainforest": ("River in an evergreen forest (Unsplash).jpg", "CC0"),
    "ocean": ("Blue ocean wave (Unsplash).jpg", "CC0"),
    "celestial": ("M20 nebula.jpg", "Public domain — NASA"),
    "mountain": ("Mountain sunrise (Unsplash).jpg", "CC0"),
    "garden": ("White meadow by a mountain stream (Unsplash).jpg", "CC0"),
    "sketch": ("Dore Bible The Gleaners.jpg", "Public domain — Gustave Doré"),
    "parable_sower": ("The Sower (The Parables of Our Lord and Saviour Jesus Christ) MET DP835795.jpg", "Public domain / Met Open Access"),
    "parable_prayer": ("The Pharisee and the Publican (The Parables of Our Lord and Saviour Jesus Christ) MET DP835786.jpg", "Public domain / Met Open Access"),
    "parable_pearl": ("The Pearl of Great Price (The Parables of Our Lord and Saviour Jesus Christ) MET DP835752.jpg", "Public domain / Met Open Access"),
    "ark": ("Noah's Ark - Google Art Project.jpg", "Public domain"),
    "red_sea": ("Aivazovsky Passage of the Jews through the Red Sea.jpg", "Public domain — Ivan Aivazovsky"),
    "creation": ("The Creation - George Graham.jpg", "CC0"),
    "map": ("Rawson, A.L. Map of Palestine and all Bible lands. 1873.jpg", "Public domain"),
    "lion": ("Pensive lion (Unsplash).jpg", "CC0"),
    "fox": ("Red fox sitting in the snow (55104387112).jpg", "Public domain — U.S. National Park Service / Jacob W. Frank"),
}


def commons_original(filename: str) -> str:
    # Special:Redirect/file resolves the exact Commons file and can return a
    # production-sized thumbnail, avoiding unnecessary multi-megabyte originals.
    return (
        "https://commons.wikimedia.org/wiki/Special:Redirect/file/"
        + urllib.parse.quote(filename.replace(" ", "_"), safe="()_,.-")
        + "?width=2048"
    )


def download(key: str) -> Path:
    filename, _license = SOURCES[key]
    ext = Path(filename).suffix or ".jpg"
    target = SRC / f"{key}{ext}"
    if target.exists() and target.stat().st_size > 10_000:
        return target
    url = commons_original(filename)
    data = None
    last_error = None
    for attempt in range(7):
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "MyStudyCompanionThemeBuilder/0.14.2 art-qc (private app build; contact: GitHub ksolo21-web/Hummer)",
                "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                data = response.read()
            break
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code not in (429, 500, 502, 503, 504):
                raise RuntimeError(f"Failed to download {key}: {url}: {exc}") from exc
        except Exception as exc:
            last_error = exc
        time.sleep(min(45, 5 * (attempt + 1)))
    if data is None:
        raise RuntimeError(f"Failed to download {key} after retries: {url}: {last_error}")
    time.sleep(2.5)
    if len(data) < 10_000:
        raise RuntimeError(f"Downloaded source too small for {key}: {len(data)} bytes")
    target.write_bytes(data)
    return target


def load(key: str) -> Image.Image:
    with Image.open(download(key)) as im:
        return ImageOps.exif_transpose(im).convert("RGB")


def fit(im: Image.Image, focal=(0.5, 0.5), size=(W, H)) -> Image.Image:
    return ImageOps.fit(im, size, method=Image.Resampling.LANCZOS, centering=focal)


def grade(im: Image.Image, *, contrast=1.06, color=1.05, brightness=1.0, cool=0.0, warm=0.0) -> Image.Image:
    out = ImageEnhance.Contrast(im).enhance(contrast)
    out = ImageEnhance.Color(out).enhance(color)
    out = ImageEnhance.Brightness(out).enhance(brightness)
    if cool or warm:
        r, g, b = out.split()
        if cool:
            r = r.point(lambda x: max(0, min(255, int(x * (1 - cool * 0.12)))))
            b = b.point(lambda x: max(0, min(255, int(x * (1 + cool * 0.18)))))
        if warm:
            r = r.point(lambda x: max(0, min(255, int(x * (1 + warm * 0.18)))))
            b = b.point(lambda x: max(0, min(255, int(x * (1 - warm * 0.12)))))
        out = Image.merge("RGB", (r, g, b))
    return out


def readability(im: Image.Image, top=0.08, bottom=0.40) -> Image.Image:
    overlay = Image.new("RGBA", im.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    bands = 192
    for i in range(bands):
        t = i / (bands - 1)
        y0 = int(H * i / bands)
        y1 = int(H * (i + 1) / bands) + 1
        alpha = int(255 * (top * max(0.0, 1 - t / 0.35) + bottom * max(0.0, (t - 0.55) / 0.45) ** 1.35))
        draw.rectangle((0, y0, W, y1), fill=(2, 7, 14, min(255, alpha)))
    return Image.alpha_composite(im.convert("RGBA"), overlay).convert("RGB")


def subtle_texture(im: Image.Image, opacity=0.035) -> Image.Image:
    noise = Image.effect_noise(im.size, 10).convert("L")
    noise = ImageOps.colorize(noise, "#6a6a6a", "#bcbcbc")
    return Image.blend(im, ImageChops.soft_light(im, noise), opacity)


def save(slug: str, im: Image.Image) -> None:
    im = fit(im) if im.size != (W, H) else im
    im = subtle_texture(im)
    path = SCENES / f"theme_scene_{slug}.webp"
    im.save(path, "WEBP", quality=94, method=6)
    if path.stat().st_size < 60_000:
        raise RuntimeError(f"Scene unexpectedly small: {path}")


def moonlit_wolf() -> Image.Image:
    wolf = fit(load("wolf"), (0.50, 0.43))
    forest = fit(load("moon_forest"), (0.52, 0.46))
    forest = grade(forest, contrast=1.12, color=0.82, brightness=0.72, cool=0.72)
    wolf = grade(wolf, contrast=1.16, color=0.72, brightness=0.78, cool=0.85)
    blended = Image.blend(forest, ImageChops.screen(forest, wolf), 0.62)
    draw = ImageDraw.Draw(blended, "RGBA")
    cx, cy, radius = 235, 230, 146
    for r in range(radius + 90, radius - 1, -3):
        t = (r - radius) / 90
        draw.ellipse((cx-r, cy-r, cx+r, cy+r), fill=(175, 211, 239, int(70 * (1 - t) ** 2)))
    draw.ellipse((cx-radius, cy-radius, cx+radius, cy+radius), fill=(224, 237, 246, 235))
    for ox, oy, rr in [(-45, -30, 24), (36, 10, 32), (-5, 55, 18)]:
        draw.ellipse((cx+ox-rr, cy+oy-rr, cx+ox+rr, cy+oy+rr), fill=(172, 190, 205, 46))
    return readability(blended, top=0.12, bottom=0.47)


def nature(key: str, focal, settings, bottom=0.34) -> Image.Image:
    return readability(grade(fit(load(key), focal), **settings), top=0.05, bottom=bottom)


def parchment(im: Image.Image, focal=(0.5, 0.5), sepia=True) -> Image.Image:
    im = fit(im, focal)
    if sepia:
        im = ImageOps.colorize(ImageOps.grayscale(im), "#2c1d12", "#f2dfb5")
    paper = subtle_texture(Image.new("RGB", (W, H), "#d9bf8c"), 0.12)
    return ImageEnhance.Contrast(Image.blend(paper, im, 0.82)).enhance(1.08)


def parable_panels() -> Image.Image:
    canvas = Image.new("RGB", (W, H), "#d7bc86")
    margin, gutter = 54, 24
    panel_h = (H - margin * 2 - gutter * 2) // 3
    for i, key in enumerate(("parable_sower", "parable_prayer", "parable_pearl")):
        art = fit(parchment(load(key), (0.5, 0.46), True), (0.5, 0.5), (W - margin * 2, panel_h))
        y = margin + i * (panel_h + gutter)
        canvas.paste(art, (margin, y))
        ImageDraw.Draw(canvas).rectangle((margin, y, W-margin, y+panel_h), outline="#5d4328", width=4)
    return readability(canvas, top=0.02, bottom=0.16)


def timeline() -> Image.Image:
    canvas = subtle_texture(Image.new("RGB", (W, H), "#d4b47d"), 0.16)
    arts = [load("creation"), load("ark"), load("sketch"), load("red_sea")]
    bands = [(80, 110, 944, 390), (80, 430, 944, 710), (80, 750, 944, 1030), (80, 1070, 944, 1350)]
    draw = ImageDraw.Draw(canvas)
    for idx, (art, box) in enumerate(zip(arts, bands)):
        panel = fit(art, (0.5, 0.5), (box[2]-box[0], box[3]-box[1]))
        panel = ImageOps.colorize(ImageOps.grayscale(panel), "#25190e", "#e9cf9c")
        canvas.paste(panel, box[:2])
        draw.rectangle(box, outline="#6e4b22", width=4)
        if idx < 3:
            x = W // 2
            draw.line((x, box[3], x, bands[idx+1][1]), fill="#7d5525", width=7)
            cy = (box[3] + bands[idx+1][1]) // 2
            draw.ellipse((x-12, cy-12, x+12, cy+12), fill="#d7b467", outline="#5b3c1c", width=3)
    draw.line((W//2, 42, W//2, 110), fill="#7d5525", width=7)
    draw.ellipse((W//2-14, 28, W//2+14, 56), fill="#d7b467", outline="#5b3c1c", width=3)
    return readability(canvas, top=0.0, bottom=0.12)


def bible_map() -> Image.Image:
    image = ImageEnhance.Contrast(ImageEnhance.Color(parchment(load("map"), (0.53, 0.52), False)).enhance(0.72)).enhance(1.10)
    return readability(image, top=0.02, bottom=0.18)


def main() -> None:
    for key in SOURCES:
        download(key)
    save("moonlit_wolf", moonlit_wolf())
    save("waterfall_serenity", nature("waterfall", (0.50, 0.48), dict(contrast=1.12, color=1.13, brightness=0.93, cool=0.18), 0.30))
    save("rainforest_harmony", nature("rainforest", (0.50, 0.48), dict(contrast=1.10, color=1.15, brightness=0.88, cool=0.10), 0.36))
    save("ocean_majesty", nature("ocean", (0.56, 0.50), dict(contrast=1.12, color=1.10, brightness=0.94, cool=0.26), 0.34))
    save("celestial_wonder", nature("celestial", (0.50, 0.48), dict(contrast=1.13, color=1.16, brightness=0.88, cool=0.20), 0.36))
    save("mountain_sunrise", nature("mountain", (0.47, 0.48), dict(contrast=1.08, color=1.12, brightness=0.94, warm=0.26), 0.34))
    save("creation_garden", nature("garden", (0.51, 0.54), dict(contrast=1.08, color=1.15, brightness=0.94, warm=0.08), 0.30))
    save("bible_sketch_study", readability(parchment(load("sketch"), (0.50, 0.47), True), top=0.02, bottom=0.22))
    save("parable_line_panels", parable_panels())
    save("noahs_ark", readability(grade(fit(load("ark"), (0.50, 0.48)), contrast=1.08, color=1.08, brightness=0.92, warm=0.12), top=0.04, bottom=0.28))
    save("red_sea_deliverance", readability(grade(fit(load("red_sea"), (0.50, 0.50)), contrast=1.13, color=1.07, brightness=0.90, cool=0.16), top=0.05, bottom=0.34))
    save("creation_sky", readability(grade(fit(load("creation"), (0.50, 0.50)), contrast=1.10, color=1.11, brightness=0.92, warm=0.10), top=0.04, bottom=0.30))
    save("bible_timeline", timeline())
    save("bible_map", bible_map())
    save("lion_premium_2", readability(grade(fit(load("lion"), (0.57, 0.47)), contrast=1.13, color=1.04, brightness=0.88, warm=0.20), top=0.08, bottom=0.38))
    save("fox_premium_2", readability(grade(fit(load("fox"), (0.50, 0.46)), contrast=1.12, color=1.10, brightness=0.90, cool=0.08), top=0.06, bottom=0.36))

    slugs = ["moonlit_wolf", "waterfall_serenity", "rainforest_harmony", "ocean_majesty", "celestial_wonder", "mountain_sunrise", "creation_garden", "bible_sketch_study", "parable_line_panels", "noahs_ark", "red_sea_deliverance", "creation_sky", "bible_timeline", "bible_map", "lion_premium_2", "fox_premium_2"]
    thumb_w, thumb_h = 256, 384
    sheet = Image.new("RGB", (4 * thumb_w, 4 * (thumb_h + 42)), "#12161d")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=18)
    for i, slug in enumerate(slugs):
        with Image.open(SCENES / f"theme_scene_{slug}.webp") as image:
            thumb = image.convert("RGB").resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        x, y = (i % 4) * thumb_w, (i // 4) * (thumb_h + 42)
        sheet.paste(thumb, (x, y))
        draw.rectangle((x, y + thumb_h, x + thumb_w, y + thumb_h + 42), fill="#12161d")
        draw.text((x + 10, y + thumb_h + 12), slug.replace("_", " ").title(), fill="white", font=font)
    sheet.save(OUT / "msc-0.14.2-theme-art-qc-contact-sheet.jpg", quality=94)

    manifest = {"canvas": [W, H], "themes": slugs, "sources": {key: {"filename": value[0], "license": value[1], "url": commons_original(value[0])} for key, value in SOURCES.items()}, "rules": ["standalone full-scene artwork", "no phone or UI baked into artwork", "no blurred crop-fill", "accepted first nine themes are untouched"]}
    (OUT / "SOURCE-CREDITS.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    with (OUT / "SHA256SUMS.txt").open("w", encoding="utf-8") as sums:
        for path in sorted(SCENES.glob("*.webp")):
            sums.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")
    print(f"PASS: generated {len(slugs)} production theme scenes and visual QC contact sheet in {OUT}")


if __name__ == "__main__":
    main()
