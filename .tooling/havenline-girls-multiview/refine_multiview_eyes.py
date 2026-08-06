#!/usr/bin/env python3
"""Add flat, natural irises and pupils inside reconstructed eye sockets.

The neural mesh already contains eyelids and sclera. Previous spherical inserts protruded
from the face and created a googly-eyed result. This pass uses thin matte oval discs placed
flush against the reconstructed socket, preserving the existing eye shape while adding the
missing dark iris and pupil. Each disc intentionally exceeds the production renderer's
minimum skinned-mesh vertex count so it cannot be mistaken for helper geometry and hidden.
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
    ordered = sorted(values)
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
    obj["havenlineEyeSurfaceType"] = "flat flush oval disc"
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
    face_y = quantile([point.y for point in upper], 0.075)
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": bounds_center_x + height * 0.0080,
        "faceY": face_y,
        "eyeZ": minimum.z + height * 0.854,
        "eyeOffsetX": min(height * 0.0305, width * 0.077),
        "sampleCount": len(upper),
    }


def add_eyes(character, frame):
    height = frame["height"]
    iris = make_material(f"{character}_RefinedIris", (0.026, 0.008, 0.003, 1.0), 0.78)
    pupil = make_material(f"{character}_RefinedPupil", (0.0010, 0.0010, 0.0012, 1.0), 0.82)
    created = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        iris_y = frame["faceY"] - height * 0.0010
        pupil_y = iris_y - height * 0.00025
        z = frame["eyeZ"]
        created.append(
            oval_disc(
                f"{character}_RefinedIris_{side}",
                (x, iris_y, z),
                height * 0.0091,
                height * 0.0104,
                iris,
                128,
            )
        )
        created.append(
            oval_disc(
                f"{character}_RefinedPupil_{side}",
                (x, pupil_y, z),
                height * 0.0038,
                height * 0.0045,
                pupil,
                112,
            )
        )
    for obj in created:
        if len(obj.data.vertices) < 101:
            raise RuntimeError(
                f"Production renderer would hide {obj.name}: only {len(obj.data.vertices)} vertices"
            )
    return created


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
        "schemaVersion": 6,
        "character": args.character,
        "source": args.input,
        "output": str(output_path),
        "success": False,
        "method": "flat matte iris and pupil discs placed flush in existing reconstructed sockets and sized above the production capture mesh threshold",
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = estimate_eye_frame(meshes)
        created = add_eyes(args.character, frame)
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        export_glb(output_path, all_meshes)
        report.update({
            "success": True,
            "eyeFrame": {
                "centerX": frame["centerX"],
                "faceY": frame["faceY"],
                "eyeZ": frame["eyeZ"],
                "eyeOffsetX": frame["eyeOffsetX"],
                "sampleCount": frame["sampleCount"],
            },
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
