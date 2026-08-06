#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib

from PIL import Image, ImageEnhance, ImageFilter, ImageOps


def main() -> int:
    parser = argparse.ArgumentParser()
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
    # The approved turnaround sheets place the unobstructed three-quarter front view
    # in the left quarter. Exclude the title and bottom view label before reconstruction.
    crop = image.crop(
        (
            int(width * 0.015),
            int(height * 0.075),
            int(width * 0.295),
            int(height * 0.875),
        )
    )
    crop = ImageOps.contain(crop, (896, 896), Image.Resampling.LANCZOS)
    crop = ImageEnhance.Contrast(crop).enhance(1.04)
    crop = ImageEnhance.Sharpness(crop).enhance(1.06)
    crop = crop.filter(ImageFilter.UnsharpMask(radius=1.1, percent=80, threshold=3))
    canvas = Image.new("RGB", (896, 896), (244, 246, 249))
    canvas.paste(crop, ((896 - crop.width) // 2, (896 - crop.height) // 2))
    canvas.save(output, quality=96, subsampling=0, optimize=True)

    report = {
        "schemaVersion": 1,
        "character": args.character,
        "source": str(source),
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "sourceSize": [width, height],
        "cropFractions": [0.015, 0.075, 0.295, 0.875],
        "output": str(output),
        "outputBytes": output.stat().st_size,
        "outputSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "approvedTurnaroundView": "three-quarter-front",
    }
    output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
