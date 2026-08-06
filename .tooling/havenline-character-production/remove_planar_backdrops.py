#!/usr/bin/env python3
"""Remove depth-separated full-body reconstruction backdrops from cleaned character GLBs.

Multi-image TRELLIS can reconstruct a large, paper-thin rectangular plane behind an
otherwise good character. It is not a body component: it spans most of the character's
width and height, occupies only a tiny fraction of scene depth, and is separated from the
body by a real empty Y-axis gap. This pass detects only that unambiguous geometry class,
then deletes its vertices in edit mode while preserving the character, facial accessories,
materials, UVs, normals, and all non-planar components. It fails closed when the geometry
cannot be classified safely.
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
    return sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else []


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


def world_vertices(obj):
    matrix = obj.matrix_world
    for vertex in obj.data.vertices:
        yield vertex.index, matrix @ vertex.co


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def cluster_metrics(points):
    if not points:
        raise RuntimeError("Cannot measure an empty planar candidate")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    extent = maximum - minimum
    return {
        "minimum": list(minimum),
        "maximum": list(maximum),
        "extent": list(extent),
        "width": float(extent.x),
        "depth": float(extent.y),
        "height": float(extent.z),
    }


def detect_planar_depth_cluster(obj, scene_minimum, scene_maximum):
    samples = sorted((float(point.y), index, point) for index, point in world_vertices(obj))
    count = len(samples)
    if count < 1000:
        return None

    scene_extent = scene_maximum - scene_minimum
    scene_span = max(scene_extent.x, scene_extent.y, scene_extent.z, 1e-6)
    candidates = []
    minimum_gap = scene_span * 0.0035

    for split_index in range(count - 1):
        left_y = samples[split_index][0]
        right_y = samples[split_index + 1][0]
        gap = right_y - left_y
        if gap < minimum_gap:
            continue

        left_count = split_index + 1
        right_count = count - left_count
        for side, side_count in (("near-min-y", left_count), ("near-max-y", right_count)):
            ratio = side_count / count
            if not 0.008 <= ratio <= 0.20:
                continue
            selected_samples = (
                samples[:left_count] if side == "near-min-y" else samples[left_count:]
            )
            points = [item[2] for item in selected_samples]
            metrics = cluster_metrics(points)
            width_coverage = metrics["width"] / max(scene_extent.x, 1e-6)
            height_coverage = metrics["height"] / max(scene_extent.z, 1e-6)
            depth_ratio = metrics["depth"] / scene_span
            if width_coverage < 0.55:
                continue
            if height_coverage < 0.70:
                continue
            if depth_ratio > 0.035:
                continue
            threshold = (left_y + right_y) * 0.5
            candidates.append(
                {
                    "side": side,
                    "gap": gap,
                    "threshold": threshold,
                    "selectedRatio": ratio,
                    "selectedCount": side_count,
                    "widthCoverage": width_coverage,
                    "heightCoverage": height_coverage,
                    "depthToSceneSpan": depth_ratio,
                    "metrics": metrics,
                }
            )

    if not candidates:
        return None
    candidates.sort(
        key=lambda item: (
            item["heightCoverage"],
            item["widthCoverage"],
            item["gap"],
            -item["selectedRatio"],
        ),
        reverse=True,
    )
    best = candidates[0]
    if len(candidates) > 1:
        second = candidates[1]
        if (
            abs(best["heightCoverage"] - second["heightCoverage"]) < 0.03
            and abs(best["widthCoverage"] - second["widthCoverage"]) < 0.03
            and abs(best["gap"] - second["gap"]) < scene_span * 0.002
            and best["side"] != second["side"]
        ):
            raise RuntimeError(
                f"Ambiguous planar backdrop candidates on both depth extremes for {obj.name}"
            )
    return best


def selected_indices(obj, candidate):
    threshold = float(candidate["threshold"])
    side = candidate["side"]
    result = []
    for index, point in world_vertices(obj):
        remove = (
            side == "near-min-y" and point.y < threshold
        ) or (
            side == "near-max-y" and point.y > threshold
        )
        if remove:
            result.append(index)
    return result


def delete_vertices(obj, indices):
    if not indices:
        raise RuntimeError(f"Planar candidate for {obj.name} selected no vertices")
    if bpy.context.object and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.select_all(action="DESELECT")
    bpy.ops.object.mode_set(mode="OBJECT")
    for vertex in obj.data.vertices:
        vertex.select = False
    for index in indices:
        obj.data.vertices[index].select = True
    selected = sum(1 for vertex in obj.data.vertices if vertex.select)
    if selected != len(indices):
        raise RuntimeError(
            f"Selection synchronization failed for {obj.name}: expected {len(indices)}, got {selected}"
        )

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    return selected


def clean_object(obj, scene_minimum, scene_maximum):
    before = len(obj.data.vertices)
    candidate = detect_planar_depth_cluster(obj, scene_minimum, scene_maximum)
    if candidate is None:
        return {
            "object": obj.name,
            "verticesBefore": before,
            "verticesAfter": before,
            "verticesRemoved": 0,
            "planarBackdropDetected": False,
            "reason": "no unambiguous depth-separated full-body plane",
        }

    indices = selected_indices(obj, candidate)
    selected_ratio = len(indices) / max(before, 1)
    if abs(selected_ratio - candidate["selectedRatio"]) > 0.01:
        raise RuntimeError(
            f"Planar selection ratio changed unexpectedly for {obj.name}: "
            f"detected={candidate['selectedRatio']:.4f}, selected={selected_ratio:.4f}"
        )
    if selected_ratio > 0.20:
        raise RuntimeError(f"Unsafe planar deletion ratio for {obj.name}: {selected_ratio:.4f}")

    selected = delete_vertices(obj, indices)
    after = len(obj.data.vertices)
    removed = before - after
    if removed != selected or removed <= 0:
        raise RuntimeError(
            f"Planar backdrop deletion mismatch for {obj.name}: selected={selected}, removed={removed}"
        )
    return {
        "object": obj.name,
        "verticesBefore": before,
        "verticesAfter": after,
        "verticesSelectedForRemoval": selected,
        "verticesRemoved": removed,
        "removedRatio": removed / max(before, 1),
        "planarBackdropDetected": True,
        "candidate": candidate,
        "reason": "minority full-body plane isolated from the character along scene depth",
    }


def export_glb(path: pathlib.Path, meshes):
    bpy.ops.object.select_all(action="DESELECT")
    surviving = [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
    if not surviving:
        raise RuntimeError("Planar cleanup removed every mesh")
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
        raise RuntimeError(f"No planar-cleaned GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_planar_clean.glb"
    report_path = root / "planar-backdrop-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        minimum, maximum = world_bounds(meshes)
        objects = [clean_object(obj, minimum, maximum) for obj in meshes]
        detected = [item for item in objects if item["planarBackdropDetected"]]
        if len(detected) != 1:
            raise RuntimeError(
                f"Expected exactly one unambiguous planar backdrop, detected {len(detected)}"
            )
        export_glb(output, meshes)
        after_minimum, after_maximum = world_bounds(
            [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
        )
        report.update(
            success=True,
            inputBounds={"minimum": list(minimum), "maximum": list(maximum)},
            outputBounds={"minimum": list(after_minimum), "maximum": list(after_maximum)},
            objects=objects,
            planarBackdropsRemoved=len(detected),
            verticesRemoved=sum(item["verticesRemoved"] for item in objects),
            outputBytes=output.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
