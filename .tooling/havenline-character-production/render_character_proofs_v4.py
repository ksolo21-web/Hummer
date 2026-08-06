#!/usr/bin/env python3
"""Render close neutral proofs from the exact exported production character.

Only meshes actually deformed by the imported humanoid armature participate in framing.
This excludes Blender/glTF helper geometry such as the unit sphere that previously made
the character occupy barely half of the proof. Animation is muted and the rest pose is
restored before evaluated-vertex bounds are measured.
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


def reset_neutral_pose(armatures) -> None:
    for armature in armatures:
        if armature.animation_data:
            armature.animation_data.action = None
            for track in armature.animation_data.nla_tracks:
                track.mute = True
        for bone in armature.pose.bones:
            bone.matrix_basis = Matrix.Identity(4)
    bpy.context.scene.frame_set(1)
    bpy.context.view_layer.update()


def is_skinned_character_mesh(obj, armatures) -> bool:
    if obj.type != "MESH" or obj.data is None or len(obj.data.vertices) < 100:
        return False
    if obj.parent in armatures:
        return True
    for modifier in obj.modifiers:
        if modifier.type == "ARMATURE" and modifier.object in armatures:
            return True
    return False


def evaluated_world_bounds(meshes):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    points = []
    evaluated_vertex_count = 0
    for obj in meshes:
        evaluated = obj.evaluated_get(depsgraph)
        evaluated_mesh = evaluated.to_mesh()
        try:
            matrix = evaluated.matrix_world
            for vertex in evaluated_mesh.vertices:
                points.append(matrix @ vertex.co)
            evaluated_vertex_count += len(evaluated_mesh.vertices)
        finally:
            evaluated.to_mesh_clear()
    if not points:
        raise RuntimeError("Production GLB contains no evaluated skinned vertices")
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
    return minimum, maximum, evaluated_vertex_count


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


def add_floor(minimum: Vector, size: Vector):
    floor_size = max(size.x, size.y, 1.0) * 3.0
    bpy.ops.mesh.primitive_plane_add(size=floor_size, location=(0, 0, minimum.z - 0.012))
    floor = bpy.context.object
    floor.name = "ReviewFloor"
    material = bpy.data.materials.new("ReviewFloorMaterial")
    material.diffuse_color = (0.095, 0.105, 0.130, 1.0)
    material.roughness = 0.94
    floor.data.materials.append(material)


def configure_scene(center: Vector, size: Vector, minimum: Vector):
    scene = bpy.context.scene
    engines = {
        item.identifier
        for item in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
    }
    if "BLENDER_EEVEE_NEXT" in engines:
        scene.render.engine = "BLENDER_EEVEE_NEXT"
    elif "BLENDER_EEVEE" in engines:
        scene.render.engine = "BLENDER_EEVEE"
    else:
        raise RuntimeError(f"No EEVEE render engine is available: {sorted(engines)}")

    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.view_settings.exposure = 0.55
    try:
        scene.view_settings.look = "AgX - Medium High Contrast"
    except Exception:
        try:
            scene.view_settings.look = "Medium High Contrast"
        except Exception:
            pass

    world = scene.world or bpy.data.worlds.new("HAVENLINE_ReviewWorld")
    scene.world = world
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    if background:
        background.inputs["Color"].default_value = (0.045, 0.055, 0.078, 1.0)
        background.inputs["Strength"].default_value = 0.44

    camera_data = bpy.data.cameras.new("HAVENLINE_ReviewCamera")
    camera = bpy.data.objects.new("HAVENLINE_ReviewCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.type = "ORTHO"
    # 89–91% occupancy on the largest silhouette dimension, with safe hair/boot margin.
    camera_data.ortho_scale = max(size.z * 1.105, size.x * 1.18, size.y * 1.18, 1.78)
    camera_data.lens = 58
    scene.camera = camera

    radius = max(size.x, size.y, size.z, 1.0) * 3.4
    target = Vector((center.x, center.y, center.z + size.z * 0.004))
    add_area_light(
        "ReviewKey",
        (center.x - radius * 0.70, center.y - radius * 0.82, center.z + radius * 0.78),
        1550,
        4.8,
        target,
        (1.0, 0.88, 0.76),
    )
    add_area_light(
        "ReviewFill",
        (center.x + radius * 0.78, center.y - radius * 0.18, center.z + radius * 0.36),
        820,
        4.0,
        target,
        (0.68, 0.80, 1.0),
    )
    add_area_light(
        "ReviewRim",
        (center.x + radius * 0.12, center.y + radius * 0.78, center.z + radius * 0.58),
        980,
        3.4,
        target,
        (0.82, 0.90, 1.0),
    )
    add_floor(minimum, size)
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
            target.z + 0.01,
        )
        point_at(camera, target)
        bpy.context.view_layer.update()
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
        "schemaVersion": 4,
        "character": args.character,
        "sourceAsset": args.input,
        "success": False,
    }
    try:
        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(pathlib.Path(args.input)))
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        armatures = [obj for obj in bpy.context.scene.objects if obj.type == "ARMATURE"]
        if not armatures:
            raise RuntimeError("Exported production GLB contains no humanoid armature")
        reset_neutral_pose(armatures)
        meshes = [obj for obj in all_meshes if is_skinned_character_mesh(obj, armatures)]
        if not meshes:
            raise RuntimeError("Exported production GLB contains no skinned character mesh")
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
        minimum, maximum, evaluated_vertices = evaluated_world_bounds(meshes)
        center = (minimum + maximum) * 0.5
        size = maximum - minimum
        scene, camera, target, radius = configure_scene(center, size, minimum)
        proofs = render_angles(root, scene, camera, target, radius)
        report.update(
            success=True,
            importedMeshObjects=len(all_meshes),
            skinnedCharacterMeshes=len(meshes),
            ignoredHelperMeshes=ignored,
            armatures=len(armatures),
            evaluatedVertices=evaluated_vertices,
            cameraOrthoScale=camera.data.ortho_scale,
            expectedVerticalOccupancy=min(1.0, size.z / camera.data.ortho_scale),
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
