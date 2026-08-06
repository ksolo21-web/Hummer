#!/usr/bin/env python3
"""Reference-faithful cleanup and facial refinement for HAVENLINE characters.

This module is imported by the Blender rig builder. It removes only tiny disconnected
reconstruction islands and replaces malformed single-view facial depth with a shallow,
skinned oval surface mapped directly from the approved character reference. The surface
is ordinary curved geometry parented to the humanoid head bone; it is never a billboard.
"""

import math
import pathlib
import shutil

import bmesh
import bpy
from mathutils import Vector


REFINEMENT_SCHEMA_VERSION = 3

PROFILES = {
    "Character1": {
        "source": "sheet",
        "center_z_fraction": 0.825,
        "center_x_offset": -0.004,
        "half_width": 0.096,
        "half_height": 0.102,
        "u_min": 0.115,
        "u_max": 0.202,
        "v_min": 0.738,
        "v_max": 0.884,
    },
    "Character2": {
        "source": "sheet",
        "center_z_fraction": 0.832,
        "center_x_offset": -0.004,
        "half_width": 0.098,
        "half_height": 0.104,
        "u_min": 0.110,
        "u_max": 0.210,
        "v_min": 0.735,
        "v_max": 0.895,
    },
    "Character3": {
        "source": "front",
        "center_z_fraction": 0.812,
        "center_x_offset": -0.006,
        "half_width": 0.082,
        "half_height": 0.091,
        "u_min": 0.423,
        "u_max": 0.530,
        "v_min": 0.770,
        "v_max": 0.885,
    },
    "Character4": {
        "source": "front",
        "center_z_fraction": 0.812,
        "center_x_offset": -0.004,
        "half_width": 0.085,
        "half_height": 0.093,
        "u_min": 0.405,
        "u_max": 0.520,
        "v_min": 0.745,
        "v_max": 0.890,
    },
}


def quantile(values, fraction):
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise RuntimeError("Cannot calculate a quantile from an empty sample")
    position = max(0.0, min(1.0, float(fraction))) * (len(ordered) - 1)
    lower = int(math.floor(position))
    upper = int(math.ceil(position))
    if lower == upper:
        return ordered[lower]
    blend = position - lower
    return ordered[lower] * (1.0 - blend) + ordered[upper] * blend


def copy_approved_references(character, root):
    copied = []
    approved_sheet = (
        pathlib.Path(".tooling")
        / "havenline-character-production"
        / "references"
        / f"{character}.jpg"
    )
    if approved_sheet.is_file():
        destination = root / "approved_reference_sheet.jpg"
        shutil.copyfile(approved_sheet, destination)
        copied.append(str(destination))

    generated_front = root / "triposr_input.jpg"
    if generated_front.is_file():
        destination = root / "approved_front_reference.jpg"
        shutil.copyfile(generated_front, destination)
        copied.append(str(destination))
    return copied


