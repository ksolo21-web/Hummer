#!/usr/bin/env python3
"""Build production-ready HAVENLINE character rigs, clips, LODs and exports.

The approved female lead/reference models reconstructed by single-view TripoSR can retain
excellent clothing and silhouette detail while producing unstable facial depth. For
Characters 3 and 4 this script now preserves the reconstructed body/hair, pushes only the
central malformed face surface behind a small curved shell, and maps the approved front
reference directly onto that shell. The shell is real skinned geometry, not a camera-facing
billboard, so it remains attached to the head in Unity and reads correctly from front and
three-quarter gameplay cameras.
"""

import argparse
import json
import math
import pathlib
import shutil
import sys
import traceback

import bpy
from mathutils import Vector

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))
import reference_face_refinement as reference_refinement


def args_after_separator():
    values = sys.argv
    return values[values.index("--") + 1 :] if "--" in values else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(args_after_separator())


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for data in (bpy.data.armatures, bpy.data.cameras, bpy.data.lights):
        for item in list(data):
            if item.users == 0:
                data.remove(item)


def import_glb(path):
    bpy.ops.import_scene.gltf(filepath=str(path))
    return [item for item in bpy.context.scene.objects if item.type == "MESH"]


def world_bounds(meshes):
    world = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not world:
        raise RuntimeError("No mesh bounds were available")
    minimum = Vector((min(p.x for p in world), min(p.y for p in world), min(p.z for p in world)))
    maximum = Vector((max(p.x for p in world), max(p.y for p in world), max(p.z for p in world)))
    return minimum, maximum


