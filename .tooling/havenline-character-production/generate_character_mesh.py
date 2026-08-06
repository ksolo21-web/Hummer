#!/usr/bin/env python3
import argparse
import json
import pathlib
import shutil
import time
import traceback
from typing import Any

from PIL import Image, ImageOps
from gradio_client import Client, handle_file


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


def crop_views(sheet_path: pathlib.Path, output_directory: pathlib.Path) -> list[pathlib.Path]:
    image = Image.open(sheet_path).convert("RGB")
    width, height = image.size
    top = int(height * 0.035)
    bottom = int(height * 0.83)
    windows = ((0.00, 0.28), (0.22, 0.53), (0.48, 0.79), (0.73, 1.00))
    views = []
    for index, (left_fraction, right_fraction) in enumerate(windows):
        crop = image.crop((int(width * left_fraction), top, int(width * right_fraction), bottom))
        crop = ImageOps.contain(crop, (768, 768), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (768, 768), (244, 246, 249))
        canvas.paste(crop, ((768 - crop.width) // 2, (768 - crop.height) // 2))
        path = output_directory / f"view_{index + 1}.png"
        canvas.save(path, optimize=True)
        views.append(path)
    return views


def generate(character: str, view_paths: list[pathlib.Path], output_directory: pathlib.Path, seed: int) -> dict:
    report = {
        "schemaVersion": 1,
        "character": character,
        "generator": "trellis-community/TRELLIS",
        "mode": "multi-image",
        "seed": seed,
        "success": False,
        "attempts": [],
    }
    for attempt in range(1, 4):
        attempt_report = {"attempt": attempt, "success": False}
        report["attempts"].append(attempt_report)
        try:
            client = Client("trellis-community/TRELLIS", verbose=True)
            client.predict(api_name="/start_session")
            gallery = [{"image": handle_file(str(path)), "caption": path.stem} for path in view_paths]
            processed = client.predict(images=gallery, api_name="/preprocess_images")
            processed_items = processed if isinstance(processed, list) else [processed]
            reuploaded = []
            for index, item in enumerate(processed_items):
                value = item.get("image") if isinstance(item, dict) and "image" in item else item
                path = find_path(value)
                if path is None:
                    continue
                copied = output_directory / f"processed_{index:02d}.png"
                shutil.copy2(path, copied)
                reuploaded.append({"image": handle_file(str(copied)), "caption": f"view_{index}"})
            if len(reuploaded) < 3:
                raise RuntimeError(f"Only {len(reuploaded)} processed views were resolved")
            client.predict(api_name="/lambda_1")
            result = client.predict(
                image=None,
                multiimages=reuploaded,
                seed=seed,
                ss_guidance_strength=7.5,
                ss_sampling_steps=16,
                slat_guidance_strength=3.0,
                slat_sampling_steps=16,
                multiimage_algo="multidiffusion",
                mesh_simplify=0.92,
                texture_size=1024,
                api_name="/generate_and_extract_glb",
            )
            files = []
            collect_paths(result, files)
            copied_files = []
            for index, path in enumerate(dict.fromkeys(files)):
                if path.suffix.lower() not in {".glb", ".mp4", ".ply", ".zip"}:
                    continue
                destination = output_directory / f"trellis_{index:02d}{path.suffix.lower()}"
                shutil.copy2(path, destination)
                copied_files.append(str(destination))
            glbs = list(output_directory.glob("trellis_*.glb"))
            if not glbs:
                raise RuntimeError(f"No GLB returned; files={files!r}")
            selected = max(glbs, key=lambda path: path.stat().st_size)
            final_path = output_directory / f"{character}_raw.glb"
            shutil.copy2(selected, final_path)
            attempt_report.update(success=True, copiedFiles=copied_files, rawResult=str(result))
            report.update(success=True, selectedGlb=str(final_path), selectedBytes=final_path.stat().st_size)
            return report
        except Exception as exception:
            attempt_report.update(error=repr(exception), traceback=traceback.format_exc())
            if attempt < 3:
                time.sleep(20 * attempt)
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--sheet", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--seed", type=int, required=True)
    args = parser.parse_args()
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    sheet = output / f"{args.character}_turnaround.jpg"
    shutil.copy2(pathlib.Path(args.sheet), sheet)
    views = crop_views(sheet, output)
    report = generate(args.character, views, output, args.seed)
    (output / "generation-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
