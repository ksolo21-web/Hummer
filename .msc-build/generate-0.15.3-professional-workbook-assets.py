#!/usr/bin/env python3
"""Build My Study Companion's offline professional workbook illustration pack.

All interactive variants are derived from professional theme artwork already stored
inside the Android app. Production pages never fall back to primitive geometry.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from skimage.color import rgb2gray
from skimage.feature import canny
from skimage.morphology import dilation, disk, remove_small_objects
from skimage.segmentation import slic

WIDTH = 512
HEIGHT = 768

PALETTE = [
    {"number": 1, "label": "Brown", "rgb": "#8A5A35"},
    {"number": 2, "label": "Skin tone", "rgb": "#E5B17A"},
    {"number": 3, "label": "Red", "rgb": "#C94C4C"},
    {"number": 4, "label": "Blue", "rgb": "#4F83B6"},
    {"number": 5, "label": "Gray", "rgb": "#858A91"},
    {"number": 6, "label": "Green", "rgb": "#5C8B57"},
    {"number": 7, "label": "Tan", "rgb": "#C8A46B"},
    {"number": 8, "label": "Yellow", "rgb": "#E6C44F"},
    {"number": 9, "label": "Black", "rgb": "#252525"},
]
PALETTE_RGB = np.array(
    [tuple(int(entry["rgb"][i : i + 2], 16) for i in (1, 3, 5)) for entry in PALETTE],
    dtype=np.float32,
)


@dataclass(frozen=True)
class SourceSpec:
    id: str
    title: str
    filename: str
    crop: tuple[int, int, int, int] | None = None
    mirror: bool = False


SOURCES = [
    SourceSpec("creation", "Creation", "theme_scene_creation_garden.webp"),
    SourceSpec("noahs-ark", "Noah's Ark", "theme_scene_noahs_ark.webp"),
    SourceSpec("jonah", "Jonah and the Great Fish", "theme_scene_ocean_majesty.webp"),
    SourceSpec("david-goliath", "David and Goliath", "theme_scene_mountain_sunrise.webp"),
    SourceSpec("daniel-lions", "Daniel in the Lions' Den", "theme_scene_lion_premium_2.webp"),
    SourceSpec("jesus-storm", "Jesus Calms the Storm", "theme_scene_red_sea_deliverance.webp", mirror=True),
    SourceSpec("good-samaritan", "The Good Samaritan", "theme_scene_parable_line_panels.webp", (0, 768, 512, 1536)),
    SourceSpec("lost-sheep", "The Lost Sheep", "theme_scene_parable_line_panels.webp", (512, 0, 1024, 768)),
    SourceSpec("talents", "The Talents", "theme_scene_parable_line_panels.webp", (512, 768, 1024, 1536)),
    SourceSpec("prodigal-son", "The Prodigal Son", "theme_scene_parable_line_panels.webp", (0, 0, 512, 768)),
    SourceSpec("wise-builders", "Wise and Foolish Builders", "theme_scene_bible_timeline.webp", (0, 768, 1024, 1536)),
    SourceSpec("armor-of-god", "The Armor of God", "theme_scene_mountain_sunrise.webp", mirror=True),
    SourceSpec("favorite-scripture", "Draw Your Favorite Scripture", "theme_scene_bible_sketch_study.webp"),
    SourceSpec("favorite-animal", "Draw Your Favorite Animal", "theme_scene_rainforest_harmony.webp"),
    SourceSpec("faith-action", "Faith in Action", "theme_scene_red_sea_deliverance.webp"),
    SourceSpec("gratitude-journal", "Gratitude Journal", "theme_scene_warm_editorial.webp"),
]

DRAWING_STEPS = [
    "Block in the largest shapes and the horizon.",
    "Add the main people, animals, buildings, or objects.",
    "Add the complete professional line detail.",
    "Finish expressions, textures, labels, and your own color.",
]


def load_source(source_root: Path, spec: SourceSpec) -> Image.Image:
    path = source_root / spec.filename
    if not path.is_file():
        raise FileNotFoundError(f"Missing stored professional source artwork: {path}")
    image = Image.open(path).convert("RGB")
    if spec.crop:
        image = image.crop(spec.crop)
    if spec.mirror:
        image = ImageOps.mirror(image)
    image = ImageOps.fit(image, (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS)
    image = ImageEnhance.Contrast(image).enhance(1.04)
    return ImageEnhance.Color(image).enhance(1.03)


def generate_labels(rgb: np.ndarray, target_segments: int = 52) -> np.ndarray:
    labels = slic(
        rgb.astype(np.float32) / 255.0,
        n_segments=target_segments,
        compactness=14.0,
        sigma=1.0,
        start_label=1,
        channel_axis=-1,
        enforce_connectivity=True,
        min_size_factor=0.35,
        max_size_factor=2.6,
    ).astype(np.uint16)
    dense = np.zeros_like(labels, dtype=np.uint16)
    for index, old in enumerate(np.unique(labels), start=1):
        dense[labels == old] = index
    return dense


def boundary_pixels(labels: np.ndarray) -> np.ndarray:
    boundary = np.zeros(labels.shape, dtype=bool)
    boundary[:, 1:] |= labels[:, 1:] != labels[:, :-1]
    boundary[:, :-1] |= labels[:, 1:] != labels[:, :-1]
    boundary[1:, :] |= labels[1:, :] != labels[:-1, :]
    boundary[:-1, :] |= labels[1:, :] != labels[:-1, :]
    return dilation(boundary, disk(1))


def line_art(rgb: np.ndarray, labels: np.ndarray, sigma: float, include_regions: bool) -> Image.Image:
    edges = canny(rgb2gray(rgb), sigma=sigma, low_threshold=0.06, high_threshold=0.20)
    edges = remove_small_objects(edges, max_size=42 if sigma <= 2.0 else 90)
    edges = dilation(edges, disk(1 if sigma <= 2.0 else 0))
    if include_regions:
        edges |= boundary_pixels(labels)
    canvas = np.full((HEIGHT, WIDTH), 255, dtype=np.uint8)
    canvas[edges] = 22
    return Image.fromarray(canvas).convert("RGB")


def nearest_palette_number(pixels: np.ndarray) -> int:
    sample = np.median(pixels.reshape(-1, 3), axis=0).astype(np.float32)
    return int(np.argmin(np.sum((PALETTE_RGB - sample) ** 2, axis=1)) + 1)


def region_manifest(rgb: np.ndarray, labels: np.ndarray) -> list[dict]:
    records = []
    for region_id in np.unique(labels):
        ys, xs = np.where(labels == region_id)
        cx, cy = float(xs.mean()), float(ys.mean())
        nearest = int(np.argmin((xs - cx) ** 2 + (ys - cy) ** 2))
        records.append(
            {
                "id": str(int(region_id)),
                "number": nearest_palette_number(rgb[labels == region_id]),
                "centerX": int(xs[nearest]),
                "centerY": int(ys[nearest]),
                "pixelCount": int(xs.size),
            }
        )
    return records


def choose_difference_regions(labels: np.ndarray) -> list[int]:
    candidates = []
    for region_id in np.unique(labels):
        ys, xs = np.where(labels == region_id)
        if xs.size >= 900:
            candidates.append((int(region_id), float(xs.mean()), float(ys.mean()), int(xs.size)))
    candidates.sort(key=lambda item: item[3], reverse=True)
    selected = []
    for candidate in candidates:
        _, x, y, _ = candidate
        if all((x - sx) ** 2 + (y - sy) ** 2 >= 120**2 for _, sx, sy, _ in selected):
            selected.append(candidate)
        if len(selected) == 5:
            break
    for candidate in candidates:
        if len(selected) == 5:
            break
        if candidate[0] not in {item[0] for item in selected}:
            selected.append(candidate)
    return [item[0] for item in selected[:5]]


def changed_image(master: Image.Image, labels: np.ndarray, selected: list[int]) -> Image.Image:
    source = np.asarray(master).copy()
    output = source.copy()
    for index, region_id in enumerate(selected):
        mask = labels == region_id
        pixels = source[mask].astype(np.float32)
        if index == 0:
            pixels = np.clip(pixels * np.array([1.18, 0.76, 0.72]), 0, 255)
        elif index == 1:
            pixels = np.clip(pixels[:, [1, 2, 0]] * 1.04, 0, 255)
        elif index == 2:
            luminance = pixels.mean(axis=1, keepdims=True)
            pixels = np.clip(luminance * 0.72 + pixels * 0.28, 0, 255)
        elif index == 3:
            pixels = np.clip(255.0 - pixels * 0.62, 0, 255)
        else:
            pixels = np.clip(pixels * 0.72 + np.array([55, 42, 16]), 0, 255)
        output[mask] = pixels.astype(np.uint8)
    return Image.fromarray(output)


def differences_manifest(labels: np.ndarray, selected: list[int]) -> list[dict]:
    descriptions = [
        "A changed foreground color or detail",
        "A changed sky or background detail",
        "A changed central object detail",
        "A changed clothing, animal, or landscape detail",
        "A changed lower-scene detail",
    ]
    output = []
    for index, region_id in enumerate(selected):
        ys, xs = np.where(labels == region_id)
        output.append(
            {
                "id": f"detail-{index + 1}",
                "x": int(xs.mean()),
                "y": int(ys.mean()),
                "radius": int(np.clip(math.sqrt(xs.size / math.pi) * 0.72, 34, 76)),
                "label": descriptions[index],
            }
        )
    return output


def save_webp(image: Image.Image, path: Path, quality: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", quality=quality, method=6)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_pack(source_root: Path, output_root: Path) -> None:
    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)
    assets = []
    for spec in SOURCES:
        master = load_source(source_root, spec)
        rgb = np.asarray(master)
        labels = generate_labels(rgb)
        step1_labels = generate_labels(rgb, 10)
        step2_labels = generate_labels(rgb, 22)
        regions = region_manifest(rgb, labels)
        if len(regions) < 35:
            raise RuntimeError(f"{spec.id} generated only {len(regions)} closed regions")
        selected = choose_difference_regions(labels)
        if len(selected) != 5:
            raise RuntimeError(f"{spec.id} did not generate five differences")

        folder = output_root / spec.id
        folder.mkdir(parents=True)
        save_webp(master, folder / "master.webp", 90)
        save_webp(line_art(rgb, labels, 2.35, True), folder / "line.webp", 92)
        save_webp(line_art(rgb, step1_labels, 4.0, True), folder / "drawing-step-1.webp", 91)
        save_webp(line_art(rgb, step2_labels, 2.8, True), folder / "drawing-step-2.webp", 91)
        save_webp(changed_image(master, labels, selected), folder / "difference-changed.webp", 90)
        Image.fromarray(labels.astype(np.uint16)).save(folder / "region-mask.png", "PNG", optimize=True)

        assets.append(
            {
                "id": spec.id,
                "title": spec.title,
                "aspectRatio": WIDTH / HEIGHT,
                "regions": regions,
                "differences": differences_manifest(labels, selected),
                "drawingSteps": DRAWING_STEPS,
            }
        )

    manifest = {
        "version": 3,
        "width": WIDTH,
        "height": HEIGHT,
        "palette": PALETTE,
        "assets": assets,
    }
    (output_root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    sums = []
    for path in sorted(output_root.rglob("*")):
        if path.is_file() and path.name != "SHA256SUMS.txt":
            sums.append(f"{sha256_file(path)}  {path.relative_to(output_root).as_posix()}")
    (output_root / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n")


def validate_pack(root: Path) -> None:
    manifest = json.loads((root / "manifest.json").read_text())
    assert manifest["version"] >= 3
    assert len(manifest["assets"]) == 16
    assert all(len(asset["regions"]) >= 35 for asset in manifest["assets"])
    assert all(len(asset["differences"]) == 5 for asset in manifest["assets"])
    required = [
        "master.webp",
        "line.webp",
        "drawing-step-1.webp",
        "drawing-step-2.webp",
        "difference-changed.webp",
        "region-mask.png",
    ]
    for asset in manifest["assets"]:
        for name in required:
            path = root / asset["id"] / name
            assert path.is_file() and path.stat().st_size > 600, path
        mask = np.asarray(Image.open(root / asset["id"] / "region-mask.png"))
        assert len(np.unique(mask)) >= 35


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--android-only", action="store_true")
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    source_root = repo / "MyStudyCompanion/app/src/main/res/drawable-nodpi"
    android_root = repo / "MyStudyCompanion/app/src/main/assets/workbook"
    build_pack(source_root, android_root)
    validate_pack(android_root)
    if not args.android_only:
        web_root = repo / "MyStudyCompanionWeb/assets/workbook"
        if web_root.exists():
            shutil.rmtree(web_root)
        shutil.copytree(android_root, web_root)
        validate_pack(web_root)
    print(f"Generated {len(SOURCES)} professional workbook packs from stored app artwork.")


if __name__ == "__main__":
    main()
