#!/usr/bin/env python3
"""Render four bright, tightly framed proofs from the final production GLB.

Only substantive textured character meshes participate in framing. Tiny helper or
accidental geometry is hidden, and the camera is fitted to the actual renderable model.
"""

from __future__ import annotations

import argparse
import hashlib
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


def is_substantive_mesh(obj) -> bool:
    if obj.type != "MESH" or obj.data is None:
        return False
    if len(obj.data.polygons) < 24:
        return False
    return any(slot.material is not None for slot in obj.material_slots)


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("Production GLB contains no measurable textured character bounds")
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


def point_at(obj, target: Vector) -> None:
    obj.rotation_euler = (target - obj.location).to_track_quat("-Z", "Y").to_euler()


def add_area_light(name: str, location, energy: float, size: float, target: Vector, color):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.size = size
    data.color = color
    light = bpy.data.objects.new(name, data)
    light.location = location
    bpy.context.collection.objects.link(light)
    point_at(light, target)
    return light


def add_floor(minimum: Vector, maximum: Vector):
    width = max(maximum.x - minimum.x, maximum.y - minimum.y, 1.0) * 3.2
    bpy.ops.mesh.primitive_plane_add(size=width, location=(0, 0, minimum.z - 0.012))
    floor = bpy.context.object
    floor.name = "ProofFloor"
    material = bpy.data.materials.new("ProofFloorMaterial")
    material.diffuse_color = (0.105, 0.115, 0.135, 1.0)
    material.roughness = 0.92
    floor.data.materials.append(material)
    return floor


def configure_scene(center: Vector, size: Vector, minimum: Vector, maximum: Vector):
    scene = bpy.context.scene
    scene.frame_set(1)
    scene.render.engine = (
        "BLENDER_EEVEE_NEXT" if hasattr(bpy.types, "BLENDER_EEVEE_NEXT") else "BLENDER_EEVEE"
    )
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    try:
        scene.view_settings.look = "Medium High Contrast"
    except Exception:
        pass

    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.12, 0.13, 0.16)

    camera_data = bpy.data.cameras.new("ProductionProofCamera")
    camera = bpy.data.objects.new("ProductionProofCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(size.z * 1.12, size.x * 1.34, 1.86)
    camera_data.lens = 55
    scene.camera = camera

    radius = max(size.x, size.y, size.z, 1.0) * 3.0
    target = Vector((center.x, center.y, center.z + size.z * 0.015))
    add_area_light(
        "ProductionKey",
        (center.x - radius * 0.72, center.y - radius * 0.82, center.z + radius * 0.82),
        1750,
        4.6,
        target,
        (1.0, 0.88, 0.74),
    )
    add_area_light(
        "ProductionFill",
        (center.x + radius * 0.78, center.y - radius * 0.28, center.z + radius * 0.38),
        900,
        4.0,
        target,
        (0.62, 0.76, 1.0),
    )
    add_area_light(
        "ProductionRim",
        (center.x, center.y + radius * 0.88, center.z + radius * 0.62),
        1100,
        3.5,
        target,
        (0.82, 0.90, 1.0),
    )
    add_floor(minimum, maximum)
    return scene, camera, target, radius


def render_angles(root: pathlib.Path, scene, camera, target: Vector, radius: float):
    angles = {
        "front": -90.0,
        "three-quarter": -45.0,
        "side": 0.0,
        "back": 90.0,
    }
    results = []
    for label, degrees in angles.items():
        radians = math.radians(degrees)
        camera.location = (
            target.x + math.cos(radians) * radius,
            target.y + math.sin(radians) * radius,
            target.z + 0.02,
        )
        point_at(camera, target)
        destination = root / f"proof_{label}.png"
        scene.render.filepath = str(destination)
        bpy.ops.render.render(write_still=True)
        if not destination.is_file() or destination.stat().st_size == 0:
            raise RuntimeError(f"Blender did not write proof image: {destination}")
        results.append(
            {
                "label": label,
                "path": str(destination),
                "bytes": destination.stat().st_size,
                "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
                "cameraLocation": list(camera.location),
                "orthographicScale": camera.data.ortho_scale,
            }
        )
    if len({item["sha256"] for item in results}) != len(results):
        raise RuntimeError("Rendered proof angles are not visually distinct files")
    return results


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    report_path = root / "proof-render-report.json"
    report = {
        "schemaVersion": 2,
        "character": args.character,
        "sourceAsset": args.input,
        "success": False,
    }
    try:
        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(pathlib.Path(args.input)))
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        meshes = [obj for obj in all_meshes if is_substantive_mesh(obj)]
        if not meshes:
            raise RuntimeError("Exported production GLB contains no substantive textured mesh")
        ignored = []
        for obj in all_meshes:
            if obj not in meshes:
                obj.hide_render = True
                ignored.append(
                    {
                        "name": obj.name,
                        "vertices": len(obj.data.vertices),
                        "polygons": len(obj.data.polygons),
                    }
                )
        minimum, maximum = world_bounds(meshes)
        center = (minimum + maximum) * 0.5
        size = maximum - minimum
        scene, camera, target, radius = configure_scene(center, size, minimum, maximum)
        proofs = render_angles(root, scene, camera, target, radius)
        report.update(
            success=True,
            importedMeshObjects=len(all_meshes),
            renderableMeshObjects=len(meshes),
            ignoredHelperMeshes=ignored,
            bounds={
                "minimum": list(minimum),
                "maximum": list(maximum),
                "size": list(size),
            },
            proofs=proofs,
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
