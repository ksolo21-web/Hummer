#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


def args_after_separator():
    values = sys.argv
    return values[values.index("--") + 1:] if "--" in values else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(args_after_separator())


def point_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def configure_scene():
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
    scene.frame_start = 1
    scene.frame_end = 1
    scene.frame_set(1)

    world = scene.world or bpy.data.worlds.new("HAVENLINE_ProofWorld")
    scene.world = world
    world.color = (0.055, 0.065, 0.085)
    return scene


def create_camera(scene):
    data = bpy.data.cameras.new("HAVENLINE_ProofCamera")
    camera = bpy.data.objects.new("HAVENLINE_ProofCamera", data)
    bpy.context.collection.objects.link(camera)
    data.type = "ORTHO"
    data.ortho_scale = 2.25
    scene.camera = camera
    return camera


def create_area_light(name, energy, size, location, target):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.size = size
    light = bpy.data.objects.new(name, data)
    light.location = location
    point_at(light, target)
    bpy.context.collection.objects.link(light)
    return light


def render_view(scene, camera, output_path, position, target):
    camera.location = position
    point_at(camera, target)
    bpy.context.view_layer.update()
    scene.render.filepath = str(output_path)
    bpy.ops.render.render(write_still=True)
    if not output_path.is_file() or output_path.stat().st_size == 0:
        raise RuntimeError(f"Renderer did not create a non-empty proof: {output_path}")
    payload = output_path.read_bytes()
    return {
        "path": str(output_path),
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "cameraPosition": [float(value) for value in position],
    }


def main():
    args = parse_args()
    source = pathlib.Path(args.source)
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    report_path = output / "proof-render-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "success": False,
        "sourceAsset": str(source),
        "views": [],
    }

    try:
        if not source.is_file() or source.stat().st_size == 0:
            raise RuntimeError(f"Missing final production GLB: {source}")

        clear_scene()
        bpy.ops.import_scene.gltf(filepath=str(source))
        meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        armatures = [obj for obj in bpy.context.scene.objects if obj.type == "ARMATURE"]
        if not meshes:
            raise RuntimeError("Final production GLB imported without a mesh")
        if not armatures:
            raise RuntimeError("Final production GLB imported without a humanoid armature")

        scene = configure_scene()
        target = (0.0, 0.0, 0.92)
        camera = create_camera(scene)
        create_area_light("HAVENLINE_Key", 1100, 5.0, (-3.0, -4.0, 6.0), target)
        create_area_light("HAVENLINE_Fill", 520, 4.0, (3.5, -2.0, 3.2), target)
        create_area_light("HAVENLINE_Rim", 700, 3.0, (1.0, 4.0, 5.0), target)

        radius = 5.0
        views = (
            ("front", (0.0, -radius, 0.92)),
            ("three-quarter", (radius / math.sqrt(2), -radius / math.sqrt(2), 0.92)),
            ("side", (radius, 0.0, 0.92)),
            ("back", (0.0, radius, 0.92)),
        )
        rendered = []
        for label, position in views:
            item = render_view(
                scene,
                camera,
                output / f"proof_{label}.png",
                position,
                target,
            )
            item["view"] = label
            rendered.append(item)

        hashes = [item["sha256"] for item in rendered]
        if len(set(hashes)) != len(hashes):
            raise RuntimeError("Proof renderer produced duplicate camera views")

        source_payload = source.read_bytes()
        report.update(
            success=True,
            sourceBytes=len(source_payload),
            sourceSha256=hashlib.sha256(source_payload).hexdigest(),
            importedMeshes=len(meshes),
            importedArmatures=len(armatures),
            uniqueViewCount=len(set(hashes)),
            views=rendered,
        )
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except Exception as exception:
        report.update(error=repr(exception), traceback=traceback.format_exc())
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        raise


if __name__ == "__main__":
    main()