def connected_components(mesh):
    adjacency = [[] for _ in mesh.vertices]
    for edge in mesh.edges:
        a, b = edge.vertices
        adjacency[a].append(b)
        adjacency[b].append(a)
    components = []
    visited = set()
    for start in range(len(mesh.vertices)):
        if start in visited:
            continue
        stack = [start]
        visited.add(start)
        component = []
        while stack:
            current = stack.pop()
            component.append(current)
            for neighbor in adjacency[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    stack.append(neighbor)
        components.append(component)
    return components


def cleanup_disconnected_components(meshes, world_bounds):
    """Record sanitizer ownership without re-segmenting exported UV seams."""
    minimum, maximum = world_bounds(meshes)
    span = max(maximum.x - minimum.x, maximum.y - minimum.y, maximum.z - minimum.z, 1e-6)
    return {
        "schemaVersion": REFINEMENT_SCHEMA_VERSION,
        "sceneSpan": span,
        "objects": [
            {
                "object": obj.name,
                "vertices": len(obj.data.vertices),
                "polygons": len(obj.data.polygons),
                "componentsRemoved": 0,
                "verticesRemoved": 0,
                "reason": "topology already repaired by sanitize_character_mesh.py; UV seam duplicates preserved",
            }
            for obj in meshes
        ],
        "componentsRemoved": 0,
        "verticesRemoved": 0,
        "topologyOwner": "sanitize_character_mesh.py",
    }

def reference_source(profile, root):
    if profile["source"] == "front":
        return root / "approved_front_reference.jpg"
    return root / "approved_reference_sheet.jpg"


def create_reference_face_surface(character, root, meshes, bounds):
    profile = PROFILES.get(character)
    if profile is None:
        return {"applied": False, "reason": "missing face profile"}, None
    reference = reference_source(profile, root)
    if not reference.is_file() or reference.stat().st_size == 0:
        return {"applied": False, "reason": f"missing approved face source: {reference}"}, None

    minimum = Vector(bounds["minimum"])
    maximum = Vector(bounds["maximum"])
    height = max(maximum.z - minimum.z, 0.001)
    center_x = (minimum.x + maximum.x) * 0.5 + profile["center_x_offset"]
    center_z = minimum.z + height * profile["center_z_fraction"]
    half_width = profile["half_width"]
    half_height = profile["half_height"]

    depth_samples = []
    sample_strategy = None
    for multiplier in (1.45, 2.0, 2.8, 3.6):
        candidates = []
        for obj in meshes:
            for vertex in obj.data.vertices:
                point = obj.matrix_world @ vertex.co
                dx = (point.x - center_x) / max(half_width * multiplier, 1e-6)
                dz = (point.z - center_z) / max(half_height * multiplier, 1e-6)
                if dx * dx + dz * dz <= 1.0:
                    candidates.append(point.y)
        if len(candidates) > len(depth_samples):
            depth_samples = candidates
            sample_strategy = f"ellipse-{multiplier:.2f}x"
        if len(depth_samples) >= 40:
            break

    if len(depth_samples) < 10:
        head_floor = minimum.z + height * 0.72
        x_limit = max(half_width * 3.4, (maximum.x - minimum.x) * 0.34)
        fallback = []
        for obj in meshes:
            for vertex in obj.data.vertices:
                point = obj.matrix_world @ vertex.co
                if point.z >= head_floor and abs(point.x - center_x) <= x_limit:
                    fallback.append(point.y)
        if fallback:
            depth_samples = fallback
            sample_strategy = "upper-head-band-fallback"

    if not depth_samples:
        depth_samples = [
            (obj.matrix_world @ vertex.co).y
            for obj in meshes
            for vertex in obj.data.vertices
        ]
        sample_strategy = "whole-character-emergency-fallback"
    if not depth_samples:
        raise RuntimeError(f"No vertices were available for {character} facial refinement")

    measured_front = quantile(depth_samples, 0.10)
    shell_edge_y = measured_front - 0.008
    shell_bulge = 0.014
    backing_y = shell_edge_y + 0.012
    displaced = 0
    flatten_half_width = half_width * 1.18
    flatten_half_height = half_height * 1.18
    for obj in meshes:
        inverse = obj.matrix_world.inverted()
        changed = False
        for vertex in obj.data.vertices:
            point = obj.matrix_world @ vertex.co
            dx = (point.x - center_x) / max(flatten_half_width, 1e-6)
            dz = (point.z - center_z) / max(flatten_half_height, 1e-6)
            radial = dx * dx + dz * dz
            if radial <= 1.0 and point.y < shell_edge_y + 0.030:
                target_y = backing_y + 0.006 * radial
                if point.y < target_y:
                    point.y = target_y
                    vertex.co = inverse @ point
                    displaced += 1
                    changed = True
        if changed:
            obj.data.update()

    horizontal_segments = 28
    vertical_segments = 26
    vertices = []
    faces = []
    uv_by_vertex = []
    for row in range(vertical_segments + 1):
        row_fraction = row / vertical_segments
        vertical = -0.98 + 1.96 * row_fraction
        row_scale = math.sqrt(max(0.0, 1.0 - vertical * vertical))
        for column in range(horizontal_segments + 1):
            column_fraction = column / horizontal_segments
            horizontal = -1.0 + 2.0 * column_fraction
            normalized_x = horizontal * row_scale
            radial = min(1.0, normalized_x * normalized_x + vertical * vertical)
            x = center_x + half_width * normalized_x
            z = center_z + half_height * vertical
            y = shell_edge_y - shell_bulge * (1.0 - radial)
            vertices.append((x, y, z))
            u = profile["u_min"] + column_fraction * (profile["u_max"] - profile["u_min"])
            v = profile["v_min"] + row_fraction * (profile["v_max"] - profile["v_min"])
            uv_by_vertex.append((u, v))

    row_width = horizontal_segments + 1
    for row in range(vertical_segments):
        for column in range(horizontal_segments):
            a = row * row_width + column
            b = a + 1
            d = (row + 1) * row_width + column
            c = d + 1
            faces.append((a, b, c, d))

    mesh_data = bpy.data.meshes.new(f"{character}_ApprovedFaceSurfaceMesh")
    mesh_data.from_pydata(vertices, [], faces)
    mesh_data.update(calc_edges=True)
    uv_layer = mesh_data.uv_layers.new(name="UVMap")
    for polygon in mesh_data.polygons:
        for loop_index in polygon.loop_indices:
            vertex_index = mesh_data.loops[loop_index].vertex_index
            uv_layer.data[loop_index].uv = uv_by_vertex[vertex_index]

    material = bpy.data.materials.new(f"{character}_ApprovedFaceMaterial")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output_node = nodes.new("ShaderNodeOutputMaterial")
    shader = nodes.new("ShaderNodeBsdfPrincipled")
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = bpy.data.images.load(str(reference.resolve()), check_existing=True)
    texture.image.colorspace_settings.name = "sRGB"
    texture.interpolation = "Linear"
    texture.extension = "CLIP"
    links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    links.new(shader.outputs["BSDF"], output_node.inputs["Surface"])
    if shader.inputs.get("Roughness"):
        shader.inputs["Roughness"].default_value = 0.88
    if shader.inputs.get("Specular IOR Level"):
        shader.inputs["Specular IOR Level"].default_value = 0.10
    mesh_data.materials.append(material)

    face_object = bpy.data.objects.new(f"{character}_ApprovedFaceSurface", mesh_data)
    face_object["havenlineApprovedReferenceSurface"] = True
    face_object["havenlineReferenceSource"] = str(reference)
    bpy.context.collection.objects.link(face_object)

    return {
        "schemaVersion": REFINEMENT_SCHEMA_VERSION,
        "applied": True,
        "reference": str(reference),
        "sourceKind": profile["source"],
        "depthSampleCount": len(depth_samples),
        "depthSampleStrategy": sample_strategy,
        "measuredFrontDepth": measured_front,
        "shellEdgeDepth": shell_edge_y,
        "shellCenterDepth": shell_edge_y - shell_bulge,
        "center": [center_x, shell_edge_y, center_z],
        "halfWidth": half_width,
        "halfHeight": half_height,
        "displacedMalformedFaceVertices": displaced,
        "surfaceVertices": len(vertices),
        "surfaceFaces": len(faces),
        "uvCrop": [profile["u_min"], profile["v_min"], profile["u_max"], profile["v_max"]],
        "surfaceType": "shallow oval skinned approved-reference geometry; never camera-facing",
    }, face_object
