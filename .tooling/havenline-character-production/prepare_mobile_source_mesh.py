#!/usr/bin/env python3
"""Reduce a sanitized static character source to a mobile-safe production face budget.

The production validator counts split glTF vertices, which can be roughly three times the
triangle count on textured neural meshes. This pass therefore targets source polygons,
not Blender's welded vertex count. Large mesh objects are decimated proportionally to a
34,000-face total while small detached facial/accessory objects are preserved untouched.
The source remains above the approved-detail floor, retains UV/material data, and is
exported for a fresh rig/LOD pass. The report never marks visual approval.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import traceback

import bpy


TARGET_FACES = 34000
MAX_FACES = 39000
MIN_FACES = 14000
PRESERVE_OBJECT_FACE_THRESHOLD = 500


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


def apply_decimate(obj, ratio: float) -> None:
    ratio = max(0.05, min(1.0, float(ratio)))
    if ratio >= 0.995:
        return
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    modifier = obj.modifiers.new(name="HAVENLINE_MobileBaseDecimate", type="DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.ratio = ratio
    modifier.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    obj.select_set(False)


def face_counts(meshes):
    return {obj.name: len(obj.data.polygons) for obj in meshes}


def decimate_to_budget(meshes):
    before = face_counts(meshes)
    total_before = sum(before.values())
    preserved = [
        obj for obj in meshes if len(obj.data.polygons) <= PRESERVE_OBJECT_FACE_THRESHOLD
    ]
    reducible = [obj for obj in meshes if obj not in preserved]
    preserved_faces = sum(len(obj.data.polygons) for obj in preserved)
    reducible_faces = sum(len(obj.data.polygons) for obj in reducible)
    if not reducible:
        raise RuntimeError("No reducible character mesh objects were found")

    target_reducible_faces = max(TARGET_FACES - preserved_faces, MIN_FACES)
    first_ratio = min(1.0, target_reducible_faces / max(reducible_faces, 1))
    for obj in reducible:
        apply_decimate(obj, first_ratio)

    after_first = face_counts(meshes)
    total_after_first = sum(after_first.values())
    if total_after_first > MAX_FACES:
        current_reducible = sum(len(obj.data.polygons) for obj in reducible)
        second_target = max(TARGET_FACES - preserved_faces, MIN_FACES)
        second_ratio = min(1.0, second_target / max(current_reducible, 1))
        for obj in reducible:
            apply_decimate(obj, second_ratio)
    else:
        second_ratio = 1.0

    after = face_counts(meshes)
    total_after = sum(after.values())
    if total_after > MAX_FACES:
        raise RuntimeError(
            f"Mobile source remains above face ceiling after two passes: {total_after}"
        )
    if total_after < MIN_FACES:
        raise RuntimeError(
            f"Mobile source was over-decimated below the approved-detail floor: {total_after}"
        )
    if total_before > MAX_FACES and total_after >= total_before * 0.90:
        raise RuntimeError(
            f"Mobile source was not materially reduced: before={total_before}, after={total_after}"
        )

    return {
        "targetFaces": TARGET_FACES,
        "maximumFaces": MAX_FACES,
        "minimumFaces": MIN_FACES,
        "preserveObjectFaceThreshold": PRESERVE_OBJECT_FACE_THRESHOLD,
        "facesBefore": before,
        "facesAfter": after,
        "totalFacesBefore": total_before,
        "totalFacesAfter": total_after,
        "preservedObjects": [obj.name for obj in preserved],
        "reducibleObjects": [obj.name for obj in reducible],
        "firstPassRatio": first_ratio,
        "secondPassRatio": second_ratio,
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
        raise RuntimeError(f"No mobile source GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_mobile_source.glb"
    report_path = root / "mobile-source-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "humanVisualApprovalRequired": True,
        "approved": False,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        reduction = decimate_to_budget(meshes)
        export_glb(output, meshes)
        report.update(success=True, reduction=reduction, outputBytes=output.stat().st_size)
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
