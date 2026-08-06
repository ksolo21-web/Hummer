#!/usr/bin/env python3
import argparse
import json
import pathlib

import trimesh
from pygltflib import GLTF2
from PIL import Image, ImageStat

REQUIRED_CLIPS = {"idle", "walk", "run", "gather", "carry", "deposit", "warm", "build"}


def inspect_glb(path: pathlib.Path) -> dict:
    scene = trimesh.load(path, force="scene")
    geometries = list(scene.geometry.values())
    bounds = scene.bounds.tolist() if scene.bounds is not None else None
    height = float(bounds[1][2] - bounds[0][2]) if bounds else 0.0
    return {
        "bytes": path.stat().st_size,
        "geometryCount": len(geometries),
        "vertices": sum(len(item.vertices) for item in geometries),
        "faces": sum(len(item.faces) for item in geometries),
        "bounds": bounds,
        "height": height,
    }


def inspect_gltf(path: pathlib.Path) -> dict:
    gltf = GLTF2().load_binary(str(path))
    animations = [animation.name or "" for animation in (gltf.animations or [])]
    return {
        "animations": animations,
        "skins": len(gltf.skins or []),
        "images": len(gltf.images or []),
        "textures": len(gltf.textures or []),
        "materials": len(gltf.materials or []),
        "nodes": len(gltf.nodes or []),
    }


def inspect_proof(path: pathlib.Path) -> dict:
    with Image.open(path) as image:
        rgb = image.convert("RGB")
        resized = rgb.resize((128, 128))
        stat = ImageStat.Stat(resized)
        standard_deviation = sum(stat.stddev) / len(stat.stddev)
        extrema = resized.getextrema()
        dynamic_range = sum(high - low for low, high in extrema) / len(extrema)
        return {
            "width": image.width,
            "height": image.height,
            "bytes": path.stat().st_size,
            "standardDeviation": standard_deviation,
            "dynamicRange": dynamic_range,
        }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    root = pathlib.Path(args.directory)
    failures = []

    base = root / f"{args.character}_production.glb"
    lod1 = root / f"{args.character}_LOD1.glb"
    lod2 = root / f"{args.character}_LOD2.glb"
    fbx = root / f"{args.character}_production.fbx"
    rig_report_path = root / "rig-report.json"

    required_outputs = (base, lod1, lod2, fbx)
    for required in required_outputs:
        if not required.is_file() or required.stat().st_size == 0:
            failures.append(f"Missing non-empty output: {required.name}")

    rig_report = {}
    if not rig_report_path.is_file():
        failures.append("Missing rig-report.json")
    else:
        try:
            rig_report = json.loads(rig_report_path.read_text(encoding="utf-8"))
            if rig_report.get("success") is not True:
                failures.append(f"Rig report did not pass: {rig_report.get('error', 'unknown error')}")
            if int(rig_report.get("weightedVertices", 0)) <= 0:
                failures.append("Rig report contains no deterministically weighted vertices")
            reported_clips = {str(name).lower() for name in rig_report.get("animations", [])}
            missing_reported = REQUIRED_CLIPS - reported_clips
            if missing_reported:
                failures.append(
                    "Rig report is missing authored clips: " + ", ".join(sorted(missing_reported))
                )
        except Exception as exception:
            failures.append(f"Rig report could not be read: {exception}")

    metrics = {}
    if base.is_file() and base.stat().st_size:
        try:
            metrics["baseMesh"] = inspect_glb(base)
            metrics["baseGltf"] = inspect_gltf(base)
        except Exception as exception:
            failures.append(f"Base GLB could not be inspected: {exception}")
    if lod1.is_file() and lod1.stat().st_size:
        try:
            metrics["lod1Mesh"] = inspect_glb(lod1)
            metrics["lod1Gltf"] = inspect_gltf(lod1)
        except Exception as exception:
            failures.append(f"LOD1 GLB could not be inspected: {exception}")
    if lod2.is_file() and lod2.stat().st_size:
        try:
            metrics["lod2Mesh"] = inspect_glb(lod2)
            metrics["lod2Gltf"] = inspect_gltf(lod2)
        except Exception as exception:
            failures.append(f"LOD2 GLB could not be inspected: {exception}")

    base_mesh = metrics.get("baseMesh", {})
    vertices = int(base_mesh.get("vertices", 0))
    if vertices < 6000:
        failures.append(f"Base character has only {vertices} vertices; approved-detail floor is 6000")
    if vertices > 120000:
        failures.append(f"Base character has {vertices} vertices; mobile ceiling is 120000")
    height = float(base_mesh.get("height", 0.0))
    if height and not 1.45 <= height <= 1.95:
        failures.append(f"Base character height is {height:.3f}m; expected normalized mobile range is 1.45–1.95m")

    base_gltf = metrics.get("baseGltf", {})
    if int(base_gltf.get("skins", 0)) < 1:
        failures.append("No skinned humanoid rig was found")
    if int(base_gltf.get("materials", 0)) < 1:
        failures.append("No material was found on the production character")
    if int(base_gltf.get("images", 0)) < 1 and int(base_gltf.get("textures", 0)) < 1:
        failures.append("No embedded character texture was found")

    clip_names = {name.lower() for name in base_gltf.get("animations", [])}
    for token in sorted(REQUIRED_CLIPS):
        if not any(token in name for name in clip_names):
            failures.append(f"Required animation clip is missing: {token}")

    lod1_vertices = int(metrics.get("lod1Mesh", {}).get("vertices", 0))
    lod2_vertices = int(metrics.get("lod2Mesh", {}).get("vertices", 0))
    if vertices and lod1_vertices >= vertices * 0.92:
        failures.append(
            f"LOD1 is not materially reduced: base={vertices}, lod1={lod1_vertices}"
        )
    if lod1_vertices and lod2_vertices >= lod1_vertices * 0.82:
        failures.append(
            f"LOD2 is not materially reduced: lod1={lod1_vertices}, lod2={lod2_vertices}"
        )
    if lod2_vertices and lod2_vertices < 500:
        failures.append(f"LOD2 is over-decimated at only {lod2_vertices} vertices")
    if fbx.is_file() and fbx.stat().st_size < 10000:
        failures.append(f"Production FBX is implausibly small: {fbx.stat().st_size} bytes")

    proof_names = ("front", "three-quarter", "side", "back")
    proof = {}
    for name in proof_names:
        path = root / f"proof_{name}.png"
        if not path.is_file():
            failures.append(f"Missing rendered proof: {path.name}")
            continue
        try:
            proof[name] = inspect_proof(path)
            if proof[name]["width"] < 900 or proof[name]["height"] < 900:
                failures.append(f"Rendered proof is too small: {path.name}")
            if proof[name]["standardDeviation"] < 5.0 or proof[name]["dynamicRange"] < 25.0:
                failures.append(f"Rendered proof appears blank or visually empty: {path.name}")
        except Exception as exception:
            failures.append(f"Rendered proof is unreadable: {path.name}: {exception}")

    report = {
        "schemaVersion": 2,
        "character": args.character,
        "passed": not failures,
        "metrics": metrics,
        "rigReport": rig_report,
        "proof": proof,
        "failures": failures,
        "humanVisualApprovalRequired": True,
        "unityIntegrationApproved": False,
    }
    (root / "validation-report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
