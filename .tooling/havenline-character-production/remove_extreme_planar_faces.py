#!/usr/bin/env python3
"""Remove a broad paper-thin reconstruction plane that touches the character.

Some TRELLIS floor sheets touch the boot soles, so a coordinate-gap classifier cannot
separate them. This pass evaluates polygon centres at all six scene extremes. A candidate
must occupy a thin slab, cover most of the other two scene axes, contain a meaningful
number of faces, and be overwhelmingly planar. It deletes faces in that slab rather than
all nearby vertices, then removes only vertices left with no polygons. This preserves the
character even when the floor touches the feet and fails closed unless one candidate is
clearly dominant.
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


def coordinate(value, axis: str) -> float:
    return float(getattr(value, axis))


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def polygon_world_data(obj, polygon):
    matrix = obj.matrix_world
    points = [matrix @ obj.data.vertices[index].co for index in polygon.vertices]
    center = sum(points, Vector((0.0, 0.0, 0.0))) / len(points)
    normal = (matrix.to_3x3() @ polygon.normal).normalized()
    return points, center, normal


def candidate_for(obj, scene_minimum, scene_maximum, axis: str, side: str, thickness_ratio: float):
    scene_extent = scene_maximum - scene_minimum
    scene_span = max(scene_extent.x, scene_extent.y, scene_extent.z, 1e-6)
    thickness = scene_span * thickness_ratio
    boundary = coordinate(scene_minimum if side == "minimum" else scene_maximum, axis)
    selected = []
    all_points = []
    aligned_faces = 0
    for polygon in obj.data.polygons:
        points, center, normal = polygon_world_data(obj, polygon)
        values = [coordinate(point, axis) for point in points]
        inside = (
            side == "minimum" and max(values) <= boundary + thickness
        ) or (
            side == "maximum" and min(values) >= boundary - thickness
        )
        if not inside:
            continue
        selected.append(polygon.index)
        all_points.extend(points)
        if abs(coordinate(normal, axis)) >= 0.82:
            aligned_faces += 1
    if len(selected) < 80 or not all_points:
        return None
    minimum = Vector((min(p.x for p in all_points), min(p.y for p in all_points), min(p.z for p in all_points)))
    maximum = Vector((max(p.x for p in all_points), max(p.y for p in all_points), max(p.z for p in all_points)))
    extent = maximum - minimum
    other_axes = [value for value in AXES if value != axis]
    coverage = {
        other: coordinate(extent, other) / max(coordinate(scene_extent, other), 1e-6)
        for other in other_axes
    }
    coverage_values = sorted(coverage.values())
    planar_alignment = aligned_faces / len(selected)
    face_ratio = len(selected) / max(len(obj.data.polygons), 1)
    if coverage_values[0] < 0.70 or coverage_values[1] < 0.78:
        return None
    if planar_alignment < 0.72:
        return None
    if not 0.002 <= face_ratio <= 0.55:
        return None
    return {
        "object": obj.name,
        "axis": axis,
        "side": side,
        "thicknessRatio": thickness_ratio,
        "thickness": thickness,
        "boundary": boundary,
        "faceIndices": selected,
        "facesSelected": len(selected),
        "faceRatio": face_ratio,
        "planarAlignment": planar_alignment,
        "coverage": coverage,
        "minimum": list(minimum),
        "maximum": list(maximum),
        "extent": list(extent),
    }


def find_candidate(meshes, scene_minimum, scene_maximum):
    candidates = []
    for obj in meshes:
        if len(obj.data.polygons) < 100:
            continue
        for axis in AXES:
            for side in ("minimum", "maximum"):
                for thickness_ratio in (0.004, 0.008, 0.015, 0.025):
                    candidate = candidate_for(
                        obj,
                        scene_minimum,
                        scene_maximum,
                        axis,
                        side,
                        thickness_ratio,
                    )
                    if candidate is not None:
                        candidates.append((obj, candidate))
    if not candidates:
        raise RuntimeError("No broad extreme planar face slab was detected")
    candidates.sort(
        key=lambda pair: (
            min(pair[1]["coverage"].values()),
            pair[1]["planarAlignment"],
            pair[1]["facesSelected"],
            -pair[1]["thicknessRatio"],
        ),
        reverse=True,
    )
    best_obj, best = candidates[0]
    if len(candidates) > 1:
        _, second = candidates[1]
        if (
            (best["axis"], best["side"]) != (second["axis"], second["side"])
            and abs(min(best["coverage"].values()) - min(second["coverage"].values())) < 0.025
            and abs(best["planarAlignment"] - second["planarAlignment"]) < 0.03
        ):
            raise RuntimeError(
                "Two extreme plane candidates are too similar to delete safely: "
                f"{best} versus {second}"
            )
    return best_obj, best, [item[1] for item in candidates]


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
        raise RuntimeError("Extreme-plane cleanup removed every mesh")
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
        raise RuntimeError(f"No extreme-plane-cleaned GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_extreme_plane_clean.glb"
    report_path = root / "extreme-planar-face-report.json"
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
        obj, candidate, candidates = find_candidate(meshes, scene_minimum, scene_maximum)
        deletion = delete_faces_and_loose_vertices(obj, candidate["faceIndices"])
        if deletion["facesRemoved"] != candidate["facesSelected"]:
            raise RuntimeError(
                f"Planar face deletion mismatch: expected {candidate['facesSelected']}, "
                f"removed {deletion['facesRemoved']}"
            )
        if deletion["facesRemoved"] <= 0:
            raise RuntimeError("Extreme plane cleanup removed no faces")
        export_glb(output, meshes)
        output_minimum, output_maximum = world_bounds(
            [mesh for mesh in meshes if mesh.type == "MESH" and len(mesh.data.polygons) > 0]
        )
        sanitized_candidate = dict(candidate)
        sanitized_candidate.pop("faceIndices", None)
        sanitized_candidates = []
        for item in candidates:
            copy = dict(item)
            copy.pop("faceIndices", None)
            sanitized_candidates.append(copy)
        report.update(
            success=True,
            inputBounds={"minimum": list(scene_minimum), "maximum": list(scene_maximum)},
            outputBounds={"minimum": list(output_minimum), "maximum": list(output_maximum)},
            selectedCandidate=sanitized_candidate,
            candidatesConsidered=sanitized_candidates,
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
