#!/usr/bin/env python3
import argparse
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
        add_bone(
            armature_data,
            f"{side}Shoulder",
            (0, 0, 1.39),
            (shoulder_x * sign, 0, 1.37),
            "Chest",
        )
        add_bone(
            armature_data,
            f"{side}UpperArm",
            (shoulder_x * sign, 0, 1.37),
            (arm_x * sign, 0, 1.13),
            f"{side}Shoulder",
        )
        add_bone(
            armature_data,
            f"{side}LowerArm",
            (arm_x * sign, 0, 1.13),
            (hand_x * sign, 0, 0.88),
            f"{side}UpperArm",
        )
        add_bone(
            armature_data,
            f"{side}Hand",
            (hand_x * sign, 0, 0.88),
            (hand_x * sign, -0.02, 0.73),
            f"{side}LowerArm",
        )
        add_bone(
            armature_data,
            f"{side}UpperLeg",
            (hip_x * sign, 0, 0.82),
            (hip_x * sign, 0, 0.48),
            "Hips",
        )
        add_bone(
            armature_data,
            f"{side}LowerLeg",
            (hip_x * sign, 0, 0.48),
            (hip_x * sign, 0, 0.14),
            f"{side}UpperLeg",
        )
        add_bone(
            armature_data,
            f"{side}Foot",
            (hip_x * sign, 0, 0.14),
            (hip_x * sign, -0.19, 0.07),
            f"{side}LowerLeg",
        )

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
    for name, amplitude in (
        ("gather", -0.95),
        ("carry", -0.45),
        ("deposit", -1.05),
        ("warm", -0.30),
        ("build", -1.15),
    ):
        actions.append(
            create_action(
                rig,
                name,
                36,
                lambda action, length, value=amplitude: work(action, length, value),
            )
        )
    rig.animation_data.action = bpy.data.actions.get("idle")
    return actions


def duplicate_lod(meshes, suffix, ratio):
    duplicated = []
    for original in meshes:
        copy = original.copy()
        copy.data = original.data.copy()
        copy.name = original.name + suffix
        bpy.context.collection.objects.link(copy)
        modifier = copy.modifiers.new("MobileDecimate", "DECIMATE")
        modifier.ratio = ratio
        bpy.context.view_layer.objects.active = copy
        copy.select_set(True)
        try:
            bpy.ops.object.modifier_apply(modifier=modifier.name)
        except RuntimeError as exception:
            print(f"LOD decimation warning for {copy.name}: {exception}")
        copy.select_set(False)
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
            bpy.ops.export_scene.gltf(
                filepath=str(path),
                export_format="GLB",
                use_selection=True,
                export_animations=True,
            )
        else:
            bpy.ops.export_scene.fbx(
                filepath=str(path),
                use_selection=True,
                add_leaf_bones=False,
                bake_anim=True,
                bake_anim_use_all_actions=True,
                bake_anim_use_nla_strips=False,
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
    camera.location = (0, -5.0, 0.92)
    point_camera(camera, (0, 0, 0.92))
    bpy.context.scene.camera = camera

    light_data = bpy.data.lights.new("Key", "AREA")
    light_data.energy = 1100
    light_data.size = 5
    light = bpy.data.objects.new("Key", light_data)
    light.location = (-3, -4, 6)
    bpy.context.collection.objects.link(light)

    fill_data = bpy.data.lights.new("Fill", "AREA")
    fill_data.energy = 500
    fill_data.size = 4
    fill = bpy.data.objects.new("Fill", fill_data)
    fill.location = (3, -2, 3)
    bpy.context.collection.objects.link(fill)

    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE_NEXT" if hasattr(bpy.types, "BLENDER_EEVEE_NEXT") else "BLENDER_EEVEE"
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.06, 0.07, 0.09)

    original_rotations = {obj.name: obj.rotation_euler.copy() for obj in objects}
    try:
        for label, angle in (
            ("front", 0),
            ("three-quarter", math.radians(45)),
            ("side", math.radians(90)),
            ("back", math.radians(180)),
        ):
            for obj in objects:
                obj.rotation_euler[2] = original_rotations[obj.name].z + angle
            scene.render.filepath = str(root / f"proof_{label}.png")
            bpy.ops.render.render(write_still=True)
    finally:
        for obj in objects:
            obj.rotation_euler = original_rotations[obj.name]


def main():
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    report_path = root / "rig-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "success": False,
        "input": args.input,
        "outputs": [],
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        if not meshes:
            raise RuntimeError("Generated GLB contains no mesh objects")
        bounds = normalize(meshes)
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
        report.update(
            success=True,
            bounds=bounds,
            meshObjects=len(meshes),
            weightedVertices=weighted_vertices,
            bones=[bone.name for bone in rig.data.bones],
            animations=[action.name for action in actions],
            outputs=[
                {"path": str(path), "bytes": path.stat().st_size}
                for path in outputs
                if path.is_file()
            ],
        )
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except Exception as exception:
        report.update(error=repr(exception), traceback=traceback.format_exc())
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        raise


if __name__ == "__main__":
    main()
