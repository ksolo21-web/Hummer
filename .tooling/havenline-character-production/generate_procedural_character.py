#!/usr/bin/env python3
"""Author clean, rounded HAVENLINE expedition characters without hosted AI quota.

The four models are deterministic interpretations of the approved turnaround sheets.
They share a mobile-friendly art direction while preserving each character's defining
face, hair, eyewear, age, winter outfit and equipment. Outputs remain unapproved until
rendered proofs pass both automated gates and human visual review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
import random
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
    parser.add_argument("--reference")
    parser.add_argument("--reason", default="deterministic approved-reference rebuild")
    return parser.parse_args(args_after_separator())


CONFIGS = {
    "Character1": {
        "adult": True,
        "body": 1.04,
        "skin": (0.31, 0.145, 0.070),
        "hair": (0.025, 0.018, 0.015),
        "hair_style": "short",
        "glasses": True,
        "beard": True,
        "hoops": False,
        "headband": False,
        "scarf": True,
        "open_jacket": False,
        "shirt": (0.045, 0.060, 0.095),
    },
    "Character2": {
        "adult": True,
        "body": 0.98,
        "skin": (0.36, 0.180, 0.095),
        "hair": (0.055, 0.033, 0.022),
        "hair_style": "side_bob",
        "glasses": True,
        "beard": False,
        "hoops": True,
        "headband": False,
        "scarf": True,
        "open_jacket": False,
        "shirt": (0.055, 0.070, 0.110),
    },
    "Character3": {
        "adult": False,
        "body": 0.91,
        "skin": (0.28, 0.125, 0.060),
        "hair": (0.022, 0.015, 0.013),
        "hair_style": "curl_bob",
        "glasses": False,
        "beard": False,
        "hoops": False,
        "headband": False,
        "scarf": False,
        "open_jacket": True,
        "shirt": (0.30, 0.045, 0.060),
    },
    "Character4": {
        "adult": False,
        "body": 0.92,
        "skin": (0.36, 0.175, 0.080),
        "hair": (0.040, 0.024, 0.018),
        "hair_style": "curly_pony",
        "glasses": False,
        "beard": False,
        "hoops": True,
        "headband": True,
        "scarf": True,
        "open_jacket": False,
        "shirt": (0.045, 0.055, 0.085),
    },
}

COLORS = {
    "blue": (0.035, 0.185, 0.390),
    "blue_dark": (0.020, 0.075, 0.175),
    "orange": (0.88, 0.205, 0.045),
    "cream": (0.89, 0.835, 0.720),
    "pants": (0.040, 0.055, 0.105),
    "knee": (0.135, 0.145, 0.175),
    "leather": (0.255, 0.105, 0.038),
    "leather_dark": (0.080, 0.035, 0.018),
    "metal": (0.42, 0.46, 0.52),
    "gold": (0.74, 0.42, 0.075),
    "white": (0.94, 0.93, 0.88),
    "iris": (0.105, 0.045, 0.018),
    "black": (0.006, 0.007, 0.010),
    "mouth": (0.30, 0.045, 0.050),
}


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def textured_material(name: str, color, root: pathlib.Path, roughness=0.64, metallic=0.0):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = (*color, 1.0)
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    principled = nodes.get("Principled BSDF")
    principled.inputs["Roughness"].default_value = roughness
    principled.inputs["Metallic"].default_value = metallic

    image = bpy.data.images.new(f"{name}_Texture", width=8, height=8, alpha=True)
    rng = random.Random(hashlib.sha256(name.encode()).digest())
    pixels: list[float] = []
    for _ in range(64):
        variation = rng.uniform(-0.025, 0.025)
        pixels.extend(
            [
                max(0.0, min(1.0, color[0] + variation)),
                max(0.0, min(1.0, color[1] + variation)),
                max(0.0, min(1.0, color[2] + variation)),
                1.0,
            ]
        )
    image.pixels = pixels
    texture_path = root / f"{name.lower().replace(' ', '_')}.png"
    image.filepath_raw = str(texture_path)
    image.file_format = "PNG"
    image.save()
    image.pack()
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = image
    texture.interpolation = "Linear"
    links.new(texture.outputs["Color"], principled.inputs["Base Color"])
    return material


def apply_material(obj, material) -> None:
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = True


def apply_scale(obj) -> None:
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.select_set(False)


def ellipsoid(name, location, scale, material, segments=32, rings=20):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def rounded_box(name, location, dimensions, material, bevel=0.045, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dimensions
    apply_scale(obj)
    modifier = obj.modifiers.new("Rounded", "BEVEL")
    modifier.width = min(bevel, min(dimensions) * 0.34)
    modifier.segments = 4
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    obj.select_set(False)
    apply_material(obj, material)
    return obj


def cylinder_between(name, start, end, radius, material, vertices=28):
    start_v = Vector(start)
    end_v = Vector(end)
    direction = end_v - start_v
    bpy.ops.mesh.primitive_cylinder_add(
        vertices=vertices,
        radius=radius,
        depth=direction.length,
        location=(start_v + end_v) * 0.5,
    )
    obj = bpy.context.object
    obj.name = name
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = direction.to_track_quat("Z", "Y")
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def capsule(name, start, end, radius, material, end_scale=1.0):
    cylinder_between(f"{name}_Core", start, end, radius, material)
    ellipsoid(f"{name}_A", start, (radius, radius, radius * end_scale), material, 24, 16)
    ellipsoid(f"{name}_B", end, (radius, radius, radius * end_scale), material, 24, 16)


def torus(name, location, major_radius, minor_radius, material, rotation=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(
        major_segments=32,
        minor_segments=10,
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


def curve_mesh(name, points, material, thickness=0.009):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 3
    curve.bevel_depth = thickness
    curve.bevel_resolution = 3
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
    return obj


def build_materials(character: str, config, root: pathlib.Path):
    return {
        "skin": textured_material(f"{character}_Skin", config["skin"], root, 0.56),
        "hair": textured_material(f"{character}_Hair", config["hair"], root, 0.77),
        "blue": textured_material(f"{character}_ParkaBlue", COLORS["blue"], root, 0.62),
        "blue_dark": textured_material(f"{character}_ParkaDark", COLORS["blue_dark"], root, 0.68),
        "orange": textured_material(f"{character}_OrangeTrim", COLORS["orange"], root, 0.58),
        "cream": textured_material(f"{character}_Fleece", COLORS["cream"], root, 0.86),
        "pants": textured_material(f"{character}_Pants", COLORS["pants"], root, 0.74),
        "knee": textured_material(f"{character}_Knee", COLORS["knee"], root, 0.68),
        "leather": textured_material(f"{character}_Leather", COLORS["leather"], root, 0.70),
        "leather_dark": textured_material(f"{character}_LeatherDark", COLORS["leather_dark"], root, 0.76),
        "metal": textured_material(f"{character}_Metal", COLORS["metal"], root, 0.30, 0.62),
        "gold": textured_material(f"{character}_Gold", COLORS["gold"], root, 0.34, 0.48),
        "white": textured_material(f"{character}_EyeWhite", COLORS["white"], root, 0.45),
        "iris": textured_material(f"{character}_Iris", COLORS["iris"], root, 0.44),
        "black": textured_material(f"{character}_Black", COLORS["black"], root, 0.47),
        "mouth": textured_material(f"{character}_Mouth", COLORS["mouth"], root, 0.55),
        "shirt": textured_material(f"{character}_Shirt", config["shirt"], root, 0.72),
    }


def build_face(config, materials, head_center, head_scale):
    skin = materials["skin"]
    hair = materials["hair"]
    front_y = head_center[1] - head_scale[1] * 0.94
    eye_z = head_center[2] + head_scale[2] * 0.08
    eye_x = head_scale[0] * 0.37

    for side in (-1, 1):
        x = head_center[0] + side * eye_x
        ellipsoid(f"EyeWhite_{side}", (x, front_y, eye_z), (0.047, 0.019, 0.036), materials["white"], 28, 18)
        ellipsoid(f"Iris_{side}", (x, front_y - 0.018, eye_z), (0.022, 0.010, 0.024), materials["iris"], 24, 16)
        ellipsoid(f"Pupil_{side}", (x, front_y - 0.027, eye_z), (0.010, 0.006, 0.012), materials["black"], 20, 14)
        curve_mesh(
            f"Brow_{side}",
            [
                (x - 0.043, front_y - 0.020, eye_z + 0.076),
                (x, front_y - 0.031, eye_z + 0.086),
                (x + 0.043, front_y - 0.020, eye_z + 0.078),
            ],
            hair,
            0.008,
        )

    ellipsoid("Nose", (0, front_y - 0.025, head_center[2] - 0.025), (0.028, 0.023, 0.038), skin, 24, 16)
    curve_mesh(
        "Smile",
        [
            (-0.050, front_y - 0.032, head_center[2] - 0.105),
            (0, front_y - 0.039, head_center[2] - 0.118),
            (0.050, front_y - 0.032, head_center[2] - 0.105),
        ],
        materials["mouth"],
        0.008,
    )
    for side in (-1, 1):
        ellipsoid(f"Ear_{side}", (side * head_scale[0] * 0.96, head_center[1], head_center[2]), (0.033, 0.025, 0.055), skin, 22, 14)

    if config["glasses"]:
        for side in (-1, 1):
            torus(
                f"Glasses_{side}",
                (side * eye_x, front_y - 0.035, eye_z),
                0.058,
                0.007,
                materials["black"],
                rotation=(math.radians(90), 0, 0),
                scale=(1.05, 1.0, 0.88),
            )
        curve_mesh("GlassesBridge", [(-0.025, front_y - 0.041, eye_z), (0, front_y - 0.045, eye_z + 0.003), (0.025, front_y - 0.041, eye_z)], materials["black"], 0.006)

    if config["hoops"]:
        for side in (-1, 1):
            torus(
                f"Hoop_{side}",
                (side * head_scale[0] * 1.03, head_center[1] - 0.012, head_center[2] - 0.075),
                0.035,
                0.006,
                materials["gold"],
                rotation=(math.radians(90), 0, 0),
            )

    if config["beard"]:
        ellipsoid("BeardChin", (0, front_y + 0.015, head_center[2] - 0.125), (0.155, 0.050, 0.105), hair, 30, 18)
        for side in (-1, 1):
            ellipsoid(f"BeardJaw_{side}", (side * 0.132, front_y + 0.022, head_center[2] - 0.070), (0.060, 0.040, 0.105), hair, 24, 16)
        curve_mesh("BeardSmile", [(-0.045, front_y - 0.042, head_center[2] - 0.105), (0, front_y - 0.046, head_center[2] - 0.116), (0.045, front_y - 0.042, head_center[2] - 0.105)], materials["mouth"], 0.007)


def build_hair(config, materials, head_center, head_scale):
    hair = materials["hair"]
    style = config["hair_style"]
    if style == "short":
        ellipsoid("HairCap", (0, head_center[1] + 0.030, head_center[2] + 0.155), (head_scale[0] * 0.98, head_scale[1] * 0.92, 0.130), hair, 32, 20)
        for ring, count in ((0, 10), (1, 8)):
            for index in range(count):
                angle = math.tau * index / count + ring * 0.21
                ellipsoid(
                    f"ShortCurl_{ring}_{index}",
                    (math.cos(angle) * head_scale[0] * (0.76 - ring * 0.08), head_center[1] + math.sin(angle) * head_scale[1] * 0.68, head_center[2] + 0.245 - ring * 0.055),
                    (0.045, 0.040, 0.038),
                    hair,
                    20,
                    14,
                )
    elif style == "side_bob":
        ellipsoid("BobBack", (0, head_center[1] + 0.105, head_center[2] + 0.035), (head_scale[0] * 1.06, 0.145, 0.260), hair, 34, 22)
        for side in (-1, 1):
            for row in range(4):
                ellipsoid(
                    f"BobLock_{side}_{row}",
                    (side * (0.190 + row * 0.010), head_center[1] - 0.015 + row * 0.035, head_center[2] + 0.150 - row * 0.095),
                    (0.075, 0.060, 0.105),
                    hair,
                    24,
                    16,
                )
        ellipsoid("SideSweep", (-0.105, head_center[1] - 0.155, head_center[2] + 0.145), (0.150, 0.040, 0.165), hair, 28, 18)
    elif style == "curl_bob":
        ellipsoid("CurlBobBase", (0, head_center[1] + 0.105, head_center[2] + 0.020), (head_scale[0] * 1.08, 0.145, 0.245), hair, 32, 20)
        placements = []
        for row, z_offset in enumerate((0.225, 0.140, 0.055, -0.035, -0.120)):
            count = 10 if row < 3 else 8
            for index in range(count):
                angle = math.radians(25 + index * (310 / max(1, count - 1)))
                if math.sin(angle) < -0.60 and abs(math.cos(angle)) < 0.42:
                    continue
                placements.append((math.cos(angle) * head_scale[0] * 0.96, head_center[1] + math.sin(angle) * head_scale[1] * 0.78 + 0.045, head_center[2] + z_offset))
        for index, location in enumerate(placements):
            size = 0.052 + (index % 3) * 0.004
            ellipsoid(f"BobCurl_{index}", location, (size, size * 0.92, size * 1.04), hair, 20, 14)
    elif style == "curly_pony":
        ellipsoid("PonyHairCap", (0, head_center[1] + 0.035, head_center[2] + 0.150), (head_scale[0] * 1.02, head_scale[1] * 0.94, 0.150), hair, 32, 20)
        for side in (-1, 1):
            for row in range(3):
                ellipsoid(f"TempleCurl_{side}_{row}", (side * (0.195 + row * 0.008), head_center[1] - 0.055 + row * 0.035, head_center[2] + 0.105 - row * 0.080), (0.052, 0.047, 0.065), hair, 20, 14)
        pony_center = Vector((0, head_center[1] + 0.255, head_center[2] + 0.115))
        for ring, count in ((0, 10), (1, 8), (2, 6)):
            radius = 0.135 - ring * 0.028
            for index in range(count):
                angle = math.tau * index / count + ring * 0.18
                ellipsoid(f"PonyCurl_{ring}_{index}", pony_center + Vector((math.cos(angle) * radius, math.sin(angle) * radius * 0.58, (ring - 1) * 0.060)), (0.058, 0.054, 0.060), hair, 20, 14)
        if config["headband"]:
            torus("Headband", (0, head_center[1] - 0.005, head_center[2] + 0.155), head_scale[0] * 0.92, 0.014, materials["black"], rotation=(math.radians(90), 0, 0), scale=(1.0, 1.0, 0.78))


def build_character(character: str, root: pathlib.Path):
    if character not in CONFIGS:
        raise RuntimeError(f"No approved authored specification exists for {character}")
    config = CONFIGS[character]
    materials = build_materials(character, config, root)
    body = config["body"]
    adult = config["adult"]
    shoulder_x = 0.285 * body
    hip_x = 0.115 * body

    ellipsoid("ParkaTorso", (0, 0, 1.075), (0.305 * body, 0.205, 0.355), materials["blue"], 36, 24)
    ellipsoid("ParkaSkirt", (0, 0.005, 0.890), (0.300 * body, 0.205, 0.175), materials["blue"], 34, 22)
    rounded_box("OrangeHem", (0, -0.205, 0.855), (0.49 * body, 0.025, 0.042), materials["orange"], 0.012)
    if config["open_jacket"]:
        rounded_box("InnerVestLeft", (-0.095, -0.205, 1.105), (0.145, 0.026, 0.390), materials["cream"], 0.026)
        rounded_box("InnerVestRight", (0.095, -0.205, 1.105), (0.145, 0.026, 0.390), materials["cream"], 0.026)
        rounded_box("VisibleShirt", (0, -0.222, 1.115), (0.145, 0.020, 0.350), materials["shirt"], 0.018)
        rounded_box("OpeningLeft", (-0.184, -0.222, 1.110), (0.024, 0.018, 0.410), materials["orange"], 0.007)
        rounded_box("OpeningRight", (0.184, -0.222, 1.110), (0.024, 0.018, 0.410), materials["orange"], 0.007)
    else:
        rounded_box("JacketZip", (0, -0.215, 1.085), (0.030, 0.018, 0.480), materials["orange"], 0.007)

    for side in (-1, 1):
        shoulder = Vector((side * shoulder_x, 0, 1.325))
        elbow = Vector((side * 0.415 * body, -0.010, 1.070))
        wrist = Vector((side * 0.445 * body, -0.030, 0.825))
        capsule(f"UpperArm_{side}", shoulder, elbow, 0.102 * body, materials["blue"])
        capsule(f"LowerArm_{side}", elbow, wrist, 0.086 * body, materials["blue"])
        ellipsoid(f"ShoulderPatch_{side}", shoulder + Vector((0, -0.025, 0.005)), (0.120, 0.150, 0.090), materials["orange"], 26, 16)
        torus(f"FurCuff_{side}", wrist, 0.085, 0.024, materials["cream"], scale=(1.0, 1.0, 0.72))
        ellipsoid(f"Glove_{side}", wrist + Vector((0, -0.012, -0.115)), (0.085, 0.070, 0.112), materials["leather_dark"], 26, 18)

    rounded_box("BeltFront", (0, -0.218, 0.925), (0.52 * body, 0.035, 0.070), materials["leather"], 0.018)
    rounded_box("BeltBack", (0, 0.205, 0.925), (0.52 * body, 0.030, 0.065), materials["leather"], 0.016)
    rounded_box("Buckle", (0, -0.245, 0.925), (0.105, 0.025, 0.085), materials["metal"], 0.012)
    for side in (-1, 1):
        rounded_box(f"BeltPouch_{side}", (side * 0.235 * body, -0.225, 0.900), (0.120, 0.070, 0.135), materials["leather"], 0.027)
        capsule(f"PackStrap_{side}", (side * 0.205 * body, -0.145, 1.350), (side * 0.245 * body, -0.190, 1.030), 0.025, materials["leather"])

    for side in (-1, 1):
        hip = Vector((side * hip_x, 0, 0.790))
        knee = Vector((side * hip_x, 0, 0.485))
        ankle = Vector((side * hip_x, 0, 0.205))
        capsule(f"Thigh_{side}", hip, knee, 0.105 * body, materials["pants"])
        capsule(f"Shin_{side}", knee, ankle, 0.093 * body, materials["pants"])
        rounded_box(f"KneePad_{side}", (side * hip_x, -0.100, 0.490), (0.175, 0.055, 0.165), materials["knee"], 0.032)
        torus(f"BootFur_{side}", (side * hip_x, 0, 0.225), 0.098, 0.026, materials["cream"], scale=(1, 1, 0.72))
        rounded_box(f"Boot_{side}", (side * hip_x, -0.065, 0.105), (0.205, 0.300, 0.180), materials["leather"], 0.050)
        rounded_box(f"BootSole_{side}", (side * hip_x, -0.070, 0.028), (0.225, 0.325, 0.055), materials["leather_dark"], 0.018)
        for lace in range(3):
            curve_mesh(f"BootLace_{side}_{lace}", [(side * hip_x - 0.050, -0.220, 0.105 + lace * 0.035), (side * hip_x, -0.228, 0.112 + lace * 0.035), (side * hip_x + 0.050, -0.220, 0.105 + lace * 0.035)], materials["gold"], 0.005)

    rounded_box("Backpack", (0, 0.265, 1.100), (0.43 * body, 0.205, 0.530), materials["leather"], 0.070)
    rounded_box("BackpackFlap", (0, 0.382, 1.260), (0.38 * body, 0.045, 0.180), materials["leather_dark"], 0.032)
    rounded_box("PackBuckle", (0, 0.412, 1.235), (0.070, 0.025, 0.065), materials["gold"], 0.010)
    cylinder_between("Bedroll", (-0.235 * body, 0.355, 1.470), (0.235 * body, 0.355, 1.470), 0.098, materials["blue_dark"], 32)
    for x in (-0.125 * body, 0.125 * body):
        rounded_box(f"BedrollStrap_{x:+.2f}", (x, 0.435, 1.470), (0.040, 0.024, 0.210), materials["leather"], 0.010)
    cylinder_between("MetalCup", (0.315 * body, 0.355, 0.970), (0.315 * body, 0.355, 0.815), 0.050, materials["metal"], 24)

    head_z = 1.595 if adult else 1.585
    head_scale = (0.235, 0.205, 0.255) if adult else (0.248, 0.215, 0.267)
    head_center = Vector((0, -0.020, head_z))
    capsule("Neck", (0, 0, 1.340), (0, 0, 1.445), 0.092, materials["skin"])
    if config["scarf"]:
        torus("Scarf", (0, 0, 1.405), 0.145, 0.050, materials["blue_dark"], scale=(1.25, 0.92, 0.76))
    else:
        torus("FurCollar", (0, 0, 1.405), 0.180, 0.052, materials["cream"], scale=(1.28, 0.94, 0.76))
    ellipsoid("Head", head_center, head_scale, materials["skin"], 38, 26)
    build_face(config, materials, head_center, head_scale)
    build_hair(config, materials, head_center, head_scale)

    return config


def join_meshes(character: str):
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError("Authored character contains no mesh objects")
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.join()
    joined = bpy.context.object
    joined.name = f"{character}_AuthoredCharacter"
    for polygon in joined.data.polygons:
        polygon.use_smooth = True
    return joined


def export_glb(destination: pathlib.Path, character: str):
    joined = join_meshes(character)
    bpy.ops.object.select_all(action="DESELECT")
    joined.select_set(True)
    bpy.context.view_layer.objects.active = joined
    bpy.ops.export_scene.gltf(
        filepath=str(destination),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
        export_materials="EXPORT",
    )
    if not destination.is_file() or destination.stat().st_size == 0:
        raise RuntimeError(f"Blender did not export a non-empty authored GLB: {destination}")
    return joined


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    destination = root / f"{args.character}_raw.glb"
    report_path = root / "generation-report.json"
    reference = pathlib.Path(args.reference) if args.reference else None
    report = {
        "schemaVersion": 3,
        "character": args.character,
        "generator": "HAVENLINE rounded deterministic character authoring",
        "mode": "approved-reference-specific offline production",
        "fallbackReason": args.reason,
        "success": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        if reference:
            if not reference.is_file() or reference.stat().st_size == 0:
                raise FileNotFoundError(f"Approved reference is missing: {reference}")
            report.update(
                reference=str(reference),
                referenceBytes=reference.stat().st_size,
                referenceSha256=hashlib.sha256(reference.read_bytes()).hexdigest(),
            )
        clear_scene()
        config = build_character(args.character, root)
        joined = export_glb(destination, args.character)
        report.update(
            success=True,
            selectedGlb=str(destination),
            selectedBytes=destination.stat().st_size,
            vertices=len(joined.data.vertices),
            faces=len(joined.data.polygons),
            materialSlots=len(joined.data.materials),
            authoredTraits={
                "adult": config["adult"],
                "hairStyle": config["hair_style"],
                "glasses": config["glasses"],
                "beard": config["beard"],
                "hoops": config["hoops"],
                "headband": config["headband"],
                "scarf": config["scarf"],
                "openJacket": config["open_jacket"],
                "winterExpeditionWardrobe": True,
                "backpackBedrollAndCup": True,
                "roundedNonBlockySilhouette": True,
            },
            truthfulFallback=True,
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
