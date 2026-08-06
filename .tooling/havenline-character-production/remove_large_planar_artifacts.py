#!/usr/bin/env python3
"""Remove one unambiguous large planar reconstruction artifact on any scene axis.

TRELLIS multi-view exports sometimes contain a floor, billboard, or capture sheet that is
spatially separated from the character and makes orientation detection impossible. The
artifact is a minority vertex cluster at one axis extreme, spans most of the other two
scene axes, and has negligible thickness. This pass detects that geometry without assuming
which axis is up, deletes only its prevalidated vertex set in Blender edit mode, preserves
materials/UVs/normals on the character, and fails closed unless exactly one candidate is
unambiguous.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


AXES = ("x", "y", "z")


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


def coordinate(point: Vector, axis: str) -> float:
    return float(getattr(point, axis))


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


def bounds_for_points(points):
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum, maximum - minimum


def detect_candidates(obj, scene_minimum, scene_maximum):
    all_samples = [(index, point) for index, point in world_vertices(obj)]
    count = len(all_samples)
    if count < 1000:
        return []

    scene_extent = scene_maximum - scene_minimum
    scene_spans = {axis: max(coordinate(scene_extent, axis), 1e-6) for axis in AXES}
    scene_span = max(scene_spans.values())
    candidates = []

    for axis in AXES:
        samples = sorted(
            (coordinate(point, axis), index, point)
            for index, point in all_samples
        )
        minimum_gap = scene_span * 0.0035
        for split_index in range(count - 1):
            lower_value = samples[split_index][0]
            upper_value = samples[split_index + 1][0]
            gap = upper_value - lower_value
            if gap < minimum_gap:
                continue
            lower_count = split_index + 1
            upper_count = count - lower_count
            for side, side_count, selected_samples in (
                ("minimum", lower_count, samples[:lower_count]),
                ("maximum", upper_count, samples[lower_count:]),
            ):
                ratio = side_count / count
                if not 0.005 <= ratio <= 0.35:
                    continue
                points = [item[2] for item in selected_samples]
                minimum, maximum, extent = bounds_for_points(points)
                thickness_ratio = coordinate(extent, axis) / scene_span
                other_axes = [value for value in AXES if value != axis]
                coverage = {
                    other: coordinate(extent, other) / scene_spans[other]
                    for other in other_axes
                }
                coverage_values = sorted(coverage.values())
                if thickness_ratio > 0.035:
                    continue
                if coverage_values[0] < 0.50 or coverage_values[1] < 0.68:
                    continue
                threshold = (lower_value + upper_value) * 0.5
                candidates.append(
                    {
                        "axis": axis,
                        "side": side,
                        "threshold": threshold,
                        "gap": gap,
                        "selectedRatio": ratio,
                        "selectedCount": side_count,
                        "thicknessRatio": thickness_ratio,
                        "coverage": coverage,
                        "minimum": list(minimum),
                        "maximum": list(maximum),
                        "extent": list(extent),
                    }
                )
    return candidates


def candidate_indices(obj, candidate):
    axis = candidate["axis"]
    side = candidate["side"]
    threshold = float(candidate["threshold"])
    indices = []
    for index, point in world_vertices(obj):
        value = coordinate(point, axis)
        remove = (side == "minimum" and value < threshold) or (
            side == "maximum" and value > threshold
        )
        if remove:
            indices.append(index)
    return indices


def delete_vertices(obj, indices):
    if not indices:
        raise RuntimeError(f"Planar artifact candidate for {obj.name} selected no vertices")
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


def choose_candidate(meshes, scene_minimum, scene_maximum):
    all_candidates = []
    for obj in meshes:
        for candidate in detect_candidates(obj, scene_minimum, scene_maximum):
            candidate = dict(candidate)
            candidate["object"] = obj.name
            all_candidates.append((obj, candidate))
    if not all_candidates:
        raise RuntimeError("No unambiguous large planar artifact was detected")
    all_candidates.sort(
        key=lambda pair: (
            min(pair[1]["coverage"].values()),
            max(pair[1]["coverage"].values()),
            pair[1]["gap"],
            -pair[1]["selectedRatio"],
        ),
        reverse=True,
    )
    best_obj, best = all_candidates[0]
    if len(all_candidates) > 1:
        _, second = all_candidates[1]
        best_min_coverage = min(best["coverage"].values())
        second_min_coverage = min(second["coverage"].values())
        if (
            abs(best_min_coverage - second_min_coverage) < 0.03
            and abs(best["thicknessRatio"] - second["thicknessRatio"]) < 0.008
            and abs(best["gap"] - second["gap"]) < max(
                (scene_maximum - scene_minimum).length * 0.002,
                1e-6,
            )
            and (best["axis"], best["side"]) != (second["axis"], second["side"])
        ):
            raise RuntimeError(
                "Two planar artifact candidates are too similar to delete safely: "
                f"{best} versus {second}"
            )
    return best_obj, best, [item[1] for item in all_candidates]


def export_glb(path: pathlib.Path, meshes):
    bpy.ops.object.select_all(action="DESELECT")
    surviving = [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
    if not surviving:
        raise RuntimeError("Planar artifact cleanup removed every mesh")
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
    output = root / f"{args.character}_large_plane_clean.glb"
    report_path = root / "large-planar-artifact-report.json"
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
        scene_minimum, scene_maximum = world_bounds(meshes)
        obj, candidate, all_candidates = choose_candidate(meshes, scene_minimum, scene_maximum)
        before = len(obj.data.vertices)
        indices = candidate_indices(obj, candidate)
        selected_ratio = len(indices) / max(before, 1)
        if abs(selected_ratio - candidate["selectedRatio"]) > 0.01:
            raise RuntimeError(
                f"Planar candidate selection ratio changed: detected={candidate['selectedRatio']:.4f}, "
                f"selected={selected_ratio:.4f}"
            )
        selected = delete_vertices(obj, indices)
        after = len(obj.data.vertices)
        removed = before - after
        if removed != selected or removed <= 0:
            raise RuntimeError(
                f"Planar deletion mismatch: selected={selected}, removed={removed}"
            )
        export_glb(output, meshes)
        output_minimum, output_maximum = world_bounds(
            [mesh for mesh in meshes if mesh.type == "MESH" and len(mesh.data.vertices) > 0]
        )
        report.update(
            success=True,
            inputBounds={"minimum": list(scene_minimum), "maximum": list(scene_maximum)},
            outputBounds={"minimum": list(output_minimum), "maximum": list(output_maximum)},
            selectedCandidate=candidate,
            candidatesConsidered=all_candidates,
            verticesBefore=before,
            verticesAfter=after,
            verticesRemoved=removed,
            removedRatio=removed / max(before, 1),
            outputBytes=output.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
