#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
from typing import Any

from PIL import Image, ImageDraw, ImageOps

CHARACTERS = ("Character1", "Character2", "Character3", "Character4")
VIEWS = ("front", "three-quarter", "side", "back")


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: pathlib.Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"Expected JSON object in {path}")
    return payload


def fit(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    contained = ImageOps.contain(image.convert("RGB"), size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", size, (238, 242, 247))
    canvas.paste(
        contained,
        ((size[0] - contained.width) // 2, (size[1] - contained.height) // 2),
    )
    return canvas


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build checksum-pinned approved-reference versus Unity render sheets."
    )
    parser.add_argument("--review-directory", required=True)
    parser.add_argument("--source-evidence", required=True)
    args = parser.parse_args()

    review = pathlib.Path(args.review_directory)
    evidence = pathlib.Path(args.source_evidence)
    report_path = review / "unity-character-review-report.json"
    source_set_path = evidence / "source-artifact-set.json"
    if not report_path.is_file():
        raise FileNotFoundError(report_path)
    if not source_set_path.is_file():
        raise FileNotFoundError(source_set_path)

    report = load_json(report_path)
    source_set = load_json(source_set_path)
    if report.get("allMachineEvidenceComplete") is not True:
        raise RuntimeError("Unity review report has incomplete machine evidence")
    if report.get("humanVisualApprovalRequired") is not True:
        raise RuntimeError("Unity review report attempted to bypass human review")
    if report.get("approved") is not False:
        raise RuntimeError("Unity review report was prematurely marked approved")
    if str(report.get("humanVisualReviewStatus", "")).lower() != "pending":
        raise RuntimeError("Unity review report must remain pending before manual inspection")
    if source_set.get("humanVisualApprovalRequired") is not True:
        raise RuntimeError("Source artifact set attempted to bypass human review")
    if source_set.get("approved") is not False or source_set.get("unityIntegrated") is not False:
        raise RuntimeError("Source artifact set was prematurely promoted")

    report_characters = {
        item.get("character"): item
        for item in report.get("characters", [])
        if isinstance(item, dict)
    }
    source_characters = {
        item.get("character"): item
        for item in source_set.get("characters", [])
        if isinstance(item, dict)
    }
    if sorted(report_characters) != list(CHARACTERS):
        raise RuntimeError("Unity review report must contain Character1 through Character4 exactly")
    if sorted(source_characters) != list(CHARACTERS):
        raise RuntimeError("Source artifact set must contain Character1 through Character4 exactly")

    comparison_directory = review / "Comparisons"
    comparison_directory.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, Any] = {
        "schemaVersion": 4,
        "sourceSetSha256": source_set.get("sourceSetSha256"),
        "unityReviewReportSha256": sha256(report_path),
        "humanVisualApprovalRequired": True,
        "humanVisualReviewStatus": "pending",
        "approved": False,
        "characters": [],
    }

    for character in CHARACTERS:
        source = source_characters[character]
        unity = report_characters[character]
        if source.get("machinePassed") is not True:
            raise RuntimeError(f"{character} source machine evidence is incomplete")
        if source.get("acceptedForUnityReview") is not True:
            raise RuntimeError(f"{character} source artifact was not accepted for Unity review")
        if source.get("humanVisualApprovalRequired") is not True:
            raise RuntimeError(f"{character} source artifact bypassed human visual approval")
        if source.get("approved") is not False or source.get("unityIntegrated") is not False:
            raise RuntimeError(f"{character} source artifact was prematurely promoted")

        if unity.get("machineEvidenceComplete") is not True:
            raise RuntimeError(f"{character} Unity machine evidence is incomplete")
        if unity.get("approved") is not False:
            raise RuntimeError(f"{character} Unity evidence was prematurely approved")
        if str(unity.get("humanVisualReviewStatus", "")).lower() != "pending":
            raise RuntimeError(f"{character} Unity evidence must remain pending")

        expected_model_path = (
            f"Assets/Havenline/Art/Characters/Production/"
            f"{character}/{character}_production.fbx"
        )
        if source.get("productionFbxPath") != expected_model_path:
            raise RuntimeError(f"{character} source FBX path is not canonical")
        if unity.get("modelAssetPath") != expected_model_path:
            raise RuntimeError(f"{character} Unity review imported a different FBX path")
        source_fbx_hash = source.get("productionFbxSha256")
        unity_fbx_hash = unity.get("modelAssetSha256")
        if not isinstance(source_fbx_hash, str) or len(source_fbx_hash) != 64:
            raise RuntimeError(f"{character} source FBX has no valid SHA-256")
        if unity_fbx_hash != source_fbx_hash:
            raise RuntimeError(
                f"{character} Unity imported FBX hash does not match the staged source artifact"
            )

        reference = evidence / character / "approved_reference_sheet.jpg"
        if not reference.is_file():
            raise FileNotFoundError(reference)
        expected_reference_hash = source.get("approvedReferenceSha256")
        actual_reference_hash = sha256(reference)
        if actual_reference_hash != expected_reference_hash:
            raise RuntimeError(f"{character} approved reference hash changed before comparison")

        unity_images: list[Image.Image] = []
        unity_files: list[str] = []
        unity_hashes: list[str] = []
        for view in VIEWS:
            image_path = review / f"HAVENLINE-{character}-unity-{view}.png"
            if not image_path.is_file() or image_path.stat().st_size < 5_000:
                raise RuntimeError(f"Missing or implausibly small Unity render: {image_path}")
            unity_images.append(fit(Image.open(image_path), (640, 640)))
            unity_files.append(image_path.name)
            unity_hashes.append(sha256(image_path))

        report_hashes = unity.get("renderSha256") or []
        if unity_hashes != report_hashes:
            raise RuntimeError(f"{character} Unity render hashes do not match the review report")
        if len(set(unity_hashes)) != 4:
            raise RuntimeError(f"{character} Unity review views are not four distinct images")

        unity_grid = Image.new("RGB", (1280, 1280), (238, 242, 247))
        unity_grid.paste(unity_images[0], (0, 0))
        unity_grid.paste(unity_images[1], (640, 0))
        unity_grid.paste(unity_images[2], (0, 640))
        unity_grid.paste(unity_images[3], (640, 640))
        reference_panel = fit(Image.open(reference), (1280, 1280))

        canvas = Image.new("RGB", (2560, 1400), (22, 28, 38))
        canvas.paste(reference_panel, (0, 120))
        canvas.paste(unity_grid, (1280, 120))
        draw = ImageDraw.Draw(canvas)
        draw.text((32, 34), f"{character} — EXACT APPROVED TURNAROUND", fill=(245, 247, 250))
        draw.text((1312, 34), f"{character} — ACTUAL UNITY URP RENDER", fill=(245, 247, 250))
        draw.text(
            (32, 88),
            "Left: reference copied from the checksum-pinned source artifact",
            fill=(178, 190, 207),
        )
        draw.text(
            (1312, 88),
            "Right: front / three-quarter / side / back from the authored landscape scene",
            fill=(178, 190, 207),
        )
        output = comparison_directory / f"HAVENLINE-{character}-approved-vs-unity.png"
        canvas.save(output, optimize=True)

        manifest["characters"].append(
            {
                "character": character,
                "artifactId": source.get("artifactId"),
                "artifactDigest": source.get("artifactDigest"),
                "artifactPolicy": source.get("artifactPolicy"),
                "productionFbxPath": expected_model_path,
                "productionFbxSha256": source_fbx_hash,
                "approvedReferenceSha256": actual_reference_hash,
                "unityModelAssetSha256": unity_fbx_hash,
                "unityRenderFiles": unity_files,
                "unityRenderSha256": unity_hashes,
                "comparisonFile": str(output.relative_to(review).as_posix()),
                "comparisonSha256": sha256(output),
                "machineEvidenceComplete": True,
                "humanVisualReviewStatus": "pending",
                "approved": False,
            }
        )

    manifest_path = review / "comparison-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
