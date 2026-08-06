#!/usr/bin/env python3
"""Create real skinned 3D facial details for the approved HAVENLINE lead characters.

The earlier image-mapped oval face patch was visually unacceptable: it pasted a circular
portrait over an otherwise useful reconstruction. This module keeps the reconstructed head
and adds only modeled details that the approved sheets require—glasses, eyes, beard and
mouth accents. Every detail is ordinary mesh geometry that is weighted to the Head bone by
the existing deterministic binder; nothing faces the camera or behaves as a billboard.
"""

import math

import bpy
from mathutils import Vector


SCHEMA_VERSION = 1


def quantile(values, fraction):
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise RuntimeError("Cannot estimate the face surface from an empty sample")
    position = max(0.0, min(1.0, float(fraction))) * (len(ordered) - 1)
    lower = int(math.floor(position))
    upper = int(math.ceil(position))
    if lower == upper:
        return ordered[lower]
    blend = position - lower
    return ordered[lower] * (1.0 - blend) + ordered[upper] * blend


def make_material(name, rgba, roughness=0.7, metallic=0.0):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = rgba
    principled = material.node_tree.nodes.get("Principled BSDF")
    if principled:
        if principled.inputs.get("Base Color"):
            principled.inputs["Base Color"].default_value = rgba
        if principled.inputs.get("Roughness"):
            principled.inputs["Roughness"].default_value = roughness
        if principled.inputs.get("Metallic"):
            principled.inputs["Metallic"].default_value = metallic
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


def ellipsoid(name, location, scale, material, segments=28, rings=18):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segments,
        ring_count=rings,
        location=location,
    )
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    apply_scale(obj)
    apply_material(obj, material)
    return obj


def curve_object(name, points, bevel_depth, material, cyclic=False, resolution=3):
    curve_data = bpy.data.curves.new(name + "Curve", "CURVE")
    curve_data.dimensions = "3D"
    curve_data.resolution_u = resolution
    curve_data.bevel_resolution = 3
    curve_data.bevel_depth = bevel_depth
    spline = curve_data.splines.new("POLY")
    spline.points.add(len(points) - 1)
    for target, point in zip(spline.points, points):
        target.co = (*point, 1.0)
    spline.use_cyclic_u = cyclic
    obj = bpy.data.objects.new(name, curve_data)
    bpy.context.collection.objects.link(obj)
    curve_data.materials.append(material)
    return obj


def ellipse_ring(name, center_x, center_y, center_z, radius_x, radius_z, material, thickness):
    points = []
    for index in range(40):
        angle = math.tau * index / 40.0
        points.append(
            (
                center_x + math.cos(angle) * radius_x,
                center_y,
                center_z + math.sin(angle) * radius_z,
            )
        )
    return curve_object(name, points, thickness, material, cyclic=True)


def convert_and_join(objects, name):
    converted = []
    for obj in objects:
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        if obj.type == "CURVE":
            bpy.ops.object.convert(target="MESH")
        converted.append(bpy.context.view_layer.objects.active)

    bpy.ops.object.select_all(action="DESELECT")
    for obj in converted:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = converted[0]
    bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    joined.name = name
    joined["havenlineApprovedReferenceSurface"] = True
    joined["havenlineModeledFaceDetails"] = True
    return joined


def face_frame(character, meshes, bounds):
    minimum = Vector(bounds["minimum"])
    maximum = Vector(bounds["maximum"])
    height = max(maximum.z - minimum.z, 0.001)
    width = max(maximum.x - minimum.x, 0.001)
    center_x = (minimum.x + maximum.x) * 0.5
    eye_fraction = 0.892 if character == "Character1" else 0.884
    eye_z = minimum.z + height * eye_fraction
    sample_min_z = minimum.z + height * 0.76
    sample_max_z = minimum.z + height * 0.965
    sample_half_width = min(width * 0.19, height * 0.105)
    samples = []
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            point = matrix @ vertex.co
            if (
                sample_min_z <= point.z <= sample_max_z
                and abs(point.x - center_x) <= sample_half_width
            ):
                samples.append(point.y)
    if len(samples) < 30:
        samples = [
            (obj.matrix_world @ vertex.co).y
            for obj in meshes
            for vertex in obj.data.vertices
            if (obj.matrix_world @ vertex.co).z >= sample_min_z
        ]
    if not samples:
        raise RuntimeError(f"No upper-head samples were available for {character}")
    front_y = quantile(samples, 0.035)
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": center_x,
        "eyeZ": eye_z,
        "frontY": front_y,
        "sampleCount": len(samples),
    }


