#!/usr/bin/env python3
"""Add centered 3D irises and pupils to a multi-view reconstruction with blank eyes."""

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
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def world_vertices(meshes):
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            yield matrix @ vertex.co


def quantile(values, fraction):
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
    return material


def apply_material(obj, material):
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = True


def apply_scale(obj):
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.select_set(False)


def ellipsoid(name, location, scale, material, segments=36, rings=24):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def estimate_eye_frame(meshes):
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    height = max(extent.z, 1e-6)
    width = max(extent.x, 1e-6)
    center_x = (minimum.x + maximum.x) * 0.5
    upper = [
        point
        for point in world_vertices(meshes)
        if point.z >= minimum.z + height * 0.77
        and point.z <= minimum.z + height * 0.94
        and abs(point.x - center_x) <= width * 0.19
    ]
    if len(upper) < 40:
        raise RuntimeError(f"Not enough facial samples to place eyes safely: {len(upper)}")
    face_y = quantile([point.y for point in upper], 0.075)
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": center_x,
        "faceY": face_y,
        "eyeZ": minimum.z + height * 0.872,
        "eyeOffsetX": min(height * 0.036, width * 0.092),
        "sampleCount": len(upper),
    }


def add_eyes(character, frame):
    height = frame["height"]
    iris = make_material(f"{character}_RefinedIris", (0.095, 0.030, 0.008, 1.0), 0.40)
    pupil = make_material(f"{character}_RefinedPupil", (0.002, 0.002, 0.003, 1.0), 0.34)
    highlight = make_material(f"{character}_RefinedEyeHighlight", (0.96, 0.95, 0.91, 1.0), 0.28)
    created = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        y = frame["faceY"] - height * 0.0048
        z = frame["eyeZ"]
        created.append(ellipsoid(
            f"{character}_RefinedIris_{side}",
            (x, y, z),
            (height * 0.0132, height * 0.0042, height * 0.0165),
            iris,
        ))
        created.append(ellipsoid(
            f"{character}_RefinedPupil_{side}",
            (x, y - height * 0.0031, z),
            (height * 0.0062, height * 0.0024, height * 0.0082),
            pupil,
            28,
            18,
        ))
        created.append(ellipsoid(
            f"{character}_RefinedHighlight_{side}",
            (x - side * height * 0.0031, y - height * 0.0053, z + height * 0.0050),
            (height * 0.0026, height * 0.0015, height * 0.0032),
            highlight,
            20,
            14,
        ))
    return created


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
        raise RuntimeError(f"No refined GLB was exported to {path}")


def main():
    args = parse_args()
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    output_path = output_root / f"{args.character}_face_refined.glb"
    report_path = output_root / "multiview-eye-refinement-report.json"
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
            "outputBytes": output_path.stat().st_size,
        })
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
