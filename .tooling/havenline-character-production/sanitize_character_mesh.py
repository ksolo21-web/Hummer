#!/usr/bin/env python3
"""Remove generated mesh debris and repair provably wrong reconstruction orientation.

The hosted image-to-3D output can contain isolated single triangles, needle-like ribbons,
or distant slivers. Some generators also emit a Z-up mesh inside a glTF container whose
standard is Y-up. Blender then imports the character lying on its back, and a later rig can
still pass polygon and animation checks while producing an unusable sideways body. This
sanitizer fails closed, rotates only when one horizontal axis is unambiguously the body-height
axis, preserves materials/textures, and records every repair before the production rig step.
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import traceback

import bmesh
import bpy
from mathutils import Matrix, Vector


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


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector(
        (
            min(point.x for point in points),
            min(point.y for point in points),
            min(point.z for point in points),
        )
    )
    maximum = Vector(
        (
            max(point.x for point in points),
            max(point.y for point in points),
            max(point.z for point in points),
        )
    )
    return minimum, maximum


def bounds_payload(meshes) -> dict:
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    return {
        "minimum": [minimum.x, minimum.y, minimum.z],
        "maximum": [maximum.x, maximum.y, maximum.z],
        "extents": [extent.x, extent.y, extent.z],
        "width": extent.x,
        "depth": extent.y,
        "height": extent.z,
    }


def orient_upright(meshes) -> dict:
    """Make Blender Z the body-height axis only when the evidence is unambiguous.

    TripoSR's raw GLB arrives with body-height on Blender Y because the mesh is emitted
    Z-up inside a glTF Y-up container. The correct conversion is negative 90 degrees around
    Blender X: positive 90 degrees makes the silhouette vertical but leaves the person upside
    down. We rotate only when X or Y exceeds Blender Z by at least 35%; otherwise we preserve
    the source orientation and let later proportion gates decide.
    """

    before = bounds_payload(meshes)
    extents = before["extents"]
    dominant_axis = max(range(3), key=lambda axis: extents[axis])
    z_extent = max(extents[2], 1e-6)
    rotation = Matrix.Identity(4)
    repair = "none"

    if dominant_axis == 1 and extents[1] >= z_extent * 1.35:
        rotation = Matrix.Rotation(math.radians(-90.0), 4, "X")
        repair = "rotate_negative_90_x_y_to_z_upright"
    elif dominant_axis == 0 and extents[0] >= z_extent * 1.35:
        rotation = Matrix.Rotation(math.radians(90.0), 4, "Y")
        repair = "rotate_positive_90_y_x_to_z_upright"

    if repair != "none":
        for obj in meshes:
            obj.matrix_world = rotation @ obj.matrix_world
        bpy.context.view_layer.update()

    after = bounds_payload(meshes)
    horizontal_max = max(after["width"], after["depth"], 1e-6)
    if after["height"] < horizontal_max * 1.05:
        raise RuntimeError(
            "Character orientation remains ambiguous or non-standing after safe axis repair: "
            f"before={before['extents']}, after={after['extents']}"
        )

    return {
        "schemaVersion": 2,
        "repair": repair,
        "dominantAxisBefore": ("X", "Y", "Z")[dominant_axis],
        "before": before,
        "after": after,
        "standingAxisVerified": True,
        "uprightDirectionRule": "TripoSR Y-to-Blender-Z uses negative 90 degrees around X",
    }


def component_vertices(mesh):
    adjacency = [[] for _ in mesh.vertices]
    for edge in mesh.edges:
        a, b = edge.vertices
        adjacency[a].append(b)
        adjacency[b].append(a)

    visited = set()
    components = []
    for start in range(len(mesh.vertices)):
        if start in visited:
            continue
        stack = [start]
        visited.add(start)
        component = []
        while stack:
            current = stack.pop()
            component.append(current)
            for neighbor in adjacency[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    stack.append(neighbor)
        components.append(component)
    return components


def component_metrics(obj, component, face_counts):
    points = [obj.matrix_world @ obj.data.vertices[index].co for index in component]
    minimum = Vector(
        (
            min(point.x for point in points),
            min(point.y for point in points),
            min(point.z for point in points),
        )
    )
    maximum = Vector(
        (
            max(point.x for point in points),
            max(point.y for point in points),
            max(point.z for point in points),
        )
    )
    extent = maximum - minimum
    extents = sorted((max(extent.x, 0.0), max(extent.y, 0.0), max(extent.z, 0.0)))
    return {
        "vertices": len(component),
        "faces": face_counts.get(component[0], 0),
        "minimum": list(minimum),
        "maximum": list(maximum),
        "extents": [extent.x, extent.y, extent.z],
        "smallestExtent": extents[0],
        "middleExtent": extents[1],
        "largestExtent": extents[2],
        "boxVolume": extents[0] * extents[1] * extents[2],
    }


def should_remove(metrics, global_span: float) -> tuple[bool, str | None]:
    vertices = metrics["vertices"]
    faces = metrics["faces"]
    smallest = metrics["smallestExtent"]
    middle = metrics["middleExtent"]
    largest = metrics["largestExtent"]
    volume = metrics["boxVolume"]
    scale = max(global_span, 1e-6)

    if vertices <= 4 and faces <= 2:
        return True, "isolated triangle or quad debris"

    if (
        vertices <= 40
        and largest >= scale * 0.11
        and smallest <= scale * 0.015
        and middle <= scale * 0.045
        and volume <= scale**3 * 0.00035
    ):
        return True, "needle-like disconnected reconstruction island"

    if vertices <= 10 and faces <= 8 and largest <= scale * 0.035:
        return True, "sub-pixel disconnected fragment"

    return False, None


def sanitize_object(obj, global_span: float):
    mesh = obj.data
    mesh.validate(verbose=False)
    mesh.update()
    components = component_vertices(mesh)

    vertex_to_component = {}
    for component_index, component in enumerate(components):
        for vertex_index in component:
            vertex_to_component[vertex_index] = component_index

    faces_per_component = {index: 0 for index in range(len(components))}
    for polygon in mesh.polygons:
        if not polygon.vertices:
            continue
        component_index = vertex_to_component.get(polygon.vertices[0])
        if component_index is not None:
            faces_per_component[component_index] += 1

    removed_vertex_indices = set()
    removed = []
    kept = []
    for component_index, component in enumerate(components):
        face_counts = {component[0]: faces_per_component.get(component_index, 0)}
        metrics = component_metrics(obj, component, face_counts)
        remove, reason = should_remove(metrics, global_span)
        metrics["componentIndex"] = component_index
        if remove:
            metrics["reason"] = reason
            removed.append(metrics)
            removed_vertex_indices.update(component)
        else:
            kept.append(metrics)

    if removed_vertex_indices:
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bm.verts.ensure_lookup_table()
        delete_verts = [bm.verts[index] for index in sorted(removed_vertex_indices)]
        bmesh.ops.delete(bm, geom=delete_verts, context="VERTS")
        bm.to_mesh(mesh)
        bm.free()
        mesh.validate(verbose=False)
        mesh.update()

    return {
        "object": obj.name,
        "componentsBefore": len(components),
        "componentsRemoved": len(removed),
        "verticesRemoved": len(removed_vertex_indices),
        "removed": removed,
        "largestKeptComponents": sorted(
            kept,
            key=lambda item: (item["vertices"], item["faces"]),
            reverse=True,
        )[:12],
    }


def scene_span(meshes) -> float:
    minimum, maximum = world_bounds(meshes)
    size = maximum - minimum
    return max(size.x, size.y, size.z)


def export_cleaned(path: pathlib.Path, meshes) -> None:
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
        raise RuntimeError(f"Sanitizer did not export a non-empty GLB: {path}")


def main() -> int:
    args = parse_args()
    source = pathlib.Path(args.input)
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    report_path = output / "mesh-sanitization-report.json"
    cleaned_path = output / f"{args.character}_sanitized.glb"
    report = {
        "schemaVersion": 3,
        "character": args.character,
        "source": str(source),
        "success": False,
    }
    try:
        if not source.is_file() or source.stat().st_size == 0:
            raise RuntimeError(f"Missing raw character GLB: {source}")
        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(source))
        meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        if not meshes:
            raise RuntimeError("Raw character GLB imported without mesh objects")
        orientation = orient_upright(meshes)
        span = scene_span(meshes)
        object_reports = [sanitize_object(obj, span) for obj in meshes]
        if not any(len(obj.data.vertices) for obj in meshes):
            raise RuntimeError("Sanitization removed every mesh vertex")
        export_cleaned(cleaned_path, meshes)
        report.update(
            success=True,
            orientation=orientation,
            sceneSpan=span,
            cleanedAsset=str(cleaned_path),
            cleanedBytes=cleaned_path.stat().st_size,
            cleanedBounds=bounds_payload(meshes),
            objects=object_reports,
            componentsRemoved=sum(item["componentsRemoved"] for item in object_reports),
            verticesRemoved=sum(item["verticesRemoved"] for item in object_reports),
        )
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return 0
    except Exception as exception:
        report.update(error=repr(exception), traceback=traceback.format_exc())
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