def add_eye_details(character, frame, created, frame_material, iris_material, pupil_material, highlight_material):
    height = frame["height"]
    center_x = frame["centerX"]
    eye_z = frame["eyeZ"]
    front_y = frame["frontY"]
    eye_offset = height * (0.0315 if character == "Character1" else 0.0300)
    lens_x = height * (0.0355 if character == "Character1" else 0.0330)
    lens_z = height * (0.0260 if character == "Character1" else 0.0270)
    frame_thickness = height * (0.0046 if character == "Character1" else 0.0036)

    for side in (-1, 1):
        eye_x = center_x + side * eye_offset
        created.append(
            ellipsoid(
                f"{character}_Iris_{side}",
                (eye_x, front_y - height * 0.0070, eye_z),
                (height * 0.0100, height * 0.0045, height * 0.0130),
                iris_material,
            )
        )
        created.append(
            ellipsoid(
                f"{character}_Pupil_{side}",
                (eye_x, front_y - height * 0.0110, eye_z),
                (height * 0.0047, height * 0.0026, height * 0.0064),
                pupil_material,
                24,
                16,
            )
        )
        created.append(
            ellipsoid(
                f"{character}_EyeHighlight_{side}",
                (
                    eye_x - side * height * 0.0028,
                    front_y - height * 0.0132,
                    eye_z + height * 0.0045,
                ),
                (height * 0.0018, height * 0.0012, height * 0.0023),
                highlight_material,
                18,
                12,
            )
        )
        created.append(
            ellipse_ring(
                f"{character}_Glasses_{side}",
                eye_x,
                front_y - height * 0.0150,
                eye_z,
                lens_x,
                lens_z,
                frame_material,
                frame_thickness,
            )
        )

    bridge_left = center_x - eye_offset + lens_x * 0.82
    bridge_right = center_x + eye_offset - lens_x * 0.82
    created.append(
        curve_object(
            f"{character}_GlassesBridge",
            [
                (bridge_left, front_y - height * 0.0150, eye_z + height * 0.0010),
                (center_x, front_y - height * 0.0170, eye_z - height * 0.0010),
                (bridge_right, front_y - height * 0.0150, eye_z + height * 0.0010),
            ],
            frame_thickness * 0.85,
            frame_material,
        )
    )


def add_character1_beard(character, frame, created, beard_material, mouth_material):
    height = frame["height"]
    x = frame["centerX"]
    y = frame["frontY"] - height * 0.0090
    beard_points = [
        (x - height * 0.050, y, height * 0.862),
        (x - height * 0.047, y - height * 0.002, height * 0.833),
        (x - height * 0.030, y - height * 0.004, height * 0.812),
        (x, y - height * 0.005, height * 0.801),
        (x + height * 0.030, y - height * 0.004, height * 0.812),
        (x + height * 0.047, y - height * 0.002, height * 0.833),
        (x + height * 0.050, y, height * 0.862),
    ]
    created.append(
        curve_object(
            f"{character}_BeardJaw",
            beard_points,
            height * 0.0125,
            beard_material,
        )
    )
    mustache_z = height * 0.845
    created.append(
        curve_object(
            f"{character}_MustacheLeft",
            [
                (x - height * 0.003, y - height * 0.006, mustache_z),
                (x - height * 0.018, y - height * 0.008, mustache_z + height * 0.003),
                (x - height * 0.034, y - height * 0.006, mustache_z - height * 0.002),
            ],
            height * 0.0060,
            beard_material,
        )
    )
    created.append(
        curve_object(
            f"{character}_MustacheRight",
            [
                (x + height * 0.003, y - height * 0.006, mustache_z),
                (x + height * 0.018, y - height * 0.008, mustache_z + height * 0.003),
                (x + height * 0.034, y - height * 0.006, mustache_z - height * 0.002),
            ],
            height * 0.0060,
            beard_material,
        )
    )
    created.append(
        curve_object(
            f"{character}_Mouth",
            [
                (x - height * 0.019, y - height * 0.010, height * 0.827),
                (x, y - height * 0.012, height * 0.824),
                (x + height * 0.019, y - height * 0.010, height * 0.827),
            ],
            height * 0.0028,
            mouth_material,
        )
    )


