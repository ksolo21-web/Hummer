#!/usr/bin/env python3
"""Remove large disconnected reconstruction islands outside the character envelope."""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import traceback

import bmesh
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


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def import_glb(path):
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


def components(mesh):
    adjacency = [[] for _ in mesh.vertices]
    for edge in mesh.edges:
        a, b = edge.vertices
        adjacency[a].append(b)
        adjacency[b].append(a)
    visited = set()
    result = []
    for start in range(len(mesh.vertices)):
        if start in visited:
            continue
        stack = [start]
        visited.add(start)
        group = []
        while stack:
            current = stack.pop()
            group.append(current)
            for neighbor in adjacency[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    stack.append(neighbor)
        result.append(group)
    return result


def component_metrics(obj, indices):
    points = [obj.matrix_world @ obj.data.vertices[index].co for index in indices]
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    extent = maximum - minimum
    return {
        "vertices": len(indices),
        "minimum": minimum,
        "maximum": maximum,
        "extents": extent,
        "largestExtent": max(extent.x, extent.y, extent.z),
        "center": (minimum + maximum) * 0.5,
    }


def axis_gap(a_min, a_max, b_min, b_max):
    if a_max < b_min:
        return b_min - a_max
    if b_max < a_min:
        return a_min - b_max
    return 0.0


def should_remove(metric, main, span):
    gap_x = axis_gap(metric["minimum"].x, metric["maximum"].x, main["minimum"].x, main["maximum"].x)
    gap_y = axis_gap(metric["minimum"].y, metric["maximum"].y, main["minimum"].y, main["maximum"].y)
    gap_z = axis_gap(metric["minimum"].z, metric["maximum"].z, main["minimum"].z, main["maximum"].z)
    horizontal_gap = math.hypot(gap_x, gap_y)
    total_gap = math.sqrt(gap_x * gap_x + gap_y * gap_y + gap_z * gap_z)
    vertex_ratio = metric["vertices"] / max(main["vertices"], 1)

    detached_large_island = (
        horizontal_gap > span * 0.025
        and vertex_ratio < 0.48
        and metric["largestExtent"] > span * 0.10
    )
    detached_small_island = total_gap > span * 0.055 and vertex_ratio < 0.18
    return detached_large_island or detached_small_island, {
        "gapX": gap_x,
        "gapY": gap_y,
        "gapZ": gap_z,
        "horizontalGap": horizontal_gap,
        "totalGap": total_gap,
        "vertexRatioToMain": vertex_ratio,
    }


def clean_object(obj, span):
    mesh = obj.data
    groups = components(mesh)
    metrics = [component_metrics(obj, group) for group in groups]
    if not metrics:
        return {"object": obj.name, "componentsBefore": 0, "componentsRemoved": 0, "verticesRemoved": 0}
    main_index = max(range(len(metrics)), key=lambda index: metrics[index]["vertices"])
    main = metrics[main_index]
    remove_indices = set()
    removed = []
    kept = []
    for index, (group, metric) in enumerate(zip(groups, metrics)):
        payload = {
            "componentIndex": index,
            "vertices": metric["vertices"],
            "minimum": list(metric["minimum"]),
            "maximum": list(metric["maximum"]),
            "extents": list(metric["extents"]),
            "largestExtent": metric["largestExtent"],
            "isMain": index == main_index,
        }
        if index == main_index:
            kept.append(payload)
            continue
        remove, gaps = should_remove(metric, main, span)
        payload.update(gaps)
        if remove:
            payload["reason"] = "spatially detached reconstruction island outside main character envelope"
            removed.append(payload)
            remove_indices.update(group)
        else:
            kept.append(payload)

    if remove_indices:
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bm.verts.ensure_lookup_table()
        delete_verts = [bm.verts[index] for index in sorted(remove_indices)]
        bmesh.ops.delete(bm, geom=delete_verts, context="VERTS")
        if bm.faces:
            bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        bm.to_mesh(mesh)
        bm.free()
        mesh.validate(verbose=False)
        mesh.update()

    return {
        "object": obj.name,
        "componentsBefore": len(groups),
        "mainComponentIndex": main_index,
        "componentsRemoved": len(removed),
        "verticesRemoved": len(remove_indices),
        "removed": removed,
        "kept": kept,
    }


def export_glb(path, meshes):
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
        raise RuntimeError(f"No cleaned GLB was exported to {path}")


def main():
    args = parse_args()
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    output_path = output_root / f"{args.character}_spatial.glb"
    report_path = output_root / "spatial-outlier-report.json"
    report = {
        "schemaVersion": 1,
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
        span = max(extent.x, extent.y, extent.z, 1e-6)
        object_reports = [clean_object(obj, span) for obj in meshes]
        export_glb(output_path, meshes)
        after_minimum, after_maximum = world_bounds(meshes)
        report.update({
            "success": True,
            "sceneSpan": span,
            "inputBounds": {"minimum": list(minimum), "maximum": list(maximum)},
            "outputBounds": {"minimum": list(after_minimum), "maximum": list(after_maximum)},
            "objects": object_reports,
            "componentsRemoved": sum(item["componentsRemoved"] for item in object_reports),
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
