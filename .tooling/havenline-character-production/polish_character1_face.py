#!/usr/bin/env python3
"""Polish Character 1's modeled face repair after measured eye-line correction.

The corrected geometry now sits on the real eye line, but the first proof showed oversized
visible sclera and vertically tall frames. This pass preserves placement while shrinking
the authored eye layers, flattening and slightly widening the glasses, and retaining the
larger face-coloured socket patches that hide the failed reconstruction underneath.
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


def object_center(obj):
    if not obj.data.vertices:
        raise RuntimeError(f"{obj.name} has no vertices")
    center = Vector((0.0, 0.0, 0.0))
    for vertex in obj.data.vertices:
        center += vertex.co
    return center / len(obj.data.vertices)


def scale_object_geometry(obj, scale_x: float, scale_z: float, shift_z: float = 0.0):
    center = object_center(obj)
    for vertex in obj.data.vertices:
        vertex.co.x = center.x + (vertex.co.x - center.x) * scale_x
        vertex.co.z = center.z + shift_z + (vertex.co.z - center.z) * scale_z
    obj.data.update(calc_edges=False)
    return center, object_center(obj)


def detail_kind(name: str):
    for value in (
        "SocketPatch",
        "EyeLid",
        "Sclera",
        "Iris",
        "Pupil",
        "Brow",
        "ApprovedGlasses",
    ):
        if value in name:
            return value
    return None


def polish(meshes, character: str):
    detail_objects = [
        obj
        for obj in meshes
        if obj.name.startswith(f"{character}_") and detail_kind(obj.name) is not None
    ]
    if len(detail_objects) < 13:
        raise RuntimeError(f"Expected at least 13 Character 1 face details, found {len(detail_objects)}")

    settings = {
        "SocketPatch": (1.00, 1.00, 0.0),
        "EyeLid": (0.86, 0.66, -0.0006),
        "Sclera": (0.66, 0.46, -0.0006),
        "Iris": (0.66, 0.64, -0.0006),
        "Pupil": (0.58, 0.58, -0.0006),
        "Brow": (1.02, 0.62, 0.0012),
        "ApprovedGlasses": (1.07, 0.80, -0.0010),
    }
    reports = []
    for obj in detail_objects:
        kind = detail_kind(obj.name)
        scale_x, scale_z, shift_z = settings[kind]
        before, after = scale_object_geometry(obj, scale_x, scale_z, shift_z)
        reports.append(
            {
                "object": obj.name,
                "kind": kind,
                "vertices": len(obj.data.vertices),
                "scaleX": scale_x,
                "scaleZ": scale_z,
                "shiftZ": shift_z,
                "centerBefore": list(before),
                "centerAfter": list(after),
            }
        )
    return reports


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
        raise RuntimeError(f"No polished Character 1 GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_face_polished.glb"
    report_path = root / "character1-face-polish-report.json"
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
        objects = polish(meshes, args.character)
        export_glb(output, meshes)
        report.update(success=True, objects=objects, outputBytes=output.stat().st_size)
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