def add_character2_expression(character, frame, created, brow_material, mouth_material):
    height = frame["height"]
    x = frame["centerX"]
    y = frame["frontY"] - height * 0.013
    eye_z = frame["eyeZ"]
    for side in (-1, 1):
        eye_x = x + side * height * 0.0300
        created.append(
            curve_object(
                f"{character}_Brow_{side}",
                [
                    (eye_x - height * 0.020, y, eye_z + height * 0.027),
                    (eye_x, y - height * 0.002, eye_z + height * 0.031),
                    (eye_x + height * 0.020, y, eye_z + height * 0.026),
                ],
                height * 0.0032,
                brow_material,
            )
        )
    created.append(
        curve_object(
            f"{character}_Mouth",
            [
                (x - height * 0.020, y, height * 0.823),
                (x, y - height * 0.003, height * 0.818),
                (x + height * 0.020, y, height * 0.823),
            ],
            height * 0.0027,
            mouth_material,
        )
    )


def create_modeled_face_details(character, meshes, bounds):
    if character not in ("Character1", "Character2"):
        return {
            "schemaVersion": SCHEMA_VERSION,
            "applied": False,
            "reason": "modeled lead details are defined only for Characters 1 and 2",
        }, None

    frame = face_frame(character, meshes, bounds)
    black = make_material(f"{character}_GlassesMaterial", (0.008, 0.007, 0.006, 1.0), 0.36)
    iris_color = (0.095, 0.030, 0.010, 1.0) if character == "Character1" else (0.13, 0.048, 0.016, 1.0)
    iris = make_material(f"{character}_IrisMaterial", iris_color, 0.38)
    pupil = make_material(f"{character}_PupilMaterial", (0.001, 0.001, 0.001, 1.0), 0.32)
    highlight = make_material(f"{character}_HighlightMaterial", (0.96, 0.95, 0.91, 1.0), 0.22)
    mouth = make_material(f"{character}_MouthMaterial", (0.11, 0.025, 0.018, 1.0), 0.70)
    created = []
    add_eye_details(character, frame, created, black, iris, pupil, highlight)

    if character == "Character1":
        beard = make_material(f"{character}_BeardMaterial", (0.018, 0.010, 0.006, 1.0), 0.78)
        add_character1_beard(character, frame, created, beard, mouth)
    else:
        brow = make_material(f"{character}_BrowMaterial", (0.025, 0.012, 0.008, 1.0), 0.76)
        add_character2_expression(character, frame, created, brow, mouth)

    joined = convert_and_join(created, f"{character}_ModeledFaceDetails")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "applied": True,
        "mode": "modeled 3D reference details",
        "source": "approved character turnaround sheet",
        "faceFrame": {
            "centerX": frame["centerX"],
            "frontY": frame["frontY"],
            "eyeZ": frame["eyeZ"],
            "sampleCount": frame["sampleCount"],
        },
        "modeledObjectsJoined": len(created),
        "details": (
            ["glasses", "irises", "pupils", "eye highlights", "beard", "mustache", "mouth"]
            if character == "Character1"
            else ["glasses", "irises", "pupils", "eye highlights", "eyebrows", "mouth"]
        ),
        "surfaceType": "real skinned mesh geometry; never camera-facing; no portrait patch",
    }, joined
