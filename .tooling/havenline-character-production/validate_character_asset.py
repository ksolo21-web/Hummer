#!/usr/bin/env python3
import argparse
import json
import pathlib
import sys

import trimesh
from pygltflib import GLTF2
from PIL import Image

REQUIRED_CLIPS = {"idle", "walk", "run", "gather", "carry", "deposit", "warm", "build"}


def inspect_glb(path: pathlib.Path) -> dict:
    scene = trimesh.load(path, force="scene")
    geometries = list(scene.geometry.values())
    return {
        "bytes": path.stat().st_size,
        "geometryCount": len(geometries),
        "vertices": sum(len(item.vertices) for item in geometries),
        "faces": sum(len(item.faces) for item in geometries),
        "bounds": scene.bounds.tolist() if scene.bounds is not None else None,
    }


def inspect_gltf(path: pathlib.Path) -> dict:
    gltf = GLTF2().load_binary(str(path))
    animations = [animation.name or "" for animation in (gltf.animations or [])]
    skins = len(gltf.skins or [])
    images = len(gltf.images or [])
    materials = len(gltf.materials or [])
    return {"animations": animations, "skins": skins, "images": images, "materials": materials}


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
    for required in (base, lod1, lod2, fbx):
        if not required.is_file() or required.stat().st_size == 0:
            failures.append(f"Missing non-empty output: {required.name}")
    metrics = {}
    if base.is_file() and base.stat().st_size:
        try:
            metrics["mesh"] = inspect_glb(base)
            metrics["gltf"] = inspect_gltf(base)
        except Exception as exception:
            failures.append(f"Base GLB could not be inspected: {exception}")
    mesh = metrics.get("mesh", {})
    vertices = int(mesh.get("vertices", 0))
    if vertices < 6000:
        failures.append(f"Base character has only {vertices} vertices; approved-detail floor is 6000")
    if vertices > 120000:
        failures.append(f"Base character has {vertices} vertices; mobile ceiling is 120000")
    gltf = metrics.get("gltf", {})
    if int(gltf.get("skins", 0)) < 1:
        failures.append("No skinned humanoid rig was found")
    if int(gltf.get("materials", 0)) < 4:
        failures.append("Fewer than four materials were found")
    clip_names = {name.lower() for name in gltf.get("animations", [])}
    for token in sorted(REQUIRED_CLIPS):
        if not any(token in name for name in clip_names):
            failures.append(f"Required animation clip is missing: {token}")
    proof_names = ("front", "three-quarter", "side", "back")
    proof = {}
    for name in proof_names:
        path = root / f"proof_{name}.png"
        if not path.is_file():
            failures.append(f"Missing rendered proof: {path.name}")
            continue
        try:
            image = Image.open(path)
            proof[name] = {"width": image.width, "height": image.height, "bytes": path.stat().st_size}
            if image.width < 900 or image.height < 900:
                failures.append(f"Rendered proof is too small: {path.name}")
        except Exception as exception:
            failures.append(f"Rendered proof is unreadable: {path.name}: {exception}")
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "passed": not failures,
        "metrics": metrics,
        "proof": proof,
        "failures": failures,
        "humanVisualApprovalRequired": True,
        "unityIntegrationApproved": False,
    }
    (root / "validation-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
