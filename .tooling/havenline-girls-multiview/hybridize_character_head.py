#!/usr/bin/env python3
"""Replace failed reconstructed facial geometry with a clean modeled 3D head.

The TripoSR body/outfit reconstruction is retained. Only the damaged central facial
surface is removed. A tapered stylized head, true 3D facial features, ears, hairline
curls, and Character 4's headband are built in the same Blender coordinate system.
The result remains pending human visual approval.
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
from mathutils import Vector


def args_after_separator() -> list[str]:
    values = sys.argv
    return values[values.index("--") + 1 :] if "--" in values else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True, choices=("Character3", "Character4"))
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(args_after_separator())


CHARACTERS = {
    "Character3": {
        "skin": (0.30, 0.115, 0.045, 1.0),
        "lip": (0.42, 0.075, 0.060, 1.0),
        "hair": (0.012, 0.008, 0.006, 1.0),
        "iris": (0.105, 0.040, 0.012, 1.0),
        "headband": None,
        "curl_scale": 1.00,
    },
    "Character4": {
        "skin": (0.38, 0.155, 0.060, 1.0),
        "lip": (0.48, 0.095, 0.070, 1.0),
        "hair": (0.020, 0.012, 0.008, 1.0),
        "iris": (0.120, 0.050, 0.015, 1.0),
        "headband": (0.78, 0.135, 0.035, 1.0),
        "curl_scale": 0.92,
    },
}


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def import_glb(path: pathlib.Path):
    bpy.ops.import_scene.gltf(filepath=str(path))
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError(f"No mesh objects were imported from {path}")
    return meshes


def world_points(meshes):
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            yield matrix @ vertex.co


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No world-space mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def make_material(name: str, rgba, roughness: float, metallic: float = 0.0):
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
            principled.inputs["Metallic"].default_value = metallic
    return material


def apply_material(obj, material) -> None:
    if hasattr(obj.data, "materials"):
        obj.data.materials.append(material)
    if obj.type == "MESH":
        for polygon in obj.data.polygons:
            polygon.material_index = 0
            polygon.use_smooth = True


def apply_scale(obj) -> None:
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.select_set(False)


def ellipsoid(name, location, scale, material, segments=48, rings=32):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def tapered_head(name, location, radii, material):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=44, location=(0, 0, 0))
    obj = bpy.context.object
    obj.name = name
    rx, ry, rz = radii
    for vertex in obj.data.vertices:
        unit = vertex.co.copy()
        nz = max(-1.0, min(1.0, unit.z))
        chin = max(-nz, 0.0)
        crown = max(nz, 0.0)
        width_factor = 1.0 - 0.24 * chin**1.55 - 0.055 * crown**2
        depth_factor = 0.96 - 0.08 * chin**1.35
        vertex.co.x = unit.x * rx * width_factor
        vertex.co.y = unit.y * ry * depth_factor
        vertex.co.z = unit.z * rz
    obj.location = location
    apply_material(obj, material)
    return obj


def curve_mesh(name, points, material, thickness):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 4
    curve.bevel_depth = thickness
    curve.bevel_resolution = 4
    spline = curve.splines.new("BEZIER")
    spline.bezier_points.add(len(points) - 1)
    for point, coordinate in zip(spline.bezier_points, points):
        point.co = coordinate
        point.handle_left_type = "AUTO"
        point.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(obj)
    curve.materials.append(material)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.convert(target="MESH")
    obj.select_set(False)
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    return obj


def torus(name, location, major_radius, minor_radius, material, rotation=(0.0, 0.0, 0.0), scale=(1.0, 1.0, 1.0)):
    bpy.ops.mesh.primitive_torus_add(
        major_segments=64,
        minor_segments=16,
        major_radius=major_radius,
        minor_radius=minor_radius,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def quantile(values, fraction: float):
    if not values:
        raise RuntimeError("Cannot compute a quantile from an empty collection")
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def estimate_face_frame(meshes):
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    height = max(extent.z, 1e-6)
    width = max(extent.x, 1e-6)
    center_x = (minimum.x + maximum.x) * 0.5

    upper_points = [
        point
        for point in world_points(meshes)
        if point.z >= minimum.z + height * 0.72
        and point.z <= minimum.z + height * 0.975
        and abs(point.x - center_x) <= width * 0.24
    ]
    if len(upper_points) < 50:
        raise RuntimeError(f"Not enough upper-face samples were found: {len(upper_points)}")

    face_surface_y = quantile([point.y for point in upper_points], 0.10)
    rx = min(height * 0.098, width * 0.245)
    ry = height * 0.058
    rz = height * 0.112
    center_z = minimum.z + height * 0.855
    center_y = face_surface_y + ry * 0.62
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "center": Vector((center_x, center_y, center_z)),
        "radii": Vector((rx, ry, rz)),
        "faceSurfaceY": face_surface_y,
        "upperPointCount": len(upper_points),
    }


def remove_failed_face(meshes, frame):
    center = frame["center"]
    radii = frame["radii"]
    face_surface_y = frame["faceSurfaceY"]
    removed_total = 0
    object_reports = []

    for obj in meshes:
        inverse = obj.matrix_world.inverted()
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        bm.verts.ensure_lookup_table()
        delete_verts = []
        for vertex in bm.verts:
            world = obj.matrix_world @ vertex.co
            nx = (world.x - center.x) / max(radii.x * 0.90, 1e-8)
            nz = (world.z - center.z) / max(radii.z * 0.94, 1e-8)
            central_ellipse = nx * nx + nz * nz <= 1.0
            front_half = world.y <= face_surface_y + radii.y * 0.50
            vertical_guard = world.z >= center.z - radii.z * 0.86
            if central_ellipse and front_half and vertical_guard:
                delete_verts.append(vertex)
        if delete_verts:
            bmesh.ops.delete(bm, geom=delete_verts, context="VERTS")
            if bm.faces:
                bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
            removed = len(delete_verts)
            removed_total += removed
        else:
            removed = 0
        bm.to_mesh(obj.data)
        bm.free()
        obj.data.validate(verbose=False)
        obj.data.update()
        object_reports.append({"object": obj.name, "verticesRemoved": removed})

    if removed_total == 0:
        raise RuntimeError("The failed facial surface was not found; refusing to add overlapping geometry")
    return removed_total, object_reports


def add_modeled_head(character: str, frame):
    config = CHARACTERS[character]
    center = frame["center"]
    rx, ry, rz = frame["radii"]

    skin = make_material(f"{character}_ModeledSkin", config["skin"], 0.58)
    hair = make_material(f"{character}_ModeledHair", config["hair"], 0.80)
    eye_white = make_material(f"{character}_EyeWhite", (0.92, 0.90, 0.84, 1.0), 0.42)
    iris = make_material(f"{character}_Iris", config["iris"], 0.40)
    black = make_material(f"{character}_Pupil", (0.003, 0.003, 0.004, 1.0), 0.38)
    lip = make_material(f"{character}_Lip", config["lip"], 0.52)

    created = []
    created.append(tapered_head(f"{character}_ModeledHead", center, (rx, ry, rz), skin))

    front_y = center.y - ry * 0.97
    eye_z = center.z + rz * 0.105
    eye_x = rx * 0.405
    for side in (-1, 1):
        x = center.x + side * eye_x
        created.append(ellipsoid(
            f"{character}_EyeWhite_{side}",
            (x, front_y - ry * 0.015, eye_z),
            (rx * 0.235, ry * 0.105, rz * 0.155),
            eye_white,
            36,
            24,
        ))
        created.append(ellipsoid(
            f"{character}_Iris_{side}",
            (x, front_y - ry * 0.115, eye_z),
            (rx * 0.102, ry * 0.060, rz * 0.086),
            iris,
            30,
            20,
        ))
        created.append(ellipsoid(
            f"{character}_Pupil_{side}",
            (x, front_y - ry * 0.175, eye_z),
            (rx * 0.047, ry * 0.035, rz * 0.043),
            black,
            24,
            16,
        ))
        created.append(ellipsoid(
            f"{character}_EyeHighlight_{side}",
            (x - side * rx * 0.028, front_y - ry * 0.208, eye_z + rz * 0.032),
            (rx * 0.020, ry * 0.020, rz * 0.019),
            eye_white,
            20,
            14,
        ))
        created.append(curve_mesh(
            f"{character}_Brow_{side}",
            [
                (x - rx * 0.21, front_y - ry * 0.11, eye_z + rz * 0.27),
                (x, front_y - ry * 0.16, eye_z + rz * 0.31),
                (x + rx * 0.21, front_y - ry * 0.11, eye_z + rz * 0.27),
            ],
            hair,
            height_thickness(frame, 0.0062),
        ))

    created.append(ellipsoid(
        f"{character}_Nose",
        (center.x, front_y - ry * 0.145, center.z - rz * 0.055),
        (rx * 0.105, ry * 0.105, rz * 0.125),
        skin,
        30,
        20,
    ))
    created.append(curve_mesh(
        f"{character}_Smile",
        [
            (center.x - rx * 0.30, front_y - ry * 0.145, center.z - rz * 0.365),
            (center.x, front_y - ry * 0.195, center.z - rz * 0.405),
            (center.x + rx * 0.30, front_y - ry * 0.145, center.z - rz * 0.365),
        ],
        lip,
        height_thickness(frame, 0.0068),
    ))
    created.append(ellipsoid(
        f"{character}_LowerLip",
        (center.x, front_y - ry * 0.135, center.z - rz * 0.455),
        (rx * 0.225, ry * 0.038, rz * 0.040),
        lip,
        28,
        18,
    ))

    for side in (-1, 1):
        created.append(ellipsoid(
            f"{character}_Ear_{side}",
            (center.x + side * rx * 0.98, center.y + ry * 0.03, center.z - rz * 0.055),
            (rx * 0.13, ry * 0.34, rz * 0.22),
            skin,
            28,
            18,
        ))

    # Hairline curls conceal the join without covering the modeled face.
    curl_radius = frame["height"] * 0.034 * config["curl_scale"]
    for index in range(11):
        angle = math.radians(18.0 + index * 14.4)
        x = center.x + math.cos(angle) * rx * 1.04
        z = center.z + math.sin(angle) * rz * 1.03
        y = center.y + ry * (0.02 + 0.10 * math.sin(angle))
        created.append(ellipsoid(
            f"{character}_HairlineCurl_{index}",
            (x, y, z),
            (curl_radius, curl_radius * 0.88, curl_radius * 1.02),
            hair,
            28,
            20,
        ))
    for side in (-1, 1):
        for row in range(4):
            created.append(ellipsoid(
                f"{character}_TempleCurl_{side}_{row}",
                (
                    center.x + side * rx * (1.03 + row * 0.03),
                    center.y + ry * (0.08 + row * 0.08),
                    center.z + rz * (0.45 - row * 0.34),
                ),
                (curl_radius * 0.95, curl_radius * 0.88, curl_radius * 1.06),
                hair,
                26,
                18,
            ))

    if config["headband"] is not None:
        headband = make_material(f"{character}_Headband", config["headband"], 0.62)
        created.append(torus(
            f"{character}_Headband",
            (center.x, center.y + ry * 0.08, center.z + rz * 0.52),
            rx * 0.94,
            frame["height"] * 0.0105,
            headband,
            rotation=(math.radians(90.0), 0.0, 0.0),
            scale=(1.0, 1.0, 0.76),
        ))

    return created


def height_thickness(frame, fraction):
    return max(frame["height"] * fraction, 0.0008)


def export_scene(path: pathlib.Path):
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError("No meshes remained for hybrid export")
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
        raise RuntimeError(f"Hybridizer did not export a non-empty GLB: {path}")
    return meshes


def main() -> int:
    args = parse_args()
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    report_path = output_root / "head-hybridization-report.json"
    output_path = output_root / f"{args.character}_hybrid.glb"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "input": args.input,
        "output": str(output_path),
        "method": "modeled tapered 3D head over preserved generated body and outfit",
        "success": False,
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        before_min, before_max = world_bounds(meshes)
        frame = estimate_face_frame(meshes)
        removed, removal_reports = remove_failed_face(meshes, frame)
        created = add_modeled_head(args.character, frame)
        exported = export_scene(output_path)
        after_min, after_max = world_bounds(exported)
        report.update({
            "success": True,
            "inputBounds": {"minimum": list(before_min), "maximum": list(before_max)},
            "outputBounds": {"minimum": list(after_min), "maximum": list(after_max)},
            "faceFrame": {
                "center": list(frame["center"]),
                "radii": list(frame["radii"]),
                "faceSurfaceY": frame["faceSurfaceY"],
                "upperPointCount": frame["upperPointCount"],
            },
            "failedFaceVerticesRemoved": removed,
            "removalReports": removal_reports,
            "modeledObjectsCreated": len(created),
            "exportedMeshObjects": len(exported),
            "outputBytes": output_path.stat().st_size,
        })
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
