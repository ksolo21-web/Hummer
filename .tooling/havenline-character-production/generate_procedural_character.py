#!/usr/bin/env python3
"""Build a truthful stylized 3D fallback character when hosted reconstruction is unavailable.

The fallback is not a placeholder cube. It authors a textured, multi-part expedition
character with character-specific proportions, hair, face details, winter clothing,
boots, gloves, backpack and survival roll, then exports a production-ready raw GLB for
the same rigging/animation/LOD/review pipeline.
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
    parser.add_argument("--output", required=True)
    parser.add_argument("--reason", default="hosted reconstruction unavailable")
    return parser.parse_args(args_after_separator())


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def solid_texture_material(name: str, color, root: pathlib.Path):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = (*color, 1.0)
    material.roughness = 0.72

    image = bpy.data.images.new(f"{name}_Texture", width=4, height=4, alpha=True)
    rgba = [float(color[0]), float(color[1]), float(color[2]), 1.0]
    image.pixels = rgba * 16
    texture_path = root / f"{name.lower().replace(' ', '_')}.png"
    image.filepath_raw = str(texture_path)
    image.file_format = "PNG"
    image.save()
    image.pack()

    nodes = material.node_tree.nodes
    links = material.node_tree.links
    principled = nodes.get("Principled BSDF")
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = image
    texture.interpolation = "Closest"
    links.new(texture.outputs["Color"], principled.inputs["Base Color"])
    principled.inputs["Roughness"].default_value = 0.72
    return material


def apply_material(obj, material):
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0


def smooth(obj):
    if obj.type == "MESH":
        for polygon in obj.data.polygons:
            polygon.use_smooth = True


def add_uv_sphere(name, location, scale, material, segments=48, rings=32):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segments,
        ring_count=rings,
        location=location,
    )
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    smooth(obj)
    apply_material(obj, material)
    return obj


def add_cube(name, location, scale, material, bevel=0.04):
    bpy.ops.mesh.primitive_cube_add(location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    bevel_modifier = obj.modifiers.new("SoftEdges", "BEVEL")
    bevel_modifier.width = bevel
    bevel_modifier.segments = 3
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier=bevel_modifier.name)
    smooth(obj)
    apply_material(obj, material)
    return obj


def add_cylinder(name, location, radius, depth, material, rotation=(0, 0, 0), vertices=40):
    bpy.ops.mesh.primitive_cylinder_add(
        vertices=vertices,
        radius=radius,
        depth=depth,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    smooth(obj)
    apply_material(obj, material)
    return obj


def add_cylinder_between(name, start, end, radius, material, vertices=40):
    start = Vector(start)
    end = Vector(end)
    direction = end - start
    midpoint = (start + end) * 0.5
    obj = add_cylinder(
        name,
        midpoint,
        radius,
        direction.length,
        material,
        vertices=vertices,
    )
    obj.rotation_euler = direction.to_track_quat("Z", "Y").to_euler()
    return obj


def add_torus(name, location, major_radius, minor_radius, material, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_torus_add(
        major_radius=major_radius,
        minor_radius=minor_radius,
        major_segments=48,
        minor_segments=12,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    smooth(obj)
    apply_material(obj, material)
    return obj


def add_face(head_center, head_scale, materials, config):
    skin = materials["skin"]
    white = materials["white"]
    dark = materials["dark"]
    mouth = materials["mouth"]
    face_y = head_center[1] - head_scale[1] * 0.92
    eye_z = head_center[2] + head_scale[2] * 0.10
    eye_x = head_scale[0] * 0.42

    for side in (-1, 1):
        add_uv_sphere(
            f"EyeWhite_{side}",
            (head_center[0] + side * eye_x, face_y, eye_z),
            (0.060, 0.024, 0.052),
            white,
            segments=32,
            rings=20,
        )
        add_uv_sphere(
            f"Pupil_{side}",
            (head_center[0] + side * eye_x, face_y - 0.022, eye_z),
            (0.025, 0.013, 0.028),
            dark,
            segments=24,
            rings=16,
        )

    add_uv_sphere(
        "Nose",
        (head_center[0], face_y - 0.018, head_center[2] - head_scale[2] * 0.10),
        (0.040, 0.030, 0.055),
        skin,
        segments=28,
        rings=18,
    )
    add_uv_sphere(
        "Smile",
        (head_center[0], face_y - 0.026, head_center[2] - head_scale[2] * 0.34),
        (0.085, 0.012, 0.022),
        mouth,
        segments=28,
        rings=16,
    )

    if config.get("glasses"):
        for side in (-1, 1):
            add_torus(
                f"GlassesLens_{side}",
                (head_center[0] + side * eye_x, face_y - 0.045, eye_z),
                0.080,
                0.010,
                dark,
                rotation=(math.radians(90), 0, 0),
            )
        add_cube(
            "GlassesBridge",
            (head_center[0], face_y - 0.052, eye_z),
            (0.045, 0.008, 0.009),
            dark,
            bevel=0.006,
        )

    if config.get("beard"):
        add_uv_sphere(
            "Beard",
            (head_center[0], face_y + 0.018, head_center[2] - head_scale[2] * 0.31),
            (head_scale[0] * 0.67, head_scale[1] * 0.20, head_scale[2] * 0.38),
            materials["hair"],
            segments=48,
            rings=28,
        )
        # Re-add the mouth in front of the beard.
        add_uv_sphere(
            "BeardSmile",
            (head_center[0], face_y - 0.034, head_center[2] - head_scale[2] * 0.31),
            (0.080, 0.012, 0.020),
            mouth,
            segments=28,
            rings=16,
        )


def add_hair(head_center, head_scale, materials, style):
    hair = materials["hair"]
    if style == "short":
        for row_z, count, radius in ((0.19, 9, 0.060), (0.25, 8, 0.055), (0.31, 6, 0.052)):
            for index in range(count):
                angle = (index / count) * math.tau
                add_uv_sphere(
                    f"ShortHair_{row_z}_{index}",
                    (
                        head_center[0] + math.cos(angle) * head_scale[0] * 0.76,
                        head_center[1] + math.sin(angle) * head_scale[1] * 0.74,
                        head_center[2] + row_z,
                    ),
                    (radius, radius, radius),
                    hair,
                    segments=24,
                    rings=16,
                )
        return

    if style == "bob":
        for layer, z_offset in enumerate((0.28, 0.18, 0.08, -0.03, -0.13)):
            count = 13 if layer < 3 else 11
            for index in range(count):
                angle = math.radians(25 + index * (310 / max(count - 1, 1)))
                x = head_center[0] + math.cos(angle) * head_scale[0] * 0.92
                y = head_center[1] + math.sin(angle) * head_scale[1] * 0.88 + 0.035
                z = head_center[2] + z_offset
                scale = (0.085, 0.070, 0.105 if layer > 1 else 0.085)
                add_uv_sphere(
                    f"BobHair_{layer}_{index}",
                    (x, y, z),
                    scale,
                    hair,
                    segments=24,
                    rings=16,
                )
        return

    # Dense curls for Characters 3 and 4.
    for layer, z_offset in enumerate((0.29, 0.22, 0.14, 0.05, -0.04, -0.13)):
        count = 15 if layer < 4 else 13
        radius_factor = 0.88 if layer < 4 else 0.82
        for index in range(count):
            angle = (index / count) * math.tau + layer * 0.16
            x = head_center[0] + math.cos(angle) * head_scale[0] * radius_factor
            y = head_center[1] + math.sin(angle) * head_scale[1] * radius_factor + 0.055
            z = head_center[2] + z_offset
            curl = 0.067 if style == "curly_compact" else 0.076
            add_uv_sphere(
                f"Curl_{layer}_{index}",
                (x, y, z),
                (curl, curl, curl),
                hair,
                segments=24,
                rings=16,
            )


def build_character(character: str, root: pathlib.Path):
    configs = {
        "Character1": {
            "adult": True,
            "body": 1.08,
            "head": (0.245, 0.225, 0.285),
            "skin": (0.31, 0.145, 0.075),
            "hair": (0.035, 0.025, 0.020),
            "hair_style": "short",
            "glasses": True,
            "beard": True,
            "headband": False,
        },
        "Character2": {
            "adult": True,
            "body": 0.98,
            "head": (0.235, 0.215, 0.280),
            "skin": (0.34, 0.17, 0.095),
            "hair": (0.055, 0.033, 0.023),
            "hair_style": "bob",
            "glasses": True,
            "beard": False,
            "headband": False,
        },
        "Character3": {
            "adult": False,
            "body": 0.88,
            "head": (0.255, 0.230, 0.295),
            "skin": (0.26, 0.115, 0.060),
            "hair": (0.030, 0.020, 0.018),
            "hair_style": "curly_compact",
            "glasses": False,
            "beard": False,
            "headband": False,
        },
        "Character4": {
            "adult": False,
            "body": 0.90,
            "head": (0.255, 0.230, 0.295),
            "skin": (0.31, 0.145, 0.070),
            "hair": (0.045, 0.028, 0.021),
            "hair_style": "curly_full",
            "glasses": False,
            "beard": False,
            "headband": True,
        },
    }
    if character not in configs:
        raise RuntimeError(f"No authored fallback specification exists for {character}")
    config = configs[character]

    materials = {
        "skin": solid_texture_material(f"{character}_Skin", config["skin"], root),
        "hair": solid_texture_material(f"{character}_Hair", config["hair"], root),
        "jacket": solid_texture_material(f"{character}_JacketBlue", (0.045, 0.23, 0.48), root),
        "orange": solid_texture_material(f"{character}_OrangeTrim", (0.82, 0.23, 0.055), root),
        "fleece": solid_texture_material(f"{character}_Fleece", (0.88, 0.82, 0.70), root),
        "pants": solid_texture_material(f"{character}_Pants", (0.045, 0.075, 0.16), root),
        "leather": solid_texture_material(f"{character}_Leather", (0.26, 0.105, 0.040), root),
        "dark": solid_texture_material(f"{character}_Dark", (0.012, 0.016, 0.025), root),
        "white": solid_texture_material(f"{character}_White", (0.94, 0.94, 0.91), root),
        "mouth": solid_texture_material(f"{character}_Mouth", (0.42, 0.055, 0.050), root),
        "metal": solid_texture_material(f"{character}_Metal", (0.35, 0.38, 0.42), root),
    }

    body = config["body"]
    is_adult = config["adult"]
    leg_x = 0.115 * body
    shoulder_x = 0.235 * body
    head_z = 1.66 if is_adult else 1.63
    head_center = (0.0, -0.015, head_z)
    head_scale = config["head"]

    # Boots, legs and pants.
    for side in (-1, 1):
        x = side * leg_x
        add_cube(f"Boot_{side}", (x, -0.055, 0.13), (0.105, 0.155, 0.115), materials["leather"], 0.035)
        add_cylinder(f"LowerLeg_{side}", (x, 0.0, 0.39), 0.092 * body, 0.42, materials["pants"])
        add_cylinder(f"UpperLeg_{side}", (x, 0.0, 0.73), 0.105 * body, 0.34, materials["pants"])
        add_torus(f"BootFleece_{side}", (x, 0.0, 0.27), 0.100 * body, 0.030, materials["fleece"])

    # Winter coat, torso, hood and trim.
    add_uv_sphere(
        "CoatTorso",
        (0.0, 0.0, 1.12),
        (0.34 * body, 0.245 * body, 0.43),
        materials["jacket"],
        segments=64,
        rings=44,
    )
    add_cube("JacketFrontTrim", (0.0, -0.248 * body, 1.12), (0.030, 0.015, 0.39), materials["orange"], 0.010)
    add_torus("WaistBelt", (0.0, 0.0, 0.91), 0.305 * body, 0.035, materials["leather"])
    add_torus("FleeceCollar", (0.0, 0.0, 1.43), 0.255 * body, 0.055, materials["fleece"])

    # Arms, gloves and cuffs.
    for side in (-1, 1):
        shoulder = (side * shoulder_x, 0.0, 1.36)
        elbow = (side * 0.37 * body, -0.005, 1.10)
        wrist = (side * 0.43 * body, -0.025, 0.84)
        add_cylinder_between(f"UpperArm_{side}", shoulder, elbow, 0.105 * body, materials["jacket"])
        add_cylinder_between(f"LowerArm_{side}", elbow, wrist, 0.095 * body, materials["jacket"])
        add_uv_sphere(f"Glove_{side}", wrist, (0.085, 0.070, 0.105), materials["leather"], segments=32, rings=20)
        add_torus(f"Cuff_{side}", wrist, 0.092, 0.026, materials["fleece"])
        add_cube(f"ShoulderPatch_{side}", (side * 0.285 * body, -0.205 * body, 1.36), (0.085, 0.025, 0.075), materials["orange"], 0.025)

    # Head, face and character-specific hair.
    add_uv_sphere("Head", head_center, head_scale, materials["skin"], segments=64, rings=48)
    add_face(head_center, head_scale, materials, config)
    add_hair(head_center, head_scale, materials, config["hair_style"])
    if config.get("headband"):
        add_torus(
            "Headband",
            (head_center[0], head_center[1], head_center[2] + 0.16),
            head_scale[0] * 0.88,
            0.018,
            materials["dark"],
            rotation=(math.radians(90), 0, 0),
        )

    # Backpack, shoulder straps, bed roll and metal cup.
    add_cube("Backpack", (0.0, 0.255 * body, 1.13), (0.265 * body, 0.135, 0.36), materials["leather"], 0.055)
    for side in (-1, 1):
        add_cylinder_between(
            f"PackStrap_{side}",
            (side * 0.19 * body, -0.17, 1.40),
            (side * 0.22 * body, -0.17, 0.97),
            0.030,
            materials["leather"],
            vertices=24,
        )
    add_cylinder(
        "BedRoll",
        (0.0, 0.34 * body, 1.49),
        0.105,
        0.50 * body,
        materials["jacket"],
        rotation=(0, math.radians(90), 0),
        vertices=48,
    )
    add_cylinder(
        "MetalCup",
        (0.33 * body, 0.31 * body, 0.96),
        0.065,
        0.13,
        materials["metal"],
        vertices=32,
    )

    # Belt pouches and knee pads add survival readability.
    for side in (-1, 1):
        add_cube(f"BeltPouch_{side}", (side * 0.27 * body, -0.245 * body, 0.93), (0.075, 0.045, 0.090), materials["leather"], 0.025)
        add_cube(f"KneePad_{side}", (side * leg_x, -0.100, 0.51), (0.085, 0.030, 0.090), materials["dark"], 0.025)

    return config


def export_glb(destination: pathlib.Path):
    bpy.ops.object.select_all(action="DESELECT")
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.export_scene.gltf(
        filepath=str(destination),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
    )
    if not destination.is_file() or destination.stat().st_size == 0:
        raise RuntimeError(f"Blender did not export a non-empty fallback GLB: {destination}")
    return meshes


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    destination = root / f"{args.character}_raw.glb"
    report_path = root / "generation-report.json"
    prior_report = None
    if report_path.is_file():
        try:
            prior_report = json.loads(report_path.read_text(encoding="utf-8"))
        except Exception:
            prior_report = None
    report = {
        "schemaVersion": 2,
        "character": args.character,
        "generator": "HAVENLINE deterministic stylized fallback",
        "mode": "procedural-character-specific",
        "fallbackReason": args.reason,
        "remoteAttemptReport": prior_report,
        "success": False,
    }
    try:
        clear_scene()
        config = build_character(args.character, root)
        meshes = export_glb(destination)
        vertices = sum(len(obj.data.vertices) for obj in meshes)
        faces = sum(len(obj.data.polygons) for obj in meshes)
        report.update(
            success=True,
            selectedGlb=str(destination),
            selectedBytes=destination.stat().st_size,
            meshObjects=len(meshes),
            vertices=vertices,
            faces=faces,
            authoredTraits={
                "adult": config["adult"],
                "hairStyle": config["hair_style"],
                "glasses": config["glasses"],
                "beard": config["beard"],
                "headband": config["headband"],
                "winterExpeditionWardrobe": True,
                "backpackAndBedRoll": True,
            },
            truthfulFallback=True,
            humanVisualApprovalRequired=True,
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
