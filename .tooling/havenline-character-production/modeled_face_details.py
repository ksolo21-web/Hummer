#!/usr/bin/env python3
"""Add restrained, real 3D facial accents to HAVENLINE lead reconstructions.

The approved lead references already survive the reconstructed head texture and silhouette.
This pass therefore adds only thin glasses and, for Character 1, a subtle beard outline.
It deliberately does not replace the eyes, mouth, or face with separate portrait geometry.
Every accent is ordinary mesh geometry that the existing deterministic binder weights to
the Head bone; no element faces the camera or behaves as a billboard.
"""

import math

import bpy
from mathutils import Vector


SCHEMA_VERSION = 2


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


def make_material(name, rgba, roughness=0.72):
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
            principled.inputs["Metallic"].default_value = 0.0
    return material


def curve_object(name, points, bevel_depth, material, cyclic=False):
    curve_data = bpy.data.curves.new(name + "Curve", "CURVE")
    curve_data.dimensions = "3D"
    curve_data.resolution_u = 2
    curve_data.bevel_resolution = 2
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
    for index in range(44):
        angle = math.tau * index / 44.0
        points.append(
            (
                center_x + math.cos(angle) * radius_x,
                center_y,
                center_z + math.sin(angle) * radius_z,
            )
        )
    return curve_object(name, points, thickness, material, cyclic=True)


def convert_and_join(objects, name):
    if not objects:
        return None
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
    joined["havenlineDetailStrategy"] = "restrained reconstruction-preserving accents"
    return joined


def face_frame(character, meshes, bounds):
    minimum = Vector(bounds["minimum"])
    maximum = Vector(bounds["maximum"])
    height = max(maximum.z - minimum.z, 0.001)
    width = max(maximum.x - minimum.x, 0.001)
    center_x = (minimum.x + maximum.x) * 0.5
    eye_fraction = 0.884 if character == "Character1" else 0.878
    eye_z = minimum.z + height * eye_fraction
    sample_min_z = minimum.z + height * 0.75
    sample_max_z = minimum.z + height * 0.965
    sample_half_width = min(width * 0.22, height * 0.115)
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


def add_glasses(character, frame, created, material):
    height = frame["height"]
    center_x = frame["centerX"]
    eye_z = frame["eyeZ"]
    front_y = frame["frontY"]
    if character == "Character1":
        eye_offset = height * 0.0275
        lens_x = height * 0.0220
        lens_z = height * 0.0160
        thickness = height * 0.0018
    else:
        eye_offset = height * 0.0255
        lens_x = height * 0.0215
        lens_z = height * 0.0165
        thickness = height * 0.0016

    glasses_y = front_y - height * 0.0025
    for side in (-1, 1):
        created.append(
            ellipse_ring(
                f"{character}_Glasses_{side}",
                center_x + side * eye_offset,
                glasses_y,
                eye_z,
                lens_x,
                lens_z,
                material,
                thickness,
            )
        )

    bridge_left = center_x - eye_offset + lens_x * 0.94
    bridge_right = center_x + eye_offset - lens_x * 0.94
    created.append(
        curve_object(
            f"{character}_GlassesBridge",
            [
                (bridge_left, glasses_y, eye_z),
                (center_x, glasses_y - height * 0.0010, eye_z - height * 0.0007),
                (bridge_right, glasses_y, eye_z),
            ],
            thickness * 0.78,
            material,
        )
    )


def add_character1_beard(frame, created, material):
    height = frame["height"]
    minimum = frame["minimum"]
    x = frame["centerX"]
    y = frame["frontY"] - height * 0.0015
    z = lambda fraction: minimum.z + height * fraction

    created.append(
        curve_object(
            "Character1_BeardJaw",
            [
                (x - height * 0.039, y, z(0.856)),
                (x - height * 0.034, y - height * 0.0010, z(0.832)),
                (x - height * 0.021, y - height * 0.0016, z(0.815)),
                (x, y - height * 0.0020, z(0.808)),
                (x + height * 0.021, y - height * 0.0016, z(0.815)),
                (x + height * 0.034, y - height * 0.0010, z(0.832)),
                (x + height * 0.039, y, z(0.856)),
            ],
            height * 0.0042,
            material,
        )
    )
    mustache_z = z(0.846)
    for side in (-1, 1):
        created.append(
            curve_object(
                f"Character1_Mustache_{side}",
                [
                    (x + side * height * 0.002, y - height * 0.0015, mustache_z),
                    (x + side * height * 0.014, y - height * 0.0020, mustache_z + height * 0.0015),
                    (x + side * height * 0.026, y - height * 0.0012, mustache_z - height * 0.0010),
                ],
                height * 0.0021,
                material,
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
    frame_material = make_material(
        f"{character}_GlassesMaterial",
        (0.012, 0.010, 0.008, 1.0),
        0.42,
    )
    created = []
    add_glasses(character, frame, created, frame_material)

    details = ["thin glasses frames"]
    if character == "Character1":
        beard_material = make_material(
            "Character1_BeardMaterial",
            (0.020, 0.011, 0.007, 1.0),
            0.78,
        )
        add_character1_beard(frame, created, beard_material)
        details.extend(["subtle beard outline", "subtle mustache"])

    joined = convert_and_join(created, f"{character}_ModeledFaceDetails")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "applied": joined is not None,
        "mode": "restrained modeled 3D reference accents",
        "source": "approved character turnaround sheet plus reconstructed facial surface",
        "faceFrame": {
            "centerX": frame["centerX"],
            "frontY": frame["frontY"],
            "eyeZ": frame["eyeZ"],
            "sampleCount": frame["sampleCount"],
        },
        "modeledObjectsJoined": len(created),
        "details": details,
        "preservedReconstructionFeatures": ["eyes", "eyelids", "nose", "mouth", "cheeks", "skin texture"],
        "surfaceType": "real skinned mesh geometry; never camera-facing; no portrait patch",
    }, joined
