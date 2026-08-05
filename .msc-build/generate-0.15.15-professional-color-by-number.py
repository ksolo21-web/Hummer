#!/usr/bin/env python3
"""Create playable color-by-number packs from the app's professional source art.

The prior 0.15.15 draft replaced good illustrations with primitive geometric scenes.
This generator keeps the stored illustration as the completion reward and derives a
small set of large, connected, edge-aware regions for the actual activity.
"""
from __future__ import annotations

import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont
from scipy.ndimage import binary_dilation, distance_transform_edt
from skimage.color import rgb2gray, rgb2lab
from skimage.feature import canny
from skimage.morphology import disk, remove_small_objects
from skimage.segmentation import find_boundaries, slic

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "MyStudyCompanion"
WORKBOOK = PROJECT / "app/src/main/assets/workbook"
MANIFEST = WORKBOOK / "manifest.json"
REPORT_DIR = PROJECT / "build/reports/workbook"
WIDTH = 720
HEIGHT = 960
MIN_REGION_PIXELS = 6_000
MIN_REGION_COUNT = 14
MAX_REGION_COUNT = 20

PALETTE = [
    {"number": 1, "label": "Sunlit gold", "hex": "#F2C14E"},
    {"number": 2, "label": "Desert clay", "hex": "#C97B63"},
    {"number": 3, "label": "Olive leaf", "hex": "#789262"},
    {"number": 4, "label": "Clear sky", "hex": "#68A9CF"},
    {"number": 5, "label": "Deep water", "hex": "#356B8C"},
    {"number": 6, "label": "Royal plum", "hex": "#765A87"},
    {"number": 7, "label": "Warm linen", "hex": "#E8D6B4"},
    {"number": 8, "label": "Cedar brown", "hex": "#8A5B3D"},
]
PALETTE_RGB = np.array(
    [tuple(int(item["hex"][index:index + 2], 16) for index in (1, 3, 5)) for item in PALETTE],
    dtype=np.float32,
)
PALETTE_LAB = rgb2lab((PALETTE_RGB / 255.0).reshape(1, len(PALETTE), 3)).reshape(len(PALETTE), 3)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    names = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for name in names:
        path = Path(name)
        if path.is_file():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def relabel(labels: np.ndarray) -> np.ndarray:
    output = np.zeros_like(labels, dtype=np.int16)
    for new_id, old_id in enumerate(sorted(int(value) for value in np.unique(labels) if value > 0), start=1):
        output[labels == old_id] = new_id
    return output


def region_means(labels: np.ndarray, lab: np.ndarray) -> dict[int, np.ndarray]:
    means: dict[int, np.ndarray] = {}
    for region_id in (int(value) for value in np.unique(labels) if value > 0):
        means[region_id] = lab[labels == region_id].mean(axis=0)
    return means


def adjacent_regions(labels: np.ndarray, region_id: int) -> set[int]:
    mask = labels == region_id
    expanded = binary_dilation(mask, structure=np.ones((3, 3), dtype=bool), iterations=1)
    return {int(value) for value in np.unique(labels[expanded & ~mask]) if value > 0 and int(value) != region_id}


def merge_region(labels: np.ndarray, lab: np.ndarray, region_id: int) -> np.ndarray:
    neighbors = adjacent_regions(labels, region_id)
    if not neighbors:
        return labels
    means = region_means(labels, lab)
    source_mean = means[region_id]
    counts = np.bincount(labels.ravel())
    best = min(
        neighbors,
        key=lambda candidate: float(np.linalg.norm(source_mean - means[candidate])) - min(int(counts[candidate]), 80_000) / 80_000.0,
    )
    labels = labels.copy()
    labels[labels == region_id] = best
    return relabel(labels)


