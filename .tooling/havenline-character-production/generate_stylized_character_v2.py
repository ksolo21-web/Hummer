#!/usr/bin/env python3
"""Author smooth, reference-aligned stylized Havenline expedition characters.

This deterministic path is used for Characters 3 and 4 when hosted multi-view
reconstruction is unavailable. It deliberately avoids block primitives as the main
anatomy: the head, coat, limbs, gloves, pants and boots are rounded sculpted forms,
with character-specific face, curl silhouette, headband/scarf details and equipment.
The result remains subject to human render approval before Unity integration.
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import random
import sys
import traceback

import bpy
from mathutils import Vector


def argv_after_separator() -> list[str]:
    return sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True, choices=("Character3", "Character4"))
    parser.add_argument("--output", required=True)
    parser.add_argument("--reason", default="authenticated reconstruction unavailable")
    return parser.parse_args(argv_after_separator())


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def texture_material(name: str, color, root: pathlib.Path, roughness=0.68, seed=0):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = (*color, 1.0)

    rng = random.Random(seed)
    width = height = 32
    pixels = []
    for y in range(height):
        for x in range(width):
            weave = 0.018 * math.sin(x * 1.55) + 0.012 * math.cos(y * 1.35)
            grain = rng.uniform(-0.018, 0.018)
            variation = weave + grain
            pixels.extend(
                [
                    max(0.0, min(1.0, color[0] + variation)),
                    max(0.0, min(1.0, color[1] + variation)),
                    max(0.0, min(1.0, color[2] + variation)),
                    1.0,
                ]
            )
    image = bpy.data.images.new(f"{name}_Texture", width=width, height=height, alpha=True)
    image.pixels.foreach_set(pixels)
    image.update()
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
    texture.interpolation = "Linear"
    links.new(texture.outputs["Color"], principled.inputs["Base Color"])
    principled.inputs["Roughness"].default_value = roughness
    return material


def apply_material(obj, material) -> None:
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = True


def add_ellipsoid(name, location, scale, material, segments=48, rings=32, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segments,
        ring_count=rings,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    apply_material(obj, material)
    return obj


def add_capsule(name, start, end, radius, material, width_factor=1.0):
    start = Vector(start)
    end = Vector(end)
    direction = end - start
    midpoint = (start + end) * 0.5
    obj = add_ellipsoid(
        name,
        midpoint,
        (radius * width_factor, radius, max(direction.length * 0.56, radius * 1.2)),
        material,
        segments=44,
        rings=28,
    )
    obj.rotation_euler = direction.to_track_quat("Z", "Y").to_euler()
    return obj


def add_rounded_box(name, location, scale, material, bevel=0.045, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    modifier = obj.modifiers.new("RoundedEdges", "BEVEL")
    modifier.width = min(bevel, min(scale) * 0.80)
    modifier.segments = 5
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    apply_material(obj, material)
    return obj


def add_torus(name, location, major_radius, minor_radius, material, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_torus_add(
        major_radius=major_radius,
        minor_radius=minor_radius,
        major_segments=56,
        minor_segments=16,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    apply_material(obj, material)
    return obj


def add_face(config, materials):
    center = Vector((0.0, -0.018, 1.625))
    sx, sy, sz = config["head_scale"]
    face_y = center.y - sy * 0.91
    eye_z = center.z + 0.035
    eye_x = sx * 0.39

    for side in (-1, 1):
        add_ellipsoid(
            f"EyeWhite_{side}",
            (side * eye_x, face_y - 0.012, eye_z),
            (0.054, 0.020, 0.046),
            materials["white"],
            segments=36,
            rings=22,
        )
        add_ellipsoid(
            f"Iris_{side}",
            (side * eye_x, face_y - 0.030, eye_z),
            (0.024, 0.011, 0.027),
            materials["eye"],
            segments=30,
            rings=20,
        )
        add_ellipsoid(
            f"EyeHighlight_{side}",
            (side * eye_x - 0.007, face_y - 0.040, eye_z + 0.010),
            (0.006, 0.004, 0.007),
            materials["white"],
            segments=20,
            rings=12,
        )
        brow = add_ellipsoid(
            f"Brow_{side}",
            (side * eye_x, face_y - 0.033, eye_z + 0.079),
            (0.062, 0.010, 0.012),
            materials["hair"],
            segments=28,
            rings=16,
        )
        brow.rotation_euler.z = math.radians(-7 * side)

    add_ellipsoid(
        "Nose",
        (0.0, face_y - 0.018, center.z - 0.040),
        (0.035, 0.027, 0.050),
        materials["skin"],
        segments=32,
        rings=20,
    )
    add_ellipsoid(
        "Smile",
        (0.0, face_y - 0.028, center.z - 0.132),
        (0.073, 0.010, 0.016),
        materials["mouth"],
        segments=32,
        rings=18,
    )
    add_ellipsoid(
        "LowerLip",
        (0.0, face_y - 0.024, center.z - 0.151),
        (0.050, 0.008, 0.010),
        materials["lip"],
        segments=28,
        rings=16,
    )

    for side in (-1, 1):
        add_ellipsoid(
            f"Ear_{side}",
            (side * sx * 0.98, center.y, center.z - 0.015),
            (0.035, 0.027, 0.060),
            materials["skin"],
            segments=30,
            rings=18,
        )
        if config.get("earrings"):
            add_torus(
                f"Earring_{side}",
                (side * sx * 1.02, center.y - 0.012, center.z - 0.085),
                0.032,
                0.006,
                materials["gold"],
                rotation=(math.radians(90), 0, 0),
            )


def add_hair(config, materials):
    center = Vector((0.0, 0.020, 1.655))
    sx, sy, sz = config["head_scale"]
    # Smooth cap establishes a continuous silhouette behind the face.
    add_ellipsoid(
        "HairCap",
        (center.x, center.y + 0.045, center.z + 0.055),
        (sx * 1.04, sy * 0.99, sz * 0.92),
        materials["hair"],
        segments=56,
        rings=38,
    )

    full = config["hair_style"] == "full_curls"
    layers = (
        (0.235, 16, 0.073 if full else 0.064, 1.03),
        (0.145, 18, 0.078 if full else 0.067, 1.05),
        (0.050, 18, 0.080 if full else 0.068, 1.04),
        (-0.050, 16, 0.079 if full else 0.066, 0.99),
        (-0.145, 14, 0.075 if full else 0.062, 0.92),
    )
    for layer_index, (z_offset, count, radius, radial) in enumerate(layers):
        for index in range(count):
            angle = (index / count) * math.tau + layer_index * 0.10
            # Keep the lower central forehead open; curls frame it from sides/back.
            if layer_index >= 2 and math.sin(angle) < -0.64 and abs(math.cos(angle)) < 0.70:
                continue
            x = math.cos(angle) * sx * radial
            y = center.y + math.sin(angle) * sy * radial + 0.040
            z = center.z + z_offset
            add_ellipsoid(
                f"Curl_{layer_index}_{index}",
                (x, y, z),
                (radius, radius * 0.92, radius * 1.04),
                materials["hair"],
                segments=28,
                rings=18,
            )

    if config.get("headband"):
        add_torus(
            "Headband",
            (0.0, 0.010, center.z + 0.115),
            sx * 1.015,
            0.018,
            materials["headband"],
            rotation=(math.radians(90), 0, 0),
        )


def build_character(character: str, root: pathlib.Path):
    configs = {
        "Character3": {
            "skin": (0.31, 0.145, 0.080),
            "hair": (0.027, 0.018, 0.016),
            "head_scale": (0.250, 0.220, 0.282),
            "hair_style": "compact_curls",
            "headband": False,
            "earrings": False,
            "scarf": False,
            "accent": (0.47, 0.075, 0.065),
            "body_width": 0.96,
        },
        "Character4": {
            "skin": (0.36, 0.175, 0.090),
            "hair": (0.045, 0.027, 0.019),
            "head_scale": (0.252, 0.222, 0.284),
            "hair_style": "full_curls",
            "headband": True,
            "earrings": True,
            "scarf": True,
            "accent": (0.055, 0.075, 0.120),
            "body_width": 0.99,
        },
    }
    config = configs[character]
    seed_base = 3100 if character == "Character3" else 4100
    materials = {
        "skin": texture_material(f"{character}_Skin", config["skin"], root, 0.74, seed_base + 1),
        "hair": texture_material(f"{character}_Hair", config["hair"], root, 0.82, seed_base + 2),
        "jacket": texture_material(f"{character}_JacketBlue", (0.035, 0.205, 0.46), root, 0.77, seed_base + 3),
        "orange": texture_material(f"{character}_OrangeTrim", (0.82, 0.22, 0.045), root, 0.68, seed_base + 4),
        "fleece": texture_material(f"{character}_Fleece", (0.90, 0.84, 0.73), root, 0.92, seed_base + 5),
        "pants": texture_material(f"{character}_Pants", (0.035, 0.060, 0.135), root, 0.78, seed_base + 6),
        "leather": texture_material(f"{character}_Leather", (0.235, 0.090, 0.032), root, 0.72, seed_base + 7),
        "dark": texture_material(f"{character}_Dark", (0.012, 0.017, 0.026), root, 0.76, seed_base + 8),
        "white": texture_material(f"{character}_White", (0.95, 0.95, 0.92), root, 0.55, seed_base + 9),
        "eye": texture_material(f"{character}_Eye", (0.18, 0.075, 0.025), root, 0.38, seed_base + 10),
        "mouth": texture_material(f"{character}_Mouth", (0.29, 0.030, 0.030), root, 0.55, seed_base + 11),
        "lip": texture_material(f"{character}_Lip", (0.48, 0.16, 0.13), root, 0.55, seed_base + 12),
        "metal": texture_material(f"{character}_Metal", (0.34, 0.37, 0.41), root, 0.34, seed_base + 13),
        "gold": texture_material(f"{character}_Gold", (0.76, 0.48, 0.10), root, 0.28, seed_base + 14),
        "headband": texture_material(f"{character}_Headband", config["accent"], root, 0.72, seed_base + 15),
    }

    width = config["body_width"]
    leg_x = 0.105 * width

    # Rounded boots and natural tapered legs.
    for side in (-1, 1):
        x = side * leg_x
        add_ellipsoid(f"BootUpper_{side}", (x, -0.010, 0.205), (0.105, 0.125, 0.155), materials["leather"])
        add_rounded_box(f"BootSole_{side}", (x, -0.055, 0.095), (0.112, 0.155, 0.050), materials["dark"], 0.035)
        add_capsule(f"LowerLeg_{side}", (x, 0, 0.300), (x, 0, 0.535), 0.091, materials["pants"], 0.92)
        add_capsule(f"UpperLeg_{side}", (x, 0, 0.520), (side * 0.095, 0, 0.805), 0.104, materials["pants"], 0.96)
        add_torus(f"BootFleece_{side}", (x, 0, 0.305), 0.098, 0.025, materials["fleece"])
        add_rounded_box(f"KneePad_{side}", (x, -0.090, 0.485), (0.074, 0.026, 0.076), materials["dark"], 0.025)

    # Smooth layered winter coat; no rectangular torso silhouette.
    add_ellipsoid("CoatUpper", (0, 0, 1.205), (0.305 * width, 0.225, 0.355), materials["jacket"], segments=64, rings=42)
    add_ellipsoid("CoatLower", (0, 0, 0.965), (0.315 * width, 0.232, 0.225), materials["jacket"], segments=60, rings=38)
    add_rounded_box("FrontZipper", (0, -0.231, 1.120), (0.022, 0.012, 0.335), materials["orange"], 0.010)
    add_rounded_box("LeftHemTrim", (-0.165, -0.214, 0.875), (0.145, 0.014, 0.020), materials["orange"], 0.010)
    add_rounded_box("RightHemTrim", (0.165, -0.214, 0.875), (0.145, 0.014, 0.020), materials["orange"], 0.010)
    add_torus("FleeceCollar", (0, 0, 1.435), 0.238, 0.047, materials["fleece"])
    add_torus("WaistBelt", (0, 0, 0.955), 0.293 * width, 0.028, materials["leather"])

    if config["scarf"]:
        add_torus("Scarf", (0, -0.010, 1.455), 0.195, 0.047, materials["headband"])

    # Relaxed, rounded sleeves and gloves.
    for side in (-1, 1):
        shoulder = (side * 0.245 * width, 0, 1.350)
        elbow = (side * 0.360 * width, -0.012, 1.105)
        wrist = (side * 0.405 * width, -0.030, 0.885)
        add_capsule(f"UpperArm_{side}", shoulder, elbow, 0.105, materials["jacket"], 1.02)
        add_capsule(f"LowerArm_{side}", elbow, wrist, 0.096, materials["jacket"], 0.98)
        add_ellipsoid(f"Glove_{side}", wrist, (0.080, 0.065, 0.100), materials["leather"], segments=38, rings=24)
        add_ellipsoid(f"Cuff_{side}", (wrist[0], wrist[1], wrist[2] + 0.082), (0.103, 0.086, 0.052), materials["fleece"], segments=40, rings=24)
        add_rounded_box(f"ShoulderPatch_{side}", (side * 0.267 * width, -0.185, 1.365), (0.070, 0.025, 0.070), materials["orange"], 0.028)

    # Head and facial identity.
    add_ellipsoid("Head", (0, -0.018, 1.625), config["head_scale"], materials["skin"], segments=64, rings=48)
    add_face(config, materials)
    add_hair(config, materials)

    # Expedition equipment with rounded profiles.
    add_rounded_box("Backpack", (0, 0.250, 1.105), (0.235 * width, 0.125, 0.315), materials["leather"], 0.060)
    for side in (-1, 1):
        add_capsule(
            f"PackStrap_{side}",
            (side * 0.175 * width, -0.165, 1.395),
            (side * 0.205 * width, -0.172, 1.020),
            0.026,
            materials["leather"],
            0.75,
        )
        add_rounded_box(f"BeltPouch_{side}", (side * 0.260 * width, -0.224, 0.975), (0.064, 0.038, 0.078), materials["leather"], 0.026)
    add_capsule("BedRoll", (-0.245, 0.330, 1.475), (0.245, 0.330, 1.475), 0.094, materials["jacket"], 1.0)
    add_ellipsoid("MetalCup", (0.304 * width, 0.300, 0.975), (0.060, 0.060, 0.080), materials["metal"], segments=36, rings=22)

    return config


def join_by_material():
    groups = {}
    for obj in [item for item in bpy.context.scene.objects if item.type == "MESH"]:
        material_name = obj.data.materials[0].name if obj.data.materials else "Unassigned"
        groups.setdefault(material_name, []).append(obj)
    joined = []
    for material_name, objects in groups.items():
        bpy.ops.object.select_all(action="DESELECT")
        for obj in objects:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = objects[0]
        if len(objects) > 1:
            bpy.ops.object.join()
        active = bpy.context.view_layer.objects.active
        active.name = material_name.replace("_", "")
        joined.append(active)
    bpy.ops.object.select_all(action="DESELECT")
    return joined


def export_glb(destination: pathlib.Path, meshes) -> None:
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.export_scene.gltf(
        filepath=str(destination),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
    )
    bpy.ops.object.select_all(action="DESELECT")
    if not destination.is_file() or destination.stat().st_size == 0:
        raise RuntimeError(f"Blender did not export a non-empty GLB: {destination}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    destination = root / f"{args.character}_raw.glb"
    report_path = root / "generation-report.json"
    report = {
        "schemaVersion": 3,
        "character": args.character,
        "generator": "HAVENLINE smooth stylized character authoring v2",
        "mode": "procedural-reference-aligned",
        "fallbackReason": args.reason,
        "success": False,
        "truthfulFallback": True,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        config = build_character(args.character, root)
        meshes = join_by_material()
        export_glb(destination, meshes)
        report.update(
            success=True,
            selectedGlb=str(destination),
            selectedBytes=destination.stat().st_size,
            meshObjects=len(meshes),
            vertices=sum(len(obj.data.vertices) for obj in meshes),
            faces=sum(len(obj.data.polygons) for obj in meshes),
            authoredTraits={
                "smoothRoundedAnatomy": True,
                "largeStylizedHead": True,
                "hairStyle": config["hair_style"],
                "headband": config["headband"],
                "earrings": config["earrings"],
                "scarf": config["scarf"],
                "winterExpeditionWardrobe": True,
                "backpackBedRollAndCup": True,
            },
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
