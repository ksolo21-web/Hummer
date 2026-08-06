#!/usr/bin/env python3
"""Remove one dominant broad planar reconstruction sheet, even when it touches the body.

A TRELLIS floor or capture sheet is not always located at the scene minimum. It may pass
through the boot soles while remaining a distinct, double-sided planar layer. This pass
finds dense peaks of nearly parallel polygon centres anywhere on each scene axis. A valid
candidate must span nearly the full other-axis footprint, contain substantial surface
area, remain paper-thin, and dominate every competing planar layer. Only its aligned
faces are deleted; nearby non-planar boot and character faces are preserved. The pass
fails closed unless one sheet is unambiguous.
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


AXES = ("x", "y", "z")
BIN_COUNT = 400
NORMAL_ALIGNMENT_MINIMUM = 0.90
THICKNESS_RATIOS = (0.0025, 0.0040, 0.0060, 0.0100)


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


def coordinate(value, axis: str) -> float:
    return float(getattr(value, axis))


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def triangle_area(points) -> float:
    if len(points) < 3:
        return 0.0
    origin = points[0]
    area = 0.0
    for index in range(1, len(points) - 1):
        area += ((points[index] - origin).cross(points[index + 1] - origin)).length * 0.5
    return float(area)


def polygon_records(obj):
    matrix = obj.matrix_world
    normal_matrix = matrix.to_3x3()
    records = []
    for polygon in obj.data.polygons:
        points = [matrix @ obj.data.vertices[index].co for index in polygon.vertices]
        center = sum(points, Vector((0.0, 0.0, 0.0))) / len(points)
        normal = (normal_matrix @ polygon.normal).normalized()
        records.append(
            {
                "index": polygon.index,
                "points": points,
                "center": center,
                "normal": normal,
                "area": triangle_area(points),
            }
        )
    return records


def bounds_for_points(points):
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum, maximum - minimum


def peak_centres(records, axis: str, minimum_value: float, maximum_value: float):
    span = max(maximum_value - minimum_value, 1e-6)
    bins = [0.0] * BIN_COUNT
    for record in records:
        if abs(coordinate(record["normal"], axis)) < NORMAL_ALIGNMENT_MINIMUM:
            continue
        value = coordinate(record["center"], axis)
        index = int((value - minimum_value) / span * BIN_COUNT)
        index = max(0, min(BIN_COUNT - 1, index))
        bins[index] += max(record["area"], 0.0)
    ranked = sorted(range(BIN_COUNT), key=lambda index: bins[index], reverse=True)
    centres = []
    for index in ranked[:12]:
        if bins[index] <= 0:
            continue
        centre = minimum_value + (index + 0.5) / BIN_COUNT * span
        if any(abs(centre - existing) < span / BIN_COUNT * 2.0 for existing in centres):
            continue
        centres.append(centre)
    return centres


def candidate_for(
    obj,
    records,
    scene_minimum,
    scene_maximum,
    total_area: float,
    axis: str,
    peak: float,
    thickness_ratio: float,
):
    scene_extent = scene_maximum - scene_minimum
    scene_span = max(scene_extent.x, scene_extent.y, scene_extent.z, 1e-6)
    half_thickness = scene_span * thickness_ratio * 0.5
    selected = [
        record
        for record in records
        if abs(coordinate(record["center"], axis) - peak) <= half_thickness
        and abs(coordinate(record["normal"], axis)) >= NORMAL_ALIGNMENT_MINIMUM
    ]
    if len(selected) < 200:
        return None
    points = [point for record in selected for point in record["points"]]
    minimum, maximum, extent = bounds_for_points(points)
    other_axes = [value for value in AXES if value != axis]
    coverage = {
        other: coordinate(extent, other) / max(coordinate(scene_extent, other), 1e-6)
        for other in other_axes
    }
    coverage_values = sorted(coverage.values())
    selected_area = sum(record["area"] for record in selected)
    area_fraction = selected_area / max(total_area, 1e-9)
    actual_thickness_ratio = coordinate(extent, axis) / scene_span
    centre_values = [coordinate(record["center"], axis) for record in selected]
    weighted_denominator = max(selected_area, 1e-9)
    weighted_centre = sum(
        coordinate(record["center"], axis) * record["area"] for record in selected
    ) / weighted_denominator
    variance = sum(
        record["area"] * (coordinate(record["center"], axis) - weighted_centre) ** 2
        for record in selected
    ) / weighted_denominator
    centre_standard_deviation = math.sqrt(max(variance, 0.0))
    mean_alignment = sum(
        abs(coordinate(record["normal"], axis)) * record["area"] for record in selected
    ) / weighted_denominator
    face_ratio = len(selected) / max(len(records), 1)

    if coverage_values[0] < 0.85 or coverage_values[1] < 0.90:
        return None
    if area_fraction < 0.15 or area_fraction > 0.88:
        return None
    if actual_thickness_ratio > 0.020:
        return None
    if centre_standard_deviation > scene_span * 0.006:
        return None
    if mean_alignment < 0.90:
        return None
    if face_ratio > 0.60:
        return None

    return {
        "object": obj.name,
        "axis": axis,
        "peak": peak,
        "weightedCentre": weighted_centre,
        "thicknessRatioRequested": thickness_ratio,
        "actualThicknessRatio": actual_thickness_ratio,
        "centreStandardDeviation": centre_standard_deviation,
        "faceIndices": [record["index"] for record in selected],
        "facesSelected": len(selected),
        "faceRatio": face_ratio,
        "selectedArea": selected_area,
        "areaFraction": area_fraction,
        "meanNormalAlignment": mean_alignment,
        "coverage": coverage,
        "minimum": list(minimum),
        "maximum": list(maximum),
        "extent": list(extent),
        "centreRange": [min(centre_values), max(centre_values)],
    }


def same_sheet(first, second, scene_span: float) -> bool:
    return (
        first["object"] == second["object"]
        and first["axis"] == second["axis"]
        and abs(first["weightedCentre"] - second["weightedCentre"]) <= scene_span * 0.012
    )


def candidate_score(candidate):
    return (
        candidate["areaFraction"],
        min(candidate["coverage"].values()),
        candidate["meanNormalAlignment"],
        candidate["facesSelected"],
        -candidate["actualThicknessRatio"],
    )


def find_candidate(meshes, scene_minimum, scene_maximum):
    scene_extent = scene_maximum - scene_minimum
    scene_span = max(scene_extent.x, scene_extent.y, scene_extent.z, 1e-6)
    candidates = []
    diagnostics = []
    for obj in meshes:
        if len(obj.data.polygons) < 100:
            continue
        records = polygon_records(obj)
        total_area = sum(record["area"] for record in records)
        for axis in AXES:
            minimum_value = coordinate(scene_minimum, axis)
            maximum_value = coordinate(scene_maximum, axis)
            peaks = peak_centres(records, axis, minimum_value, maximum_value)
            diagnostics.append(
                {
                    "object": obj.name,
                    "axis": axis,
                    "peakCentres": peaks,
                    "totalFaces": len(records),
                    "totalArea": total_area,
                }
            )
            for peak in peaks:
                for thickness_ratio in THICKNESS_RATIOS:
                    candidate = candidate_for(
                        obj,
                        records,
                        scene_minimum,
                        scene_maximum,
                        total_area,
                        axis,
                        peak,
                        thickness_ratio,
                    )
                    if candidate is not None:
                        candidates.append((obj, candidate))
    if not candidates:
        raise RuntimeError(
            "No dominant broad planar layer was detected; diagnostics=" + json.dumps(diagnostics)
        )
    candidates.sort(key=lambda pair: candidate_score(pair[1]), reverse=True)
    best_obj, best = candidates[0]
    competing = [
        candidate
        for _, candidate in candidates[1:]
        if not same_sheet(best, candidate, scene_span)
    ]
    if competing:
        second = competing[0]
        if (
            second["areaFraction"] >= best["areaFraction"] * 0.82
            and min(second["coverage"].values()) >= min(best["coverage"].values()) - 0.04
        ):
            raise RuntimeError(
                "Two dominant planar layers are too similar to delete safely: "
                f"{best} versus {second}"
            )
    return best_obj, best, [item[1] for item in candidates], diagnostics


def delete_faces_and_loose_vertices(obj, face_indices):
    if not face_indices:
        raise RuntimeError("No faces were supplied for planar deletion")
    if bpy.context.object and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="FACE")
    bpy.ops.mesh.select_all(action="DESELECT")
    bpy.ops.object.mode_set(mode="OBJECT")
    for polygon in obj.data.polygons:
        polygon.select = False
    for index in face_indices:
        obj.data.polygons[index].select = True
    selected_faces = sum(1 for polygon in obj.data.polygons if polygon.select)
    if selected_faces != len(face_indices):
        raise RuntimeError(
            f"Face selection synchronization failed: expected {len(face_indices)}, got {selected_faces}"
        )
    faces_before = len(obj.data.polygons)
    vertices_before = len(obj.data.vertices)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.delete(type="FACE")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.select_all(action="DESELECT")
    bpy.ops.mesh.select_loose()
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    return {
        "facesBefore": faces_before,
        "facesAfter": len(obj.data.polygons),
        "facesRemoved": faces_before - len(obj.data.polygons),
        "verticesBefore": vertices_before,
        "verticesAfter": len(obj.data.vertices),
        "verticesRemoved": vertices_before - len(obj.data.vertices),
    }


def export_glb(path: pathlib.Path, meshes) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    surviving = [obj for obj in meshes if obj.type == "MESH" and len(obj.data.polygons) > 0]
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


def strip_face_indices(candidate):
    copy = dict(candidate)
    copy.pop("faceIndices", None)
    return copy


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_extreme_plane_clean.glb"
    report_path = root / "extreme-planar-face-report.json"
    report = {
        "schemaVersion": 2,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "detector": "dominant aligned polygon-centre layer anywhere in scene",
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        scene_minimum, scene_maximum = world_bounds(meshes)
        obj, candidate, candidates, diagnostics = find_candidate(
            meshes, scene_minimum, scene_maximum
        )
        deletion = delete_faces_and_loose_vertices(obj, candidate["faceIndices"])
        if deletion["facesRemoved"] != candidate["facesSelected"]:
            raise RuntimeError(
                f"Planar face deletion mismatch: expected {candidate['facesSelected']}, "
                f"removed {deletion['facesRemoved']}"
            )
        if deletion["facesRemoved"] <= 0:
            raise RuntimeError("Planar cleanup removed no faces")
        export_glb(output, meshes)
        output_minimum, output_maximum = world_bounds(
            [mesh for mesh in meshes if mesh.type == "MESH" and len(mesh.data.polygons) > 0]
        )
        report.update(
            success=True,
            inputBounds={"minimum": list(scene_minimum), "maximum": list(scene_maximum)},
            outputBounds={"minimum": list(output_minimum), "maximum": list(output_maximum)},
            selectedCandidate=strip_face_indices(candidate),
            candidatesConsidered=[strip_face_indices(item) for item in candidates],
            peakDiagnostics=diagnostics,
            deletion=deletion,
            outputBytes=output.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