def build_regions(rgb: np.ndarray, seed: int) -> np.ndarray:
    lab = rgb2lab(rgb)
    desired = 17 + seed % 4
    labels = slic(
        rgb,
        n_segments=desired,
        compactness=7.5,
        sigma=1.4,
        start_label=1,
        convert2lab=True,
        enforce_connectivity=True,
        slic_zero=True,
        channel_axis=-1,
    ).astype(np.int16)
    labels = relabel(labels)

    # Merge tiny fragments and keep the game comfortably below the cognitive-load ceiling.
    while True:
        counts = np.bincount(labels.ravel())
        existing = [region_id for region_id in range(1, len(counts)) if counts[region_id] > 0]
        too_small = [region_id for region_id in existing if counts[region_id] < MIN_REGION_PIXELS]
        if too_small:
            labels = merge_region(labels, lab, min(too_small, key=lambda value: counts[value]))
            continue
        if len(existing) > MAX_REGION_COUNT:
            labels = merge_region(labels, lab, min(existing, key=lambda value: counts[value]))
            continue
        break

    count = int(labels.max())
    if count < MIN_REGION_COUNT:
        # A second, slightly more spatial pass avoids a page collapsing into a few huge blobs.
        labels = slic(
            rgb,
            n_segments=MIN_REGION_COUNT + 4,
            compactness=11.0,
            sigma=1.1,
            start_label=1,
            convert2lab=True,
            enforce_connectivity=True,
            slic_zero=True,
            channel_axis=-1,
        ).astype(np.int16)
        labels = relabel(labels)
        while True:
            counts = np.bincount(labels.ravel())
            existing = [region_id for region_id in range(1, len(counts)) if counts[region_id] > 0]
            too_small = [region_id for region_id in existing if counts[region_id] < MIN_REGION_PIXELS]
            if not too_small and len(existing) <= MAX_REGION_COUNT:
                break
            labels = merge_region(
                labels,
                lab,
                min(too_small or existing, key=lambda value: counts[value]),
            )

    return relabel(labels)


def adjacency_map(labels: np.ndarray) -> dict[int, set[int]]:
    result = {region_id: set() for region_id in range(1, int(labels.max()) + 1)}
    horizontal = np.stack([labels[:, :-1], labels[:, 1:]], axis=-1).reshape(-1, 2)
    vertical = np.stack([labels[:-1, :], labels[1:, :]], axis=-1).reshape(-1, 2)
    for first, second in np.concatenate([horizontal, vertical], axis=0):
        a, b = int(first), int(second)
        if a > 0 and b > 0 and a != b:
            result[a].add(b)
            result[b].add(a)
    return result


def assign_palette(labels: np.ndarray, rgb: np.ndarray) -> dict[int, int]:
    lab = rgb2lab(rgb)
    means = region_means(labels, lab)
    adjacency = adjacency_map(labels)
    counts = np.bincount(labels.ravel())
    order = sorted(means, key=lambda region_id: (-len(adjacency[region_id]), -int(counts[region_id])))
    assigned: dict[int, int] = {}
    for region_id in order:
        distances = np.linalg.norm(PALETTE_LAB - means[region_id], axis=1)
        ranked = list(np.argsort(distances))
        chosen = ranked[0]
        for candidate in ranked:
            if all(assigned.get(neighbor) != int(candidate) + 1 for neighbor in adjacency[region_id]):
                chosen = int(candidate)
                break
        assigned[region_id] = int(chosen) + 1

    # A useful worksheet should not visually collapse into only a couple of numbers.
    used = set(assigned.values())
    if len(used) < 5:
        for index, region_id in enumerate(order):
            assigned[region_id] = index % 6 + 1
    return assigned


def best_number_center(region_mask: np.ndarray) -> tuple[int, int]:
    distance = distance_transform_edt(region_mask)
    y, x = np.unravel_index(int(distance.argmax()), distance.shape)
    return int(x), int(y)


def region_label(asset_title: str, center_x: int, center_y: int, region_id: int) -> str:
    vertical = "upper" if center_y < HEIGHT * 0.34 else "lower" if center_y > HEIGHT * 0.68 else "middle"
    horizontal = "left" if center_x < WIDTH * 0.34 else "right" if center_x > WIDTH * 0.66 else "center"
    return f"{asset_title} {vertical}-{horizontal} scene area {region_id}"


