#!/usr/bin/env python3
"""Add flat, natural irises and pupils inside reconstructed eye sockets.

The neural mesh already contains eyelids and sclera. Previous spherical inserts protruded
from the face and created a googly-eyed result. This pass uses thin matte oval discs placed
just in front of each reconstructed socket's measured local surface, preserving the eye
shape while adding the missing dark iris and pupil. Each disc exceeds the production
renderer mesh threshold. Nothing behaves as a billboard or replaces the face.
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
        raise RuntimeError("No world-space mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def world_vertices(meshes):
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            yield matrix @ vertex.co


def quantile(values, fraction: float):
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise RuntimeError("Cannot compute face surface from an empty point set")
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def make_material(name, rgba, roughness):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = rgba
    principled = material.node_tree.nodes.get("Principled BSDF")
    if principled:
        if "Base Color" in principled.inputs:
            principled.inputs["Base Color"].default_value = rgba
        if "Roughness" in principled.inputs:
            principled.inputs["Roughness"].default_value = roughness
        if "Metallic" in principled.inputs:
            principled.inputs["Metallic"].default_value = 0.0
    return material


def apply_material(obj, material) -> None:
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = False


def oval_disc(name, location, radius_x, radius_z, material, segments):
    if segments < 100:
        raise RuntimeError(f"{name} must contain at least 101 vertices for production capture")
    center_x, center_y, center_z = location
    vertices = [(center_x, center_y, center_z)]
    for index in range(segments):
        angle = math.tau * index / segments
        vertices.append(
            (
                center_x + math.cos(angle) * radius_x,
                center_y,
                center_z + math.sin(angle) * radius_z,
            )
        )
    faces = []
    for index in range(segments):
        current = 1 + index
        following = 1 + ((index + 1) % segments)
        # Front proof camera is on negative Y, so this winding produces a -Y normal.
        faces.append((0, current, following))
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.update(calc_edges=True)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    apply_material(obj, material)
    obj["havenlineModeledEyeDetail"] = True
    obj["havenlineEyeSurfaceType"] = "flat local-surface oval disc"
    obj["havenlineProductionCaptureEligible"] = len(mesh.vertices) >= 101
    return obj


def estimate_eye_frame(meshes):
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    height = max(extent.z, 1e-6)
    width = max(extent.x, 1e-6)
    bounds_center_x = (minimum.x + maximum.x) * 0.5
    upper = [
        point
        for point in world_vertices(meshes)
        if point.z >= minimum.z + height * 0.77
        and point.z <= minimum.z + height * 0.94
        and abs(point.x - bounds_center_x) <= width * 0.19
    ]
    if len(upper) < 40:
        raise RuntimeError(f"Not enough facial samples to place eyes safely: {len(upper)}")
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": bounds_center_x + height * 0.0080,
        "coarseFaceY": quantile([point.y for point in upper], 0.075),
        "eyeZ": minimum.z + height * 0.854,
        "eyeOffsetX": min(height * 0.0305, width * 0.077),
        "sampleCount": len(upper),
    }


def local_eye_surface(meshes, eye_x, eye_z, height):
    radius_x = height * 0.0180
    radius_z = height * 0.0200
    samples = []
    for point in world_vertices(meshes):
        dx = (point.x - eye_x) / max(radius_x, 1e-6)
        dz = (point.z - eye_z) / max(radius_z, 1e-6)
        if dx * dx + dz * dz <= 1.0:
            samples.append(point.y)
    if len(samples) < 20:
        raise RuntimeError(
            f"Not enough local socket samples at x={eye_x:.6f}, z={eye_z:.6f}: {len(samples)}"
        )
    # Negative Y faces the proof camera. Use the local front percentile rather than a
    # broad face percentile that can leave the discs hidden behind the sclera.
    return quantile(samples, 0.015), len(samples)


def add_eyes(character, frame, meshes):
    height = frame["height"]
    iris = make_material(f"{character}_RefinedIris", (0.021, 0.006, 0.002, 1.0), 0.80)
    pupil = make_material(f"{character}_RefinedPupil", (0.0007, 0.0007, 0.0009, 1.0), 0.84)
    created = []
    placements = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        z = frame["eyeZ"]
        local_front_y, local_samples = local_eye_surface(meshes, x, z, height)
        iris_y = local_front_y - height * 0.0024
        pupil_y = iris_y - height * 0.00030
        created.append(
            oval_disc(
                f"{character}_RefinedIris_{side}",
                (x, iris_y, z),
                height * 0.0102,
                height * 0.0113,
                iris,
                128,
            )
        )
        created.append(
            oval_disc(
                f"{character}_RefinedPupil_{side}",
                (x, pupil_y, z),
                height * 0.0041,
                height * 0.0048,
                pupil,
                112,
            )
        )
        placements.append(
            {
                "side": side,
                "x": x,
                "z": z,
                "localFrontY": local_front_y,
                "irisY": iris_y,
                "pupilY": pupil_y,
                "localSampleCount": local_samples,
            }
        )
    for obj in created:
        if len(obj.data.vertices) < 101:
            raise RuntimeError(
                f"Production renderer would hide {obj.name}: only {len(obj.data.vertices)} vertices"
            )
    return created, placements


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
        raise RuntimeError(f"No refined GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    output_path = output_root / f"{args.character}_face_refined.glb"
    report_path = output_root / "multiview-eye-refinement-report.json"
    report = {
        "schemaVersion": 7,
        "character": args.character,
        "source": args.input,
        "output": str(output_path),
        "success": False,
        "method": "flat matte iris and pupil discs positioned from each socket's measured local front surface",
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = estimate_eye_frame(meshes)
        created, placements = add_eyes(args.character, frame, meshes)
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        export_glb(output_path, all_meshes)
        report.update({
            "success": True,
            "eyeFrame": {
                "centerX": frame["centerX"],
                "coarseFaceY": frame["coarseFaceY"],
                "eyeZ": frame["eyeZ"],
                "eyeOffsetX": frame["eyeOffsetX"],
                "sampleCount": frame["sampleCount"],
            },
            "eyePlacements": placements,
            "modeledObjectsCreated": len(created),
            "modeledEyeVertexCounts": {obj.name: len(obj.data.vertices) for obj in created},
            "allModeledEyesProductionCaptureEligible": all(
                len(obj.data.vertices) >= 101 for obj in created
            ),
            "outputBytes": output_path.stat().st_size,
        })
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
