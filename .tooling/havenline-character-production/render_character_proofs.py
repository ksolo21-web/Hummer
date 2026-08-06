#!/usr/bin/env python3
"""Render four truthful review angles from the exported production GLB.

This runs after rigging/export so the review images prove the exact asset that will be
inspected and later integrated into Unity, rather than a separate pre-export scene.
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


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("Production GLB contains no measurable mesh bounds")
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


def point_camera(camera, target: Vector) -> None:
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()


def add_area_light(name: str, location, energy: float, size: float, target: Vector):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.size = size
    light = bpy.data.objects.new(name, data)
    light.location = location
    bpy.context.collection.objects.link(light)
    light.rotation_euler = (target - light.location).to_track_quat("-Z", "Y").to_euler()
    return light


def configure_scene(center: Vector, size: Vector):
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

    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.055, 0.065, 0.085)

    camera_data = bpy.data.cameras.new("ProductionProofCamera")
    camera = bpy.data.objects.new("ProductionProofCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(size.z * 1.22, size.x * 1.45, 2.05)
    camera_data.lens = 55
    scene.camera = camera

    radius = max(size.x, size.y, size.z, 1.0) * 3.25
    target = Vector((center.x, center.y, center.z + size.z * 0.015))
    add_area_light(
        "ProductionKey",
        (center.x - radius * 0.75, center.y - radius * 0.85, center.z + radius * 0.80),
        1050,
        4.5,
        target,
    )
    add_area_light(
        "ProductionFill",
        (center.x + radius * 0.75, center.y - radius * 0.20, center.z + radius * 0.35),
        500,
        3.5,
        target,
    )
    add_area_light(
        "ProductionRim",
        (center.x, center.y + radius * 0.80, center.z + radius * 0.55),
        650,
        3.0,
        target,
    )
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
            target.z,
        )
        point_camera(camera, target)
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
        "schemaVersion": 1,
        "character": args.character,
        "sourceAsset": args.input,
        "success": False,
    }
    try:
        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(pathlib.Path(args.input)))
        meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        if not meshes:
            raise RuntimeError("Exported production GLB contains no mesh objects")
        minimum, maximum = world_bounds(meshes)
        center = (minimum + maximum) * 0.5
        size = maximum - minimum
        scene, camera, target, radius = configure_scene(center, size)
        proofs = render_angles(root, scene, camera, target, radius)
        report.update(
            success=True,
            meshObjects=len(meshes),
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
