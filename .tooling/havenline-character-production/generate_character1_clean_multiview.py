#!/usr/bin/env python3
"""Generate a clean multi-view Character 1 reconstruction from the approved turnaround.

The original generic crop windows overlapped neighboring poses and included view labels.
Those contaminated inputs produced detached limbs, collapsed glasses, and an unreliable
face. Character 1 has a locked four-column sheet, so this path uses non-overlapping,
reviewed crop windows, excludes every caption/accessory panel, upscales each isolated pose,
and asks TRELLIS to retain substantially more mesh detail than the old 0.92 simplification.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import shutil
import time
import traceback
from typing import Any

from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from gradio_client import Client, handle_file


# Reviewed against the checksum-pinned 512 x 362 approved Character 1 sheet.
# Fractions keep the method deterministic if the same sheet is losslessly resized.
CROP_FRACTIONS = (
    (0.0098, 0.0276, 0.2441, 0.6906),  # front
    (0.2832, 0.0276, 0.5039, 0.6906),  # three-quarter
    (0.5391, 0.0276, 0.7168, 0.6906),  # side
    (0.7812, 0.0276, 0.9961, 0.6906),  # back
)
VIEW_NAMES = ("front", "three-quarter", "side", "back")


def find_path(value: Any):
    if isinstance(value, str):
        path = pathlib.Path(value)
        return path if path.exists() else None
    if isinstance(value, dict):
        path = value.get("path")
        if isinstance(path, str) and pathlib.Path(path).exists():
            return pathlib.Path(path)
    if isinstance(value, (list, tuple)):
        for item in value:
            path = find_path(item)
            if path is not None:
                return path
    return None


def collect_paths(value: Any, output: list[pathlib.Path]) -> None:
    path = find_path(value)
    if path is not None and path.is_file():
        output.append(path)
    if isinstance(value, dict):
        for item in value.values():
            collect_paths(item, output)
    elif isinstance(value, (list, tuple)):
        for item in value:
            collect_paths(item, output)


def crop_clean_views(sheet_path: pathlib.Path, output_directory: pathlib.Path):
    image = Image.open(sheet_path).convert("RGB")
    width, height = image.size
    outputs = []
    reports = []
    for index, ((left, top, right, bottom), view_name) in enumerate(
        zip(CROP_FRACTIONS, VIEW_NAMES),
        start=1,
    ):
        box = (
            int(round(width * left)),
            int(round(height * top)),
            int(round(width * right)),
            int(round(height * bottom)),
        )
        crop = image.crop(box)
        crop = ImageOps.contain(crop, (1024, 1024), Image.Resampling.LANCZOS)
        crop = ImageEnhance.Contrast(crop).enhance(1.035)
        crop = ImageEnhance.Sharpness(crop).enhance(1.18)
        crop = crop.filter(ImageFilter.UnsharpMask(radius=1.0, percent=70, threshold=3))
        canvas = Image.new("RGB", (1024, 1024), (244, 246, 249))
        canvas.paste(crop, ((1024 - crop.width) // 2, (1024 - crop.height) // 2))
        destination = output_directory / f"clean_view_{index}_{view_name}.png"
        canvas.save(destination, optimize=True)
        outputs.append(destination)
        reports.append(
            {
                "view": view_name,
                "cropBoxPixels": list(box),
                "sourceSize": [width, height],
                "output": str(destination),
                "outputSize": list(canvas.size),
                "neighborPosePixelsIncluded": False,
                "captionPixelsIncluded": False,
            }
        )
    return outputs, reports


def generate(character: str, views, output_directory: pathlib.Path, seed: int):
    report = {
        "schemaVersion": 2,
        "character": character,
        "generator": "trellis-community/TRELLIS",
        "mode": "clean-multi-image",
        "seed": seed,
        "meshSimplify": 0.72,
        "textureSize": 2048,
        "success": False,
        "attempts": [],
        "humanVisualApprovalRequired": True,
    }
    token = os.environ.get("HF_TOKEN") or None
    for attempt in range(1, 4):
        attempt_report = {"attempt": attempt, "success": False}
        report["attempts"].append(attempt_report)
        try:
            client = Client(
                "trellis-community/TRELLIS",
                hf_token=token,
                verbose=True,
            )
            client.predict(api_name="/start_session")
            gallery = [
                {"image": handle_file(str(path)), "caption": VIEW_NAMES[index]}
                for index, path in enumerate(views)
            ]
            processed = client.predict(images=gallery, api_name="/preprocess_images")
            processed_items = processed if isinstance(processed, list) else [processed]
            reuploaded = []
            for index, item in enumerate(processed_items):
                value = item.get("image") if isinstance(item, dict) and "image" in item else item
                path = find_path(value)
                if path is None:
                    continue
                copied = output_directory / f"processed_clean_{index:02d}.png"
                shutil.copy2(path, copied)
                reuploaded.append(
                    {"image": handle_file(str(copied)), "caption": VIEW_NAMES[index]}
                )
            if len(reuploaded) != 4:
                raise RuntimeError(f"Expected four processed clean views, resolved {len(reuploaded)}")
            client.predict(api_name="/lambda_1")
            result = client.predict(
                image=None,
                multiimages=reuploaded,
                seed=seed,
                ss_guidance_strength=8.0,
                ss_sampling_steps=20,
                slat_guidance_strength=3.5,
                slat_sampling_steps=20,
                multiimage_algo="multidiffusion",
                mesh_simplify=0.72,
                texture_size=2048,
                api_name="/generate_and_extract_glb",
            )
            files = []
            collect_paths(result, files)
            copied_files = []
            for index, path in enumerate(dict.fromkeys(files)):
                if path.suffix.lower() not in {".glb", ".mp4", ".ply", ".zip"}:
                    continue
                destination = output_directory / f"clean_trellis_{index:02d}{path.suffix.lower()}"
                shutil.copy2(path, destination)
                copied_files.append(str(destination))
            glbs = list(output_directory.glob("clean_trellis_*.glb"))
            if not glbs:
                raise RuntimeError(f"TRELLIS returned no GLB; resolved files={files!r}")
            selected = max(glbs, key=lambda path: path.stat().st_size)
            final_path = output_directory / f"{character}_raw.glb"
            shutil.copy2(selected, final_path)
            if final_path.read_bytes()[:4] != b"glTF":
                raise RuntimeError("Selected Character 1 result is not a binary GLB")
            attempt_report.update(
                success=True,
                copiedFiles=copied_files,
                rawResult=str(result),
            )
            report.update(
                success=True,
                selectedGlb=str(final_path),
                selectedBytes=final_path.stat().st_size,
            )
            return report
        except Exception as exception:
            attempt_report.update(error=repr(exception), traceback=traceback.format_exc())
            if attempt < 3:
                time.sleep(25 * attempt)
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", default="Character1")
    parser.add_argument("--sheet", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--seed", type=int, required=True)
    args = parser.parse_args()

    if args.character != "Character1":
        raise SystemExit("This reviewed crop contract is intentionally Character 1 only")
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    source_sheet = pathlib.Path(args.sheet)
    if not source_sheet.is_file() or source_sheet.stat().st_size == 0:
        raise FileNotFoundError(source_sheet)
    locked_sheet = output / "Character1_approved_turnaround.jpg"
    shutil.copy2(source_sheet, locked_sheet)
    views, crop_report = crop_clean_views(locked_sheet, output)
    report = generate(args.character, views, output, args.seed)
    report["cleanViews"] = crop_report
    report["sourceSheet"] = str(locked_sheet)
    report_path = output / "generation-report.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report.get("success") is True else 1


if __name__ == "__main__":
    raise SystemExit(main())