def make_line_art(rgb: np.ndarray, labels: np.ndarray, centers: dict[int, tuple[int, int]]) -> Image.Image:
    gray = rgb2gray(rgb)
    details = canny(gray, sigma=1.7, low_threshold=0.075, high_threshold=0.22)
    details = remove_small_objects(details, min_size=34)
    boundaries = find_boundaries(labels, mode="thick")
    thick_boundaries = binary_dilation(boundaries, structure=disk(1))
    details = details & ~binary_dilation(thick_boundaries, structure=disk(2))

    line = np.full((HEIGHT, WIDTH), 255, dtype=np.uint8)
    line[details] = 78
    line[thick_boundaries] = 14
    image = Image.fromarray(line, mode="L").convert("RGB")
    draw = ImageDraw.Draw(image)
    number_font = font(26, bold=True)
    for region_id, (x, y) in centers.items():
        text = str(region_id)
        box = draw.textbbox((0, 0), text, font=number_font)
        text_width = box[2] - box[0]
        text_height = box[3] - box[1]
        radius = max(18, int(max(text_width, text_height) * 0.72))
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill="white", outline="#202020", width=2)
        draw.text((x - text_width / 2, y - text_height / 2 - 2), text, fill="#111111", font=number_font)
    return image


def make_completion_art(source: Image.Image, labels: np.ndarray, numbers: dict[int, int]) -> Image.Image:
    source = source.filter(ImageFilter.MedianFilter(3))
    source = ImageEnhance.Contrast(source).enhance(1.04)
    source = ImageEnhance.Color(source).enhance(0.94)
    flat = np.zeros((HEIGHT, WIDTH, 3), dtype=np.uint8)
    for region_id, number in numbers.items():
        flat[labels == region_id] = PALETTE_RGB[number - 1].astype(np.uint8)
    flat_image = Image.fromarray(flat, mode="RGB")
    # Preserve the professional scene while gently harmonizing it with the playable palette.
    return Image.blend(source, flat_image, 0.16)


def mask_image(labels: np.ndarray) -> Image.Image:
    rgb = np.zeros((HEIGHT, WIDTH, 3), dtype=np.uint8)
    rgb[:, :, 0] = labels.astype(np.uint8)
    return Image.fromarray(rgb, mode="RGB")


