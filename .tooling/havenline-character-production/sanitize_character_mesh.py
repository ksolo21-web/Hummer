#!/usr/bin/env python3
"""Remove generated mesh debris without deleting legitimate character detail.

The hosted image-to-3D output can contain isolated single triangles, needle-like ribbons,
or distant slivers. Those defects may pass polygon-count checks yet become large black
spikes after skinning. This Blender-side sanitizer works on connected mesh islands,
preserves materials/textures, and records every removal before the production rig step.
"""

from __future__ import annotations

import argparse
import json
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


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


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

    # Unambiguous reconstruction dust: lone triangles/quads cannot be intentional
    # facial, clothing, hair, backpack, or equipment volumes.
    if vertices <= 4 and faces <= 2:
        return True, "isolated triangle or quad debris"

    # Needle/ribbon debris: long relative to the character but nearly zero in two
    # dimensions. This catches the detached black spikes seen on the generated leads.
    if (
        vertices <= 40
        and largest >= scale * 0.11
        and smallest <= scale * 0.015
        and middle <= scale * 0.045
        and volume <= scale**3 * 0.00035
    ):
        return True, "needle-like disconnected reconstruction island"

    # Tiny floating fragments are not useful mobile detail and become visual specks.
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
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        return 0.0
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
        "schemaVersion": 1,
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
        span = scene_span(meshes)
        object_reports = [sanitize_object(obj, span) for obj in meshes]
        if not any(len(obj.data.vertices) for obj in meshes):
            raise RuntimeError("Sanitization removed every mesh vertex")
        export_cleaned(cleaned_path, meshes)
        report.update(
            success=True,
            sceneSpan=span,
            cleanedAsset=str(cleaned_path),
            cleanedBytes=cleaned_path.stat().st_size,
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
