#!/usr/bin/env python3
"""Remove spatially detached reconstruction islands from triangle-soup meshes.

Some neural GLB exports duplicate vertices per triangle, so ordinary connected-component
analysis reports thousands of three-vertex components. This pass instead detects a large
empty horizontal gap that isolates a minority vertex cluster from the character body,
then deletes only that outlying cluster. Deletion is performed through Blender edit mode
to preserve the surviving mesh's imported UVs, materials, and custom split normals.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


def args_after_separator() -> list[str]:
    values = sys.argv
    return values[values.index("--") + 1 :] if "--" in values else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(args_after_separator())


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
        raise RuntimeError("No mesh bounds available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def find_horizontal_outlier_split(obj, scene_span: float):
    samples = sorted(
        (float((obj.matrix_world @ vertex.co).x), vertex.index)
        for vertex in obj.data.vertices
    )
    count = len(samples)
    if count < 100:
        return None

    candidates = []
    minimum_gap = scene_span * 0.020
    for index in range(count - 1):
        left_x = samples[index][0]
        right_x = samples[index + 1][0]
        gap = right_x - left_x
        if gap < minimum_gap:
            continue
        left_ratio = (index + 1) / count
        right_ratio = 1.0 - left_ratio
        if 0.03 <= left_ratio <= 0.30:
            candidates.append({
                "side": "left",
                "gap": gap,
                "threshold": (left_x + right_x) * 0.5,
                "outlierRatio": left_ratio,
                "bodyRatio": right_ratio,
                "leftEdge": left_x,
                "rightEdge": right_x,
            })
        if 0.03 <= right_ratio <= 0.30:
            candidates.append({
                "side": "right",
                "gap": gap,
                "threshold": (left_x + right_x) * 0.5,
                "outlierRatio": right_ratio,
                "bodyRatio": left_ratio,
                "leftEdge": left_x,
                "rightEdge": right_x,
            })

    if not candidates:
        return None
    return max(candidates, key=lambda item: (item["gap"], -item["outlierRatio"]))


def delete_selected_vertices_preserving_attributes(obj, threshold: float, side: str) -> int:
    bpy.ops.object.mode_set(mode="OBJECT") if bpy.context.object and bpy.context.object.mode != "OBJECT" else None
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    selected = 0
    for vertex in obj.data.vertices:
        world_x = float((obj.matrix_world @ vertex.co).x)
        remove = (side == "left" and world_x < threshold) or (side == "right" and world_x > threshold)
        vertex.select = remove
        if remove:
            selected += 1
    if selected == 0:
        return 0

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    return selected


def clean_object(obj, scene_span: float):
    split = find_horizontal_outlier_split(obj, scene_span)
    vertices_before = len(obj.data.vertices)
    if split is None:
        return {
            "object": obj.name,
            "verticesBefore": vertices_before,
            "verticesAfter": vertices_before,
            "verticesRemoved": 0,
            "splitDetected": False,
            "reason": "no unambiguous minority cluster separated by a large horizontal gap",
        }

    removed = delete_selected_vertices_preserving_attributes(
        obj,
        float(split["threshold"]),
        str(split["side"]),
    )
    actual_after = len(obj.data.vertices)
    actual_removed = vertices_before - actual_after
    if removed <= 0 or actual_removed <= 0:
        raise RuntimeError(f"A spatial split was detected for {obj.name}, but no vertices were removed")
    actual_ratio = actual_removed / max(vertices_before, 1)
    if actual_ratio > 0.35:
        raise RuntimeError(
            f"Spatial cleanup attempted to remove too much geometry from {obj.name}: {actual_ratio:.3f}"
        )

    return {
        "object": obj.name,
        "verticesBefore": vertices_before,
        "verticesAfter": actual_after,
        "verticesSelectedForRemoval": removed,
        "verticesRemoved": actual_removed,
        "removedRatio": actual_ratio,
        "splitDetected": True,
        "split": split,
        "attributePreservationMethod": "Blender edit-mode vertex deletion without normal recalculation",
        "reason": "minority vertex cluster isolated by a large empty horizontal gap",
    }


def export_glb(path: pathlib.Path, meshes) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    surviving = [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
    if not surviving:
        raise RuntimeError("Spatial cleanup removed every mesh")
    for obj in surviving:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = surviving[0]
    bpy.ops.export_scene.gltf(
        filepath=str(path),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
    )
    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError(f"No cleaned GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    output_path = output_root / f"{args.character}_spatial.glb"
    report_path = output_root / "spatial-outlier-report.json"
    report = {
        "schemaVersion": 3,
        "character": args.character,
        "source": args.input,
        "output": str(output_path),
        "success": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        minimum, maximum = world_bounds(meshes)
        extent = maximum - minimum
        scene_span = max(extent.x, extent.y, extent.z, 1e-6)
        object_reports = [clean_object(obj, scene_span) for obj in meshes]
        export_glb(output_path, meshes)
        after_minimum, after_maximum = world_bounds(
            [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
        )
        report.update({
            "success": True,
            "sceneSpan": scene_span,
            "inputBounds": {"minimum": list(minimum), "maximum": list(maximum)},
            "outputBounds": {"minimum": list(after_minimum), "maximum": list(after_maximum)},
            "objects": object_reports,
            "splitsDetected": sum(1 for item in object_reports if item["splitDetected"]),
            "verticesRemoved": sum(item["verticesRemoved"] for item in object_reports),
            "outputBytes": output_path.stat().st_size,
        })
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
