#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib

from PIL import Image, ImageEnhance, ImageFilter, ImageOps

CANVAS_SIZE = 1024
CROP_FRACTIONS = (0.015, 0.075, 0.295, 0.875)


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Prepare an approved HAVENLINE turnaround view for Stable Fast 3D."
    )
    parser.add_argument("--character", required=True)
    parser.add_argument("--sheet", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source = pathlib.Path(args.sheet)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not source.is_file() or source.stat().st_size == 0:
        raise FileNotFoundError(source)

    image = Image.open(source).convert("RGB")
    width, height = image.size
    left, top, right, bottom = CROP_FRACTIONS
    crop = image.crop(
        (
            int(width * left),
            int(height * top),
            int(width * right),
            int(height * bottom),
        )
    )
    crop = ImageOps.contain(
        crop,
        (CANVAS_SIZE - 96, CANVAS_SIZE - 96),
        Image.Resampling.LANCZOS,
    )
    crop = ImageEnhance.Contrast(crop).enhance(1.04)
    crop = ImageEnhance.Sharpness(crop).enhance(1.06)
    crop = crop.filter(ImageFilter.UnsharpMask(radius=1.1, percent=80, threshold=3))

    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (244, 246, 249, 255))
    canvas.paste(
        crop.convert("RGBA"),
        ((CANVAS_SIZE - crop.width) // 2, (CANVAS_SIZE - crop.height) // 2),
    )
    canvas.save(output, format="PNG", optimize=True)

    report = {
        "schemaVersion": 1,
        "character": args.character,
        "generatorInputFor": "Stability-AI/stable-fast-3d",
        "source": str(source),
        "sourceSha256": sha256(source),
        "sourceSize": [width, height],
        "cropFractions": list(CROP_FRACTIONS),
        "approvedTurnaroundView": "three-quarter-front",
        "output": str(output),
        "outputSize": [CANVAS_SIZE, CANVAS_SIZE],
        "outputBytes": output.stat().st_size,
        "outputSha256": sha256(output),
        "backgroundRemovalDelegatedToGenerator": True,
        "humanVisualApprovalRequired": True,
    }
    output.with_suffix(".json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