def normalize(meshes):
    minimum, maximum = world_bounds(meshes)
    size = maximum - minimum
    height = max(size.z, 0.001)
    scale = 1.72 / height
    center_x = (minimum.x + maximum.x) * 0.5
    center_y = (minimum.y + maximum.y) * 0.5
    for obj in meshes:
        obj.location.x = (obj.location.x - center_x) * scale
        obj.location.y = (obj.location.y - center_y) * scale
        obj.location.z = (obj.location.z - minimum.z) * scale
        obj.scale *= scale
        bpy.context.view_layer.objects.active = obj
        obj.select_set(True)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        obj.select_set(False)
    minimum, maximum = world_bounds(meshes)
    return {
        "minimum": [minimum.x, minimum.y, minimum.z],
        "maximum": [maximum.x, maximum.y, maximum.z],
        "width": maximum.x - minimum.x,
        "depth": maximum.y - minimum.y,
        "height": maximum.z - minimum.z,
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


def face_profile(character):
    profiles = {
        "Character3": {
            "center_z_fraction": 0.910,
            "center_x_offset": -0.006,
            "half_width": 0.116,
            "half_height": 0.128,
            "u_min": 0.360,
            "u_max": 0.610,
            "v_min": 0.742,
            "v_max": 0.948,
        },
        "Character4": {
            "center_z_fraction": 0.908,
            "center_x_offset": -0.004,
            "half_width": 0.121,
            "half_height": 0.130,
            "u_min": 0.350,
            "u_max": 0.620,
            "v_min": 0.744,
            "v_max": 0.948,
        },
    }
    return profiles.get(character)


def create_reference_face_surface(character, root, meshes, bounds):
    profile = face_profile(character)
    reference = root / "approved_front_reference.jpg"
    if profile is None:
        return {
            "applied": False,
            "reason": "reference face surface is only required for Characters 3 and 4",
        }, None
    if not reference.is_file() or reference.stat().st_size == 0:
        return {
            "applied": False,
            "reason": f"missing approved front reference: {reference}",
        }, None

    minimum = Vector(bounds["minimum"])
    maximum = Vector(bounds["maximum"])
    height = max(maximum.z - minimum.z, 0.001)
    center_x = (minimum.x + maximum.x) * 0.5 + profile["center_x_offset"]
    center_z = minimum.z + height * profile["center_z_fraction"]
    half_width = profile["half_width"]
    half_height = profile["half_height"]

    face_depth_samples = []
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            point = matrix @ vertex.co
            dx = (point.x - center_x) / max(half_width * 1.45, 1e-6)
            dz = (point.z - center_z) / max(half_height * 1.35, 1e-6)
            if dx * dx + dz * dz <= 1.0:
                face_depth_samples.append(point.y)
    if len(face_depth_samples) < 40:
        raise RuntimeError(
            f"Too few head vertices were available for approved face refinement: {len(face_depth_samples)}"
        )

    measured_front = quantile(face_depth_samples, 0.10)
    shell_edge_y = measured_front + 0.043
    shell_bulge = 0.064
    backing_center_y = shell_edge_y + 0.015

    displaced_vertices = 0
    for obj in meshes:
        inverse = obj.matrix_world.inverted()
        changed = False
        for vertex in obj.data.vertices:
            point = obj.matrix_world @ vertex.co
            dx = (point.x - center_x) / max(half_width, 1e-6)
            dz = (point.z - center_z) / max(half_height, 1e-6)
            radial = dx * dx + dz * dz
            if radial <= 1.0 and point.y < shell_edge_y + 0.024:
                target_y = backing_center_y + 0.006 * radial
                if point.y < target_y:
                    point.y = target_y
                    vertex.co = inverse @ point
                    displaced_vertices += 1
                    changed = True
        if changed:
            obj.data.update()

    horizontal_segments = 28
    vertical_segments = 24
    theta_limit = math.radians(82.0)
    phi_limit = math.radians(84.0)
    vertices = []
    faces = []
    uv_by_vertex = []

    for row in range(vertical_segments + 1):
        row_fraction = row / vertical_segments
        theta = -theta_limit + (2.0 * theta_limit * row_fraction)
        cos_theta = math.cos(theta)
        sin_theta = math.sin(theta)
        for column in range(horizontal_segments + 1):
            column_fraction = column / horizontal_segments
            phi = -phi_limit + (2.0 * phi_limit * column_fraction)
            sin_phi = math.sin(phi)
            cos_phi = math.cos(phi)
            x = center_x + half_width * cos_theta * sin_phi
            z = center_z + half_height * sin_theta
            y = shell_edge_y - shell_bulge * cos_theta * cos_phi
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
    texture.interpolation = "Linear"
    texture.extension = "CLIP"
    links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    links.new(shader.outputs["BSDF"], output_node.inputs["Surface"])
    if shader.inputs.get("Roughness"):
        shader.inputs["Roughness"].default_value = 0.62
    if shader.inputs.get("Specular IOR Level"):
        shader.inputs["Specular IOR Level"].default_value = 0.22
    mesh_data.materials.append(material)

    face_object = bpy.data.objects.new(f"{character}_ApprovedFaceSurface", mesh_data)
    face_object["havenlineApprovedReferenceSurface"] = True
    face_object["havenlineReferenceSource"] = str(reference)
    bpy.context.collection.objects.link(face_object)

    return {
        "applied": True,
        "reference": str(reference),
        "measuredFrontDepth": measured_front,
        "shellEdgeDepth": shell_edge_y,
        "shellCenterDepth": shell_edge_y - shell_bulge,
        "center": [center_x, shell_edge_y, center_z],
        "halfWidth": half_width,
        "halfHeight": half_height,
        "displacedMalformedFaceVertices": displaced_vertices,
        "surfaceVertices": len(vertices),
        "surfaceFaces": len(faces),
        "uvCrop": [
            profile["u_min"],
            profile["v_min"],
            profile["u_max"],
            profile["v_max"],
        ],
        "surfaceType": "curved skinned approved-reference geometry; never camera-facing",
    }, face_object


def add_bone(armature, name, head, tail, parent=None):
    bone = armature.edit_bones.new(name)
    bone.head = head
    bone.tail = tail
    if parent:
        bone.parent = armature.edit_bones.get(parent)
        bone.use_connect = False
    return bone


def create_rig(character, bounds):
    width = max(float(bounds["width"]), 0.45)
    shoulder_x = min(max(width * 0.19, 0.12), 0.22)
    arm_x = min(max(width * 0.31, 0.20), 0.36)
    hand_x = min(max(width * 0.35, 0.23), 0.42)
    hip_x = min(max(width * 0.10, 0.07), 0.13)

    armature_data = bpy.data.armatures.new(f"{character}_HumanoidRig")
    rig = bpy.data.objects.new(f"{character}_HumanoidRig", armature_data)
    bpy.context.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")

    add_bone(armature_data, "Hips", (0, 0, 0.80), (0, 0, 0.98))
    add_bone(armature_data, "Spine", (0, 0, 0.98), (0, 0, 1.22), "Hips")
    add_bone(armature_data, "Chest", (0, 0, 1.22), (0, 0, 1.43), "Spine")
    add_bone(armature_data, "Neck", (0, 0, 1.43), (0, 0, 1.52), "Chest")
    add_bone(armature_data, "Head", (0, 0, 1.52), (0, 0, 1.73), "Neck")

    for side, sign in (("Left", 1), ("Right", -1)):
        add_bone(armature_data, f"{side}Shoulder", (0, 0, 1.39), (shoulder_x * sign, 0, 1.37), "Chest")
        add_bone(armature_data, f"{side}UpperArm", (shoulder_x * sign, 0, 1.37), (arm_x * sign, 0, 1.13), f"{side}Shoulder")
        add_bone(armature_data, f"{side}LowerArm", (arm_x * sign, 0, 1.13), (hand_x * sign, 0, 0.88), f"{side}UpperArm")
        add_bone(armature_data, f"{side}Hand", (hand_x * sign, 0, 0.88), (hand_x * sign, -0.02, 0.73), f"{side}LowerArm")
        add_bone(armature_data, f"{side}UpperLeg", (hip_x * sign, 0, 0.82), (hip_x * sign, 0, 0.48), "Hips")
        add_bone(armature_data, f"{side}LowerLeg", (hip_x * sign, 0, 0.48), (hip_x * sign, 0, 0.14), f"{side}UpperLeg")
        add_bone(armature_data, f"{side}Foot", (hip_x * sign, 0, 0.14), (hip_x * sign, -0.19, 0.07), f"{side}LowerLeg")

    bpy.ops.object.mode_set(mode="OBJECT")
    rig.select_set(False)
    return rig


def normalized_weights(entries):
    positive = [(name, max(float(weight), 0.0)) for name, weight in entries if weight > 0]
    total = sum(weight for _, weight in positive)
    if total <= 0:
        return [("Hips", 1.0)]
    return [(name, weight / total) for name, weight in positive]


def vertex_weights(position, width):
    x, _, z = position
    sign = "Left" if x >= 0 else "Right"
    abs_x = abs(x)
    torso_edge = max(width * 0.20, 0.12)
    arm_edge = max(width * 0.28, 0.18)

    if z < 0.14:
        return normalized_weights([(f"{sign}Foot", 1.0), (f"{sign}LowerLeg", 0.15)])
    if z < 0.46:
        return normalized_weights([(f"{sign}LowerLeg", 1.0), (f"{sign}UpperLeg", 0.18)])
    if z < 0.82:
        if abs_x > arm_edge and z > 0.64:
            return normalized_weights([(f"{sign}Hand", 1.0), (f"{sign}LowerArm", 0.20)])
        return normalized_weights([(f"{sign}UpperLeg", 1.0), ("Hips", 0.22)])

    if abs_x > arm_edge:
        if z < 0.96:
            return normalized_weights([(f"{sign}Hand", 0.80), (f"{sign}LowerArm", 0.35)])
        if z < 1.20:
            return normalized_weights([(f"{sign}LowerArm", 0.80), (f"{sign}UpperArm", 0.35)])
        if z < 1.43:
            return normalized_weights([(f"{sign}UpperArm", 0.82), (f"{sign}Shoulder", 0.30)])

    if z >= 1.52:
        return normalized_weights([("Head", 1.0), ("Neck", 0.12)])
    if z >= 1.42:
        return normalized_weights([("Neck", 0.75), ("Head", 0.25), ("Chest", 0.15)])
    if z >= 1.20:
        if abs_x > torso_edge:
            return normalized_weights([(f"{sign}Shoulder", 0.62), ("Chest", 0.55)])
        return normalized_weights([("Chest", 0.80), ("Spine", 0.25)])
    if z >= 0.98:
        return normalized_weights([("Spine", 0.80), ("Chest", 0.24), ("Hips", 0.10)])
    return normalized_weights([("Hips", 0.80), ("Spine", 0.20), (f"{sign}UpperLeg", 0.12)])


def bind(meshes, rig, bounds):
    width = max(float(bounds["width"]), 0.45)
    rig_inverse = rig.matrix_world.inverted()
    required_groups = [bone.name for bone in rig.data.bones]
    weighted_vertices = 0

    for obj in meshes:
        world_matrix = obj.matrix_world.copy()
        for modifier in list(obj.modifiers):
            if modifier.type == "ARMATURE":
                obj.modifiers.remove(modifier)
        obj.vertex_groups.clear()
        groups = {name: obj.vertex_groups.new(name=name) for name in required_groups}

        for vertex in obj.data.vertices:
            rig_position = rig_inverse @ (obj.matrix_world @ vertex.co)
            assignments = vertex_weights(rig_position, width)
            for name, weight in assignments:
                groups[name].add([vertex.index], weight, "REPLACE")
            weighted_vertices += 1

        obj.parent = rig
        obj.matrix_parent_inverse = rig.matrix_world.inverted()
        obj.matrix_world = world_matrix
        modifier = obj.modifiers.new("HumanoidRig", "ARMATURE")
        modifier.object = rig
        modifier.use_vertex_groups = True
        modifier.use_bone_envelopes = False

    if weighted_vertices == 0:
        raise RuntimeError("No vertices were assigned deterministic humanoid weights")
    return weighted_vertices


def key_rotation(action, rig, bone_name, frame, rotation):
    bone = rig.pose.bones.get(bone_name)
    if bone is None:
        return
    bone.rotation_mode = "XYZ"
    bone.rotation_euler = rotation
    bone.keyframe_insert(data_path="rotation_euler", frame=frame, group=bone_name)


def create_action(rig, name, length, pose_fn):
    action = bpy.data.actions.new(name=name)
    rig.animation_data_create()
    rig.animation_data.action = action
    pose_fn(action, length)
    action.frame_range = (1, length)
    action.use_fake_user = True
    return action


def create_animations(rig):
    actions = []

    def idle(action, length):
        for frame, value in ((1, -0.025), (20, 0.025), (40, -0.025)):
            key_rotation(action, rig, "Chest", frame, (value, 0, 0))

    def cycle(action, length, amplitude):
        for frame, value in ((1, amplitude), (length // 2, -amplitude), (length, amplitude)):
            key_rotation(action, rig, "LeftUpperLeg", frame, (value, 0, 0))
            key_rotation(action, rig, "RightUpperLeg", frame, (-value, 0, 0))
            key_rotation(action, rig, "LeftUpperArm", frame, (-value * 0.8, 0, 0))
            key_rotation(action, rig, "RightUpperArm", frame, (value * 0.8, 0, 0))

    def work(action, length, amplitude):
        for frame, value in ((1, 0), (length // 2, amplitude), (length, 0)):
            key_rotation(action, rig, "Spine", frame, (value * 0.35, 0, 0))
            key_rotation(action, rig, "LeftUpperArm", frame, (value, 0, -0.15))
            key_rotation(action, rig, "RightUpperArm", frame, (value, 0, 0.15))

    actions.append(create_action(rig, "idle", 40, idle))
    actions.append(create_action(rig, "walk", 30, lambda action, length: cycle(action, length, 0.55)))
    actions.append(create_action(rig, "run", 22, lambda action, length: cycle(action, length, 0.85)))
    for name, amplitude in (("gather", -0.95), ("carry", -0.45), ("deposit", -1.05), ("warm", -0.30), ("build", -1.15)):
        actions.append(create_action(rig, name, 36, lambda action, length, value=amplitude: work(action, length, value)))
    rig.animation_data.action = bpy.data.actions.get("idle")
    return actions


def duplicate_lod(meshes, suffix, ratio):
    duplicated = []
    for original in meshes:
        copy = original.copy()
        copy.data = original.data.copy()
        copy.name = original.name + suffix
        bpy.context.collection.objects.link(copy)

        armature_object = None
        for existing in list(copy.modifiers):
            if existing.type == "ARMATURE":
                armature_object = existing.object
                copy.modifiers.remove(existing)

        local_ratio = 0.88 if original.get("havenlineApprovedReferenceSurface") else ratio
        modifier = copy.modifiers.new("MobileDecimate", "DECIMATE")
        modifier.ratio = local_ratio
        bpy.context.view_layer.objects.active = copy
        copy.select_set(True)
        try:
            bpy.ops.object.modifier_apply(modifier=modifier.name)
        except RuntimeError as exception:
            print(f"LOD decimation warning for {copy.name}: {exception}")
        finally:
            copy.select_set(False)

        if armature_object is not None:
            armature = copy.modifiers.new("HumanoidRig", "ARMATURE")
            armature.object = armature_object
            armature.use_vertex_groups = True
            armature.use_bone_envelopes = False
        duplicated.append(copy)
    return duplicated


def export_in_isolated_scene(path, meshes, rig, export_format):
    original_scene = bpy.context.window.scene
    export_scene = bpy.data.scenes.new(f"Export_{path.stem}")
    try:
        for obj in meshes + [rig]:
            export_scene.collection.objects.link(obj)
        bpy.context.window.scene = export_scene
        bpy.ops.object.select_all(action="DESELECT")
        for obj in meshes + [rig]:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = rig
        if export_format == "GLB":
            bpy.ops.export_scene.gltf(filepath=str(path), export_format="GLB", use_selection=True, export_animations=True)
        else:
            bpy.ops.export_scene.fbx(
                filepath=str(path),
                use_selection=True,
                add_leaf_bones=False,
                bake_anim=True,
                bake_anim_use_all_actions=True,
                bake_anim_use_nla_strips=False,
                path_mode="COPY",
                embed_textures=True,
            )
    finally:
        bpy.context.window.scene = original_scene
        bpy.data.scenes.remove(export_scene)
        bpy.ops.object.select_all(action="DESELECT")

    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError(f"Exporter did not create a non-empty file: {path}")


def point_camera(camera, target):
    direction = Vector(target) - camera.location
    camera.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def render_proofs(root, objects):
    camera_data = bpy.data.cameras.new("ProofCamera")
    camera = bpy.data.objects.new("ProofCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = 2.25
    bpy.context.scene.camera = camera

    light_data = bpy.data.lights.new("Key", "AREA")
    light_data.energy = 1100
    light_data.size = 5
    light = bpy.data.objects.new("Key", light_data)
    light.location = (-3, -4, 6)
    point_camera(light, (0, 0, 0.92))
    bpy.context.collection.objects.link(light)

    fill_data = bpy.data.lights.new("Fill", "AREA")
    fill_data.energy = 500
    fill_data.size = 4
    fill = bpy.data.objects.new("Fill", fill_data)
    fill.location = (3, -2, 3)
    point_camera(fill, (0, 0, 0.92))
    bpy.context.collection.objects.link(fill)

    scene = bpy.context.scene
    engines = {item.identifier for item in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items}
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.frame_set(1)
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.06, 0.07, 0.09)

    radius = 5.0
    target = (0, 0, 0.92)
    views = (("front", (0, -radius, 0.92)), ("three-quarter", (radius / math.sqrt(2), -radius / math.sqrt(2), 0.92)), ("side", (radius, 0, 0.92)), ("back", (0, radius, 0.92)))
    for label, position in views:
        camera.location = position
        point_camera(camera, target)
        bpy.context.view_layer.update()
        scene.render.filepath = str(root / f"proof_{label}.png")
        bpy.ops.render.render(write_still=True)


def main():
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    report_path = root / "rig-report.json"
    report = {"schemaVersion": 2, "character": args.character, "success": False, "input": args.input, "outputs": []}
    try:
        approved_references = reference_refinement.copy_approved_references(args.character, root)
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        if not meshes:
            raise RuntimeError("Generated GLB contains no mesh objects")
        pre_rig_cleanup = reference_refinement.cleanup_disconnected_components(
            meshes,
            world_bounds,
        )
        bounds = normalize(meshes)
        face_refinement, face_object = reference_refinement.create_reference_face_surface(
            args.character,
            root,
            meshes,
            bounds,
        )
        if face_object is not None:
            meshes.append(face_object)
        rig = create_rig(args.character, bounds)
        weighted_vertices = bind(meshes, rig, bounds)
        actions = create_animations(rig)

        render_proofs(root, meshes)
        base_glb = root / f"{args.character}_production.glb"
        base_fbx = root / f"{args.character}_production.fbx"
        export_in_isolated_scene(base_glb, meshes, rig, "GLB")
        export_in_isolated_scene(base_fbx, meshes, rig, "FBX")

        lod1 = duplicate_lod(meshes, "_LOD1", 0.62)
        lod2 = duplicate_lod(meshes, "_LOD2", 0.34)
        lod1_glb = root / f"{args.character}_LOD1.glb"
        lod2_glb = root / f"{args.character}_LOD2.glb"
        export_in_isolated_scene(lod1_glb, lod1, rig, "GLB")
        export_in_isolated_scene(lod2_glb, lod2, rig, "GLB")

        outputs = [base_glb, base_fbx, lod1_glb, lod2_glb]
        outputs.extend(root / f"proof_{name}.png" for name in ("front", "three-quarter", "side", "back"))
        outputs.extend(pathlib.Path(path) for path in approved_references)
        report.update(
            success=True,
            bounds=bounds,
            meshObjects=len(meshes),
            weightedVertices=weighted_vertices,
            bones=[bone.name for bone in rig.data.bones],
            animations=[action.name for action in actions],
            approvedReferences=approved_references,
            preRigCleanup=pre_rig_cleanup,
            faceRefinement=face_refinement,
            outputs=[{"path": str(path), "bytes": path.stat().st_size} for path in outputs if path.is_file()],
        )
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except Exception as exception:
        report.update(error=repr(exception), traceback=traceback.format_exc())
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        raise


if __name__ == "__main__":
    main()
