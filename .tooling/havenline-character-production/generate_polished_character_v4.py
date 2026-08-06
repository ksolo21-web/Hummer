#!/usr/bin/env python3
"""Author smooth, reference-specific HAVENLINE expedition characters.

This offline production path deliberately avoids the segmented cylinder-and-box anatomy
that failed the previous visual review. Main body forms are overlapping, rotated UV
ellipsoids with softened transitions; hard-surface shapes are limited to equipment,
buckles, soles, and small clothing details. Every output remains pending human visual
approval before Unity integration.
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
    parser.add_argument("--reason", default="polished approved-reference rebuild")
    return parser.parse_args(args_after_separator())


CONFIGS = {
    "Character1": {
        "adult": True,
        "body": 1.03,
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
        "head_scale": (0.245, 0.218, 0.270),
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
        "head_scale": (0.242, 0.216, 0.270),
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
        "head_scale": (0.260, 0.228, 0.286),
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
        "head_scale": (0.260, 0.228, 0.286),
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
    "lip": (0.48, 0.15, 0.13),
}


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def textured_material(name: str, color, root: pathlib.Path, roughness=0.68, metallic=0.0):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = (*color, 1.0)
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    principled = nodes.get("Principled BSDF")
    principled.inputs["Roughness"].default_value = roughness
    principled.inputs["Metallic"].default_value = metallic

    width = height = 16
    rng = random.Random(hashlib.sha256(name.encode()).digest())
    pixels: list[float] = []
    for y in range(height):
        for x in range(width):
            weave = 0.012 * math.sin(x * 1.35) + 0.009 * math.cos(y * 1.55)
            variation = weave + rng.uniform(-0.015, 0.015)
            pixels.extend(
                [
                    max(0.0, min(1.0, color[0] + variation)),
                    max(0.0, min(1.0, color[1] + variation)),
                    max(0.0, min(1.0, color[2] + variation)),
                    1.0,
                ]
            )
    image = bpy.data.images.new(f"{name}_Texture", width=width, height=height, alpha=True)
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


def ellipsoid(name, location, scale, material, segments=40, rings=28, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segments,
        ring_count=rings,
        location=location,
        rotation=rotation,
    )
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def ellipsoid_between(name, start, end, radius, material, width=1.0, depth=0.96, overlap=1.12):
    start_v = Vector(start)
    end_v = Vector(end)
    direction = end_v - start_v
    length = direction.length
    midpoint = (start_v + end_v) * 0.5
    obj = ellipsoid(
        name,
        midpoint,
        (radius * width, radius * depth, max(length * 0.5 * overlap, radius * 1.30)),
        material,
        segments=44,
        rings=30,
    )
    obj.rotation_euler = direction.to_track_quat("Z", "Y").to_euler()
    return obj


def rounded_box(name, location, dimensions, material, bevel=0.04, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dimensions
    apply_scale(obj)
    modifier = obj.modifiers.new("Rounded", "BEVEL")
    modifier.width = min(bevel, min(dimensions) * 0.34)
    modifier.segments = 5
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    obj.select_set(False)
    apply_material(obj, material)
    return obj


def torus(name, location, major_radius, minor_radius, material, rotation=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(
        major_segments=44,
        minor_segments=12,
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


def curve_mesh(name, points, material, thickness=0.008):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 4
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
        "skin": textured_material(f"{character}_Skin", config["skin"], root, 0.58),
        "hair": textured_material(f"{character}_Hair", config["hair"], root, 0.80),
        "blue": textured_material(f"{character}_ParkaBlue", COLORS["blue"], root, 0.70),
        "blue_dark": textured_material(f"{character}_ParkaDark", COLORS["blue_dark"], root, 0.74),
        "orange": textured_material(f"{character}_OrangeTrim", COLORS["orange"], root, 0.66),
        "cream": textured_material(f"{character}_Fleece", COLORS["cream"], root, 0.90),
        "pants": textured_material(f"{character}_Pants", COLORS["pants"], root, 0.78),
        "knee": textured_material(f"{character}_Knee", COLORS["knee"], root, 0.72),
        "leather": textured_material(f"{character}_Leather", COLORS["leather"], root, 0.76),
        "leather_dark": textured_material(f"{character}_LeatherDark", COLORS["leather_dark"], root, 0.80),
        "metal": textured_material(f"{character}_Metal", COLORS["metal"], root, 0.35, 0.58),
        "gold": textured_material(f"{character}_Gold", COLORS["gold"], root, 0.35, 0.46),
        "white": textured_material(f"{character}_EyeWhite", COLORS["white"], root, 0.48),
        "iris": textured_material(f"{character}_Iris", COLORS["iris"], root, 0.45),
        "black": textured_material(f"{character}_Black", COLORS["black"], root, 0.48),
        "mouth": textured_material(f"{character}_Mouth", COLORS["mouth"], root, 0.58),
        "lip": textured_material(f"{character}_Lip", COLORS["lip"], root, 0.56),
        "shirt": textured_material(f"{character}_Shirt", config["shirt"], root, 0.76),
    }


def build_face(config, materials, head_center, head_scale):
    front_y = head_center.y - head_scale[1] * 0.95
    eye_z = head_center.z + head_scale[2] * 0.07
    eye_x = head_scale[0] * 0.38
    for side in (-1, 1):
        x = side * eye_x
        ellipsoid(f"EyeWhite_{side}", (x, front_y, eye_z), (0.050, 0.018, 0.039), materials["white"], 30, 20)
        ellipsoid(f"Iris_{side}", (x, front_y - 0.017, eye_z), (0.023, 0.009, 0.026), materials["iris"], 26, 18)
        ellipsoid(f"Pupil_{side}", (x, front_y - 0.025, eye_z), (0.010, 0.005, 0.012), materials["black"], 22, 16)
        ellipsoid(f"Highlight_{side}", (x - 0.006, front_y - 0.030, eye_z + 0.009), (0.005, 0.003, 0.006), materials["white"], 18, 12)
        curve_mesh(
            f"Brow_{side}",
            [(x - 0.043, front_y - 0.018, eye_z + 0.076), (x, front_y - 0.028, eye_z + 0.086), (x + 0.043, front_y - 0.018, eye_z + 0.078)],
            materials["hair"],
            0.007,
        )
    ellipsoid("Nose", (0, front_y - 0.023, head_center.z - 0.025), (0.029, 0.022, 0.039), materials["skin"], 26, 18)
    curve_mesh(
        "Smile",
        [(-0.052, front_y - 0.030, head_center.z - 0.108), (0, front_y - 0.038, head_center.z - 0.120), (0.052, front_y - 0.030, head_center.z - 0.108)],
        materials["mouth"],
        0.007,
    )
    ellipsoid("LowerLip", (0, front_y - 0.024, head_center.z - 0.135), (0.045, 0.007, 0.009), materials["lip"], 24, 16)
    for side in (-1, 1):
        ellipsoid(f"Ear_{side}", (side * head_scale[0] * 0.97, head_center.y, head_center.z - 0.005), (0.033, 0.024, 0.055), materials["skin"], 24, 16)

    if config["glasses"]:
        for side in (-1, 1):
            torus(
                f"Glasses_{side}",
                (side * eye_x, front_y - 0.033, eye_z),
                0.059,
                0.0065,
                materials["black"],
                rotation=(math.radians(90), 0, 0),
                scale=(1.04, 1.0, 0.88),
            )
        curve_mesh("GlassesBridge", [(-0.025, front_y - 0.040, eye_z), (0, front_y - 0.044, eye_z + 0.002), (0.025, front_y - 0.040, eye_z)], materials["black"], 0.0055)

    if config["hoops"]:
        for side in (-1, 1):
            torus(
                f"Hoop_{side}",
                (side * head_scale[0] * 1.03, head_center.y - 0.010, head_center.z - 0.078),
                0.032,
                0.0055,
                materials["gold"],
                rotation=(math.radians(90), 0, 0),
            )

    if config["beard"]:
        ellipsoid("BeardChin", (0, front_y + 0.012, head_center.z - 0.142), (0.135, 0.038, 0.082), materials["hair"], 34, 22)
        for side in (-1, 1):
            ellipsoid(f"BeardJaw_{side}", (side * 0.115, front_y + 0.016, head_center.z - 0.090), (0.052, 0.032, 0.078), materials["hair"], 28, 18)
        curve_mesh("Mustache", [(-0.050, front_y - 0.032, head_center.z - 0.085), (0, front_y - 0.038, head_center.z - 0.092), (0.050, front_y - 0.032, head_center.z - 0.085)], materials["hair"], 0.009)
        curve_mesh("BeardSmile", [(-0.043, front_y - 0.040, head_center.z - 0.118), (0, front_y - 0.044, head_center.z - 0.126), (0.043, front_y - 0.040, head_center.z - 0.118)], materials["mouth"], 0.006)


def add_hair(config, materials, head_center, head_scale):
    hair = materials["hair"]
    style = config["hair_style"]
    if style == "short":
        ellipsoid("HairCap", (0, head_center.y + 0.035, head_center.z + 0.150), (head_scale[0] * 0.98, head_scale[1] * 0.92, 0.125), hair, 42, 28)
        for ring, count, z, radius in ((0, 11, 0.245, 0.042), (1, 9, 0.202, 0.040)):
            for index in range(count):
                angle = math.tau * index / count + ring * 0.18
                ellipsoid(
                    f"ShortCurl_{ring}_{index}",
                    (math.cos(angle) * head_scale[0] * (0.77 - ring * 0.08), head_center.y + math.sin(angle) * head_scale[1] * 0.69, head_center.z + z),
                    (radius, radius * 0.94, radius * 0.92),
                    hair,
                    22,
                    16,
                )
    elif style == "side_bob":
        ellipsoid("BobBack", (0, head_center.y + 0.105, head_center.z + 0.025), (head_scale[0] * 1.06, 0.145, 0.255), hair, 46, 30)
        for side in (-1, 1):
            for row in range(4):
                ellipsoid(
                    f"BobLock_{side}_{row}",
                    (side * (0.185 + row * 0.008), head_center.y - 0.015 + row * 0.030, head_center.z + 0.145 - row * 0.090),
                    (0.066, 0.052, 0.095),
                    hair,
                    26,
                    18,
                )
        ellipsoid("SideSweep", (-0.105, head_center.y - 0.155, head_center.z + 0.145), (0.148, 0.036, 0.160), hair, 34, 22)
    elif style == "curl_bob":
        ellipsoid("CurlBobBase", (0, head_center.y + 0.100, head_center.z + 0.020), (head_scale[0] * 1.07, 0.145, 0.245), hair, 42, 28)
        count_index = 0
        for row, z_offset in enumerate((0.225, 0.145, 0.065, -0.020, -0.105)):
            count = 12 if row < 3 else 10
            for index in range(count):
                angle = math.radians(18 + index * (324 / max(1, count - 1)))
                if math.sin(angle) < -0.68 and abs(math.cos(angle)) < 0.38:
                    continue
                size = 0.050 + ((count_index + row) % 3) * 0.003
                ellipsoid(
                    f"BobCurl_{count_index}",
                    (math.cos(angle) * head_scale[0] * 0.98, head_center.y + math.sin(angle) * head_scale[1] * 0.80 + 0.045, head_center.z + z_offset),
                    (size, size * 0.93, size * 1.05),
                    hair,
                    22,
                    16,
                )
                count_index += 1
    elif style == "curly_pony":
        ellipsoid("PonyHairCap", (0, head_center.y + 0.035, head_center.z + 0.145), (head_scale[0] * 1.02, head_scale[1] * 0.94, 0.145), hair, 42, 28)
        for side in (-1, 1):
            for row in range(4):
                ellipsoid(f"TempleCurl_{side}_{row}", (side * (0.190 + row * 0.006), head_center.y - 0.050 + row * 0.028, head_center.z + 0.120 - row * 0.072), (0.050, 0.045, 0.061), hair, 22, 16)
        pony_center = Vector((0, head_center.y + 0.255, head_center.z + 0.115))
        for ring, count in ((0, 12), (1, 10), (2, 8)):
            radius = 0.142 - ring * 0.028
            for index in range(count):
                angle = math.tau * index / count + ring * 0.16
                ellipsoid(
                    f"PonyCurl_{ring}_{index}",
                    pony_center + Vector((math.cos(angle) * radius, math.sin(angle) * radius * 0.60, (ring - 1) * 0.055)),
                    (0.055, 0.051, 0.058),
                    hair,
                    22,
                    16,
                )
        if config["headband"]:
            torus("Headband", (0, head_center.y - 0.004, head_center.z + 0.155), head_scale[0] * 0.92, 0.014, materials["black"], rotation=(math.radians(90), 0, 0), scale=(1.0, 1.0, 0.78))


def build_character(character: str, root: pathlib.Path):
    if character not in CONFIGS:
        raise RuntimeError(f"No approved authored specification exists for {character}")
    config = CONFIGS[character]
    materials = build_materials(character, config, root)
    body = config["body"]
    adult = config["adult"]
    shoulder_x = 0.265 * body
    hip_x = 0.130 * body

    # Layered organic parka silhouette.
    ellipsoid("ParkaTorso", (0, 0, 1.085), (0.292 * body, 0.205, 0.345), materials["blue"], 50, 34)
    ellipsoid("ParkaLower", (0, 0.004, 0.900), (0.300 * body, 0.207, 0.175), materials["blue"], 46, 30)
    rounded_box("OrangeHem", (0, -0.205, 0.858), (0.48 * body, 0.024, 0.038), materials["orange"], 0.012)
    if config["open_jacket"]:
        ellipsoid("InnerVestLeft", (-0.090, -0.202, 1.105), (0.120, 0.025, 0.260), materials["cream"], 32, 22)
        ellipsoid("InnerVestRight", (0.090, -0.202, 1.105), (0.120, 0.025, 0.260), materials["cream"], 32, 22)
        ellipsoid("VisibleShirt", (0, -0.218, 1.115), (0.112, 0.020, 0.245), materials["shirt"], 32, 22)
        rounded_box("OpeningLeft", (-0.178, -0.222, 1.110), (0.020, 0.016, 0.395), materials["orange"], 0.006)
        rounded_box("OpeningRight", (0.178, -0.222, 1.110), (0.020, 0.016, 0.395), materials["orange"], 0.006)
    else:
        rounded_box("JacketZip", (0, -0.213, 1.090), (0.026, 0.016, 0.455), materials["orange"], 0.006)

    for side in (-1, 1):
        shoulder = Vector((side * shoulder_x, 0, 1.325))
        elbow = Vector((side * 0.390 * body, -0.010, 1.075))
        wrist = Vector((side * 0.420 * body, -0.028, 0.835))
        ellipsoid_between(f"UpperArm_{side}", shoulder, elbow, 0.100 * body, materials["blue"], 1.04, 0.96, 1.15)
        ellipsoid_between(f"LowerArm_{side}", elbow, wrist, 0.084 * body, materials["blue"], 1.02, 0.96, 1.15)
        ellipsoid(f"ShoulderPatch_{side}", shoulder + Vector((0, -0.074, 0.010)), (0.090, 0.060, 0.066), materials["orange"], 30, 20)
        ellipsoid(f"FurCuff_{side}", wrist + Vector((0, 0, 0.005)), (0.098, 0.086, 0.042), materials["cream"], 32, 22)
        ellipsoid(f"Glove_{side}", wrist + Vector((0, -0.010, -0.105)), (0.076, 0.065, 0.100), materials["leather_dark"], 32, 22)

    rounded_box("BeltFront", (0, -0.216, 0.930), (0.50 * body, 0.030, 0.060), materials["leather"], 0.016)
    rounded_box("Buckle", (0, -0.238, 0.930), (0.090, 0.022, 0.072), materials["metal"], 0.010)
    for side in (-1, 1):
        rounded_box(f"BeltPouch_{side}", (side * 0.225 * body, -0.222, 0.902), (0.105, 0.062, 0.120), materials["leather"], 0.026)
        ellipsoid_between(f"PackStrap_{side}", (side * 0.198 * body, -0.145, 1.345), (side * 0.238 * body, -0.188, 1.035), 0.022, materials["leather"], 0.80, 0.72, 1.05)

    for side in (-1, 1):
        hip = Vector((side * hip_x, 0, 0.790))
        knee = Vector((side * hip_x, 0, 0.495))
        ankle = Vector((side * hip_x, 0, 0.225))
        ellipsoid_between(f"Thigh_{side}", hip, knee, 0.102 * body, materials["pants"], 1.02, 0.98, 1.17)
        ellipsoid_between(f"Shin_{side}", knee, ankle, 0.089 * body, materials["pants"], 0.98, 0.96, 1.16)
        ellipsoid(f"KneePad_{side}", (side * hip_x, -0.092, 0.495), (0.070, 0.026, 0.074), materials["knee"], 30, 20)
        ellipsoid(f"BootFur_{side}", (side * hip_x, 0, 0.235), (0.101, 0.091, 0.040), materials["cream"], 32, 22)
        ellipsoid(f"BootUpper_{side}", (side * hip_x, -0.020, 0.145), (0.103, 0.115, 0.125), materials["leather"], 36, 24)
        ellipsoid(f"BootToe_{side}", (side * hip_x, -0.118, 0.080), (0.108, 0.142, 0.066), materials["leather"], 36, 24)
        rounded_box(f"BootSole_{side}", (side * hip_x, -0.080, 0.028), (0.218, 0.298, 0.050), materials["leather_dark"], 0.018)
        for lace in range(3):
            curve_mesh(f"BootLace_{side}_{lace}", [(side * hip_x - 0.045, -0.225, 0.100 + lace * 0.030), (side * hip_x, -0.232, 0.106 + lace * 0.030), (side * hip_x + 0.045, -0.225, 0.100 + lace * 0.030)], materials["gold"], 0.0045)

    rounded_box("Backpack", (0, 0.252, 1.105), (0.41 * body, 0.190, 0.500), materials["leather"], 0.065)
    rounded_box("BackpackFlap", (0, 0.360, 1.260), (0.35 * body, 0.040, 0.165), materials["leather_dark"], 0.030)
    rounded_box("PackBuckle", (0, 0.389, 1.235), (0.064, 0.022, 0.058), materials["gold"], 0.009)
    ellipsoid_between("Bedroll", (-0.225 * body, 0.335, 1.465), (0.225 * body, 0.335, 1.465), 0.092, materials["blue_dark"], 1.0, 1.0, 1.04)
    ellipsoid("MetalCup", (0.305 * body, 0.335, 0.900), (0.052, 0.052, 0.075), materials["metal"], 28, 20)

    head_z = 1.600 if adult else 1.590
    head_scale = config["head_scale"]
    head_center = Vector((0, -0.020, head_z))
    ellipsoid_between("Neck", (0, 0, 1.345), (0, 0, 1.445), 0.086, materials["skin"], 0.95, 0.95, 1.08)
    if config["scarf"]:
        torus("Scarf", (0, 0, 1.410), 0.142, 0.045, materials["blue_dark"], scale=(1.24, 0.92, 0.76))
    else:
        torus("FurCollar", (0, 0, 1.410), 0.177, 0.048, materials["cream"], scale=(1.28, 0.94, 0.76))
    ellipsoid("Head", head_center, head_scale, materials["skin"], 52, 36)
    build_face(config, materials, head_center, head_scale)
    add_hair(config, materials, head_center, head_scale)
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
    joined.name = f"{character}_PolishedCharacter"
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
        "schemaVersion": 4,
        "character": args.character,
        "generator": "HAVENLINE polished smooth character authoring v4",
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
                "smoothEllipsoidAnatomy": True,
                "segmentedCylinderAnatomy": False,
                "winterExpeditionWardrobe": True,
                "backpackBedrollAndCup": True,
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