def resolve_source(folder: Path, asset: dict[str, object]) -> Path:
    candidates = [
        folder / "master.webp",
        WORKBOOK / str(asset.get("masterPath", "")),
        WORKBOOK / str(asset.get("colorMasterPath", "")),
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    fail(f"{asset.get('id')}: professional source master is missing")
    raise AssertionError


def create_contact_sheet(previews: list[tuple[str, Image.Image, Image.Image]]) -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    cell_width, cell_height = 360, 505
    sheet = Image.new("RGB", (cell_width * 4, cell_height * 4), "#E9E5DC")
    draw = ImageDraw.Draw(sheet)
    title_font = font(18, bold=True)
    small_font = font(13)
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
        line_thumb = line.copy()
        line_thumb.thumbnail((160, 410), Image.Resampling.LANCZOS)
        color_thumb = completion.copy()
        color_thumb.thumbnail((160, 410), Image.Resampling.LANCZOS)
        sheet.paste(line_thumb, (x0 + 15, y0 + 50))
        sheet.paste(color_thumb, (x0 + 185, y0 + 50))
        clipped = title if len(title) <= 34 else title[:31] + "…"
        draw.text((x0 + 16, y0 + 18), clipped, fill="#1A1A1A", font=title_font)
        draw.text((x0 + 16, y0 + 468), "PLAY PAGE", fill="#5D6670", font=small_font)
        draw.text((x0 + 185, y0 + 468), "COMPLETED REVEAL", fill="#5D6670", font=small_font)
    path = REPORT_DIR / "color-by-number-professional-contact-sheet.jpg"
    sheet.save(path, quality=92, optimize=True)
    return path


def main() -> None:
    if not MANIFEST.is_file():
        fail("workbook manifest is missing")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assets = manifest.get("assets")
    if not isinstance(assets, list) or len(assets) != 16:
        fail("expected exactly 16 stored professional workbook illustrations")

    previews: list[tuple[str, Image.Image, Image.Image]] = []
    minimum_pixels = WIDTH * HEIGHT
    maximum_regions = 0
    palette_diversity = 8

    for asset in assets:
        asset_id = str(asset.get("id", "")).strip()
        title = str(asset.get("title", asset_id)).strip() or asset_id
        if not asset_id:
            fail("blank workbook asset id")
        folder = WORKBOOK / asset_id
        folder.mkdir(parents=True, exist_ok=True)
        source_path = resolve_source(folder, asset)
        source = Image.open(source_path).convert("RGB").resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)
        rgb = np.asarray(source, dtype=np.float32) / 255.0
        seed = int(hashlib.sha256(asset_id.encode("utf-8")).hexdigest()[:8], 16)
        labels = build_regions(rgb, seed)
        region_count = int(labels.max())
        if not MIN_REGION_COUNT <= region_count <= MAX_REGION_COUNT:
            fail(f"{asset_id}: generated {region_count} regions; expected {MIN_REGION_COUNT}-{MAX_REGION_COUNT}")

        counts = Counter(int(value) for value in labels.ravel() if value > 0)
        if min(counts.values()) < MIN_REGION_PIXELS:
            fail(f"{asset_id}: a region is too small after connected-region cleanup")
        minimum_pixels = min(minimum_pixels, min(counts.values()))
        maximum_regions = max(maximum_regions, region_count)

        numbers = assign_palette(labels, rgb)
        palette_diversity = min(palette_diversity, len(set(numbers.values())))
        centers = {region_id: best_number_center(labels == region_id) for region_id in range(1, region_count + 1)}
        line = make_line_art(rgb, labels, centers)
        completion = make_completion_art(source, labels, numbers)
        region_mask = mask_image(labels)

        completion.save(folder / "color-master.webp", format="WEBP", quality=91, method=6)
        line.save(folder / "color-line.png", format="PNG", optimize=True)
        region_mask.save(folder / "color-region-mask.png", format="PNG", optimize=True)

        asset["colorMasterPath"] = f"{asset_id}/color-master.webp"
        asset["colorLinePath"] = f"{asset_id}/color-line.png"
        asset["colorRegionMaskPath"] = f"{asset_id}/color-region-mask.png"
        asset["colorRegionCount"] = region_count
        asset["colorNumbersUsed"] = sorted(set(numbers.values()))
        asset["colorRegions"] = [
            {
                "id": region_id,
                "number": numbers[region_id],
                "centerX": round(centers[region_id][0] / WIDTH * 1000),
                "centerY": round(centers[region_id][1] / HEIGHT * 1000),
                "label": region_label(title, centers[region_id][0], centers[region_id][1], region_id),
                "pixelCount": counts[region_id],
            }
            for region_id in range(1, region_count + 1)
        ]
        previews.append((title, line, completion))

    if palette_diversity < 5:
        fail(f"palette diversity collapsed to {palette_diversity} colors on at least one page")

    manifest["version"] = 4
    manifest["colorByNumberVersion"] = 2
    manifest["colorByNumberQuality"] = "professional-source-art-v3"
    manifest["colorByNumberDesign"] = {
        "completionReward": "stored professional illustration",
        "regions": "large connected edge-aware segments",
        "numberPlacement": "maximum interior distance",
        "interaction": "tap-fill with hints undo redo reset progress and completion reveal",
    }
    manifest["palette"] = PALETTE
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    contact = create_contact_sheet(previews)
    print(
        "PASS: generated 16 professional-source color-by-number activities; "
        f"minimum region={minimum_pixels} pixels; maximum regions={maximum_regions}; preview={contact}."
    )


if __name__ == "__main__":
    main()
