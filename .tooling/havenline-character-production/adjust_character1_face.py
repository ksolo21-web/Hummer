#!/usr/bin/env python3
"""Move Character 1's modeled face repair onto the measured reconstructed eye line.

The first modeled-glasses pass proved the geometry survives rigging and Unity export, but
its proportional eye-line estimate was too low for Character 1's unusually tall forehead.
This deterministic correction raises every authored face layer by 5.25 percent of source
height, widens the eye centres, and enlarges the glasses around their own centre without
changing the reconstructed body, outfit, head or texture.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


def cli_args() -> list[str]:
    return sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(cli_args())


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def import_glb(path: pathlib.Path):
    bpy.ops.import_scene.gltf(filepath=str(path))
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError(f"No mesh objects imported from {path}")
    return meshes


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def object_center(obj):
    if not obj.data.vertices:
        raise RuntimeError(f"Face detail {obj.name} contains no vertices")
    total = Vector((0.0, 0.0, 0.0))
    for vertex in obj.data.vertices:
        total += vertex.co
    return total / len(obj.data.vertices)


def is_face_detail(obj, character: str) -> bool:
    if obj.type != "MESH":
        return False
    if obj.get("havenlineCharacter1FaceDetail") is True:
        return True
    expected = (
        f"{character}_SocketPatch_",
        f"{character}_EyeLid_",
        f"{character}_Sclera_",
        f"{character}_Iris_",
        f"{character}_Pupil_",
        f"{character}_Brow_",
        f"{character}_ApprovedGlasses",
    )
    return obj.name.startswith(expected)


def adjust_details(meshes, character: str):
    minimum, maximum = world_bounds(meshes)
    height = max(maximum.z - minimum.z, 1e-6)
    center_x = (minimum.x + maximum.x) * 0.5 + height * 0.0040
    delta_z = height * 0.0525
    eye_separation_scale = 1.45
    glasses_x_scale = 1.25
    glasses_z_scale = 1.28

    details = [obj for obj in meshes if is_face_detail(obj, character)]
    if len(details) < 13:
        raise RuntimeError(
            f"Expected at least 13 authored Character 1 face objects, found {len(details)}"
        )
    glasses = [obj for obj in details if "ApprovedGlasses" in obj.name]
    if len(glasses) != 1:
        raise RuntimeError(f"Expected exactly one modeled glasses object, found {len(glasses)}")

    reports = []
    for obj in details:
        before = object_center(obj)
        if obj is glasses[0]:
            old_eye_z = before.z
            for vertex in obj.data.vertices:
                vertex.co.x = center_x + (vertex.co.x - center_x) * glasses_x_scale
                vertex.co.z = old_eye_z + delta_z + (vertex.co.z - old_eye_z) * glasses_z_scale
            method = "raised and scaled around glasses centre"
        else:
            target_x = center_x + (before.x - center_x) * eye_separation_scale
            shift_x = target_x - before.x
            for vertex in obj.data.vertices:
                vertex.co.x += shift_x
                vertex.co.z += delta_z
            method = "raised and moved outward without resizing"
        obj.data.update(calc_edges=False)
        after = object_center(obj)
        reports.append(
            {
                "object": obj.name,
                "vertices": len(obj.data.vertices),
                "centerBefore": list(before),
                "centerAfter": list(after),
                "method": method,
            }
        )

    after_minimum, after_maximum = world_bounds(meshes)
    return {
        "sourceBounds": {"minimum": list(minimum), "maximum": list(maximum)},
        "outputBounds": {"minimum": list(after_minimum), "maximum": list(after_maximum)},
        "sourceHeight": height,
        "faceCenterX": center_x,
        "raiseDistance": delta_z,
        "eyeSeparationScale": eye_separation_scale,
        "glassesXScale": glasses_x_scale,
        "glassesZScale": glasses_z_scale,
        "objects": reports,
    }


def export_glb(path: pathlib.Path, meshes) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.export_scene.gltf(
        filepath=str(path),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
    )
    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError(f"No adjusted Character 1 GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_face_adjusted.glb"
    report_path = root / "character1-face-adjustment-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        adjustment = adjust_details(meshes, args.character)
        export_glb(output, meshes)
        report.update(success=True, adjustment=adjustment, outputBytes=output.stat().st_size)
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
