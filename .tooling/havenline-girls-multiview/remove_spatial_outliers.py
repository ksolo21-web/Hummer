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


def selected_vertex_indices(obj, threshold: float, side: str) -> list[int]:
    selected = []
    for vertex in obj.data.vertices:
        world_x = float((obj.matrix_world @ vertex.co).x)
        remove = (
            (side == "left" and world_x < threshold)
            or (side == "right" and world_x > threshold)
        )
        if remove:
            selected.append(vertex.index)
    return selected


def delete_selected_vertices_preserving_attributes(
    obj,
    vertex_indices: list[int],
) -> int:
    if not vertex_indices:
        return 0

    if bpy.context.object and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    # Blender can retain imported per-vertex selection flags. Enter edit mode once and
    # explicitly clear them before applying the prevalidated object-mode index set.
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.select_all(action="DESELECT")
    bpy.ops.object.mode_set(mode="OBJECT")

    for vertex in obj.data.vertices:
        vertex.select = False
    for index in vertex_indices:
        obj.data.vertices[index].select = True

    selected_count = sum(1 for vertex in obj.data.vertices if vertex.select)
    if selected_count != len(vertex_indices):
        raise RuntimeError(
            f"Blender selection synchronization failed for {obj.name}: "
            f"expected {len(vertex_indices)}, selected {selected_count}"
        )

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    return selected_count


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

    indices = selected_vertex_indices(
        obj,
        float(split["threshold"]),
        str(split["side"]),
    )
    selected_ratio = len(indices) / max(vertices_before, 1)
    expected_ratio = float(split["outlierRatio"])
    ratio_error = abs(selected_ratio - expected_ratio)

    # Fail closed before mutating the mesh. This prevents stale imported selection state
    # or a coordinate-space mismatch from deleting the whole character.
    if not indices:
        raise RuntimeError(
            f"A spatial split was detected for {obj.name}, but its selected outlier set is empty"
        )
    if selected_ratio > 0.35:
        raise RuntimeError(
            f"Spatial cleanup rejected an unsafe pre-delete selection for {obj.name}: "
            f"{selected_ratio:.3f}"
        )
    if ratio_error > 0.015:
        raise RuntimeError(
            f"Spatial cleanup selection disagrees with the detected split for {obj.name}: "
            f"selected={selected_ratio:.4f}, expected={expected_ratio:.4f}"
        )

    selected_count = delete_selected_vertices_preserving_attributes(obj, indices)
    actual_after = len(obj.data.vertices)
    actual_removed = vertices_before - actual_after
    actual_ratio = actual_removed / max(vertices_before, 1)
    if selected_count <= 0 or actual_removed <= 0:
        raise RuntimeError(f"A spatial split was detected for {obj.name}, but no vertices were removed")
    if actual_removed != selected_count:
        raise RuntimeError(
            f"Spatial cleanup deletion count mismatch for {obj.name}: "
            f"selected={selected_count}, removed={actual_removed}"
        )
    if actual_ratio > 0.35:
        raise RuntimeError(
            f"Spatial cleanup removed too much geometry from {obj.name}: {actual_ratio:.3f}"
        )

    return {
        "object": obj.name,
        "verticesBefore": vertices_before,
        "verticesAfter": actual_after,
        "verticesSelectedForRemoval": selected_count,
        "verticesRemoved": actual_removed,
        "selectedRatio": selected_ratio,
        "expectedOutlierRatio": expected_ratio,
        "selectionRatioError": ratio_error,
        "removedRatio": actual_ratio,
        "splitDetected": True,
        "split": split,
        "attributePreservationMethod": (
            "prevalidated object-space index selection followed by Blender edit-mode "
            "vertex deletion without normal recalculation"
        ),
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
        "schemaVersion": 4,
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
