#!/usr/bin/env python3
import argparse
import math
import pathlib
import sys

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


def import_glb(path):
    bpy.ops.import_scene.gltf(filepath=str(path))
    return [item for item in bpy.context.scene.objects if item.type == "MESH"]


def normalize(meshes):
    points = [corner for obj in meshes for corner in obj.bound_box]
    world = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    minimum = Vector((min(p.x for p in world), min(p.y for p in world), min(p.z for p in world)))
    maximum = Vector((max(p.x for p in world), max(p.y for p in world), max(p.z for p in world)))
    size = maximum - minimum
    height = max(size.z, 0.001)
    scale = 1.72 / height
    center = (minimum + maximum) * 0.5
    for obj in meshes:
        obj.scale *= scale
        obj.location = (obj.location - center) * scale
        obj.location.z -= minimum.z * scale
        bpy.context.view_layer.objects.active = obj
        obj.select_set(True)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        obj.select_set(False)


def add_bone(armature, name, head, tail, parent=None):
    bone = armature.edit_bones.new(name)
    bone.head = head
    bone.tail = tail
    if parent:
        bone.parent = armature.edit_bones.get(parent)
        bone.use_connect = False
    return bone


def create_rig(character):
    armature_data = bpy.data.armatures.new(f"{character}_HumanoidRig")
    rig = bpy.data.objects.new(f"{character}_HumanoidRig", armature_data)
    bpy.context.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    add_bone(armature_data, "Hips", (0, 0, 0.82), (0, 0, 1.00))
    add_bone(armature_data, "Spine", (0, 0, 1.00), (0, 0, 1.24), "Hips")
    add_bone(armature_data, "Chest", (0, 0, 1.24), (0, 0, 1.46), "Spine")
    add_bone(armature_data, "Neck", (0, 0, 1.46), (0, 0, 1.56), "Chest")
    add_bone(armature_data, "Head", (0, 0, 1.56), (0, 0, 1.78), "Neck")
    for side, sign in (("Left", 1), ("Right", -1)):
        add_bone(armature_data, f"{side}Shoulder", (0, 0, 1.42), (0.16 * sign, 0, 1.42), "Chest")
        add_bone(armature_data, f"{side}UpperArm", (0.16 * sign, 0, 1.42), (0.42 * sign, 0, 1.31), f"{side}Shoulder")
        add_bone(armature_data, f"{side}LowerArm", (0.42 * sign, 0, 1.31), (0.62 * sign, 0, 1.18), f"{side}UpperArm")
        add_bone(armature_data, f"{side}Hand", (0.62 * sign, 0, 1.18), (0.72 * sign, 0, 1.12), f"{side}LowerArm")
        add_bone(armature_data, f"{side}UpperLeg", (0.12 * sign, 0, 0.84), (0.14 * sign, 0, 0.48), "Hips")
        add_bone(armature_data, f"{side}LowerLeg", (0.14 * sign, 0, 0.48), (0.14 * sign, 0, 0.14), f"{side}UpperLeg")
        add_bone(armature_data, f"{side}Foot", (0.14 * sign, 0, 0.14), (0.14 * sign, -0.20, 0.08), f"{side}LowerLeg")
    bpy.ops.object.mode_set(mode="OBJECT")
    rig.select_set(False)
    return rig


def bind(meshes, rig):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    try:
        bpy.ops.object.parent_set(type="ARMATURE_AUTO")
    except RuntimeError:
        for obj in meshes:
            modifier = obj.modifiers.new("HumanoidRig", "ARMATURE")
            modifier.object = rig
            obj.parent = rig
    bpy.ops.object.select_all(action="DESELECT")


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


def create_animations(rig):
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
    create_action(rig, "idle", 40, idle)
    create_action(rig, "walk", 30, lambda action, length: cycle(action, length, 0.55))
    create_action(rig, "run", 22, lambda action, length: cycle(action, length, 0.85))
    for name, amplitude in (("gather", -0.95), ("carry", -0.45), ("deposit", -1.05), ("warm", -0.30), ("build", -1.15)):
        create_action(rig, name, 36, lambda action, length, value=amplitude: work(action, length, value))
    rig.animation_data.action = bpy.data.actions.get("idle")


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
        except RuntimeError:
            pass
        copy.select_set(False)
        duplicated.append(copy)
    return duplicated


def export_selected(path, objects, export_format):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    if export_format == "GLB":
        bpy.ops.export_scene.gltf(filepath=str(path), export_format="GLB", use_selection=True, export_animations=True)
    else:
        bpy.ops.export_scene.fbx(filepath=str(path), use_selection=True, add_leaf_bones=False, bake_anim=True)
    bpy.ops.object.select_all(action="DESELECT")


def render_proofs(root, character, objects):
    camera_data = bpy.data.cameras.new("ProofCamera")
    camera = bpy.data.objects.new("ProofCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = 2.25
    camera.location = (0, -5.0, 0.92)
    bpy.context.scene.camera = camera
    light_data = bpy.data.lights.new("Key", "AREA")
    light_data.energy = 1100
    light_data.size = 5
    light = bpy.data.objects.new("Key", light_data)
    light.location = (-3, -4, 6)
    bpy.context.collection.objects.link(light)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE_NEXT" if hasattr(bpy.types, "BLENDER_EEVEE_NEXT") else "BLENDER_EEVEE"
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.color = (0.06, 0.07, 0.09)
    for label, angle in (("front", 0), ("three-quarter", math.radians(45)), ("side", math.radians(90)), ("back", math.radians(180))):
        for obj in objects:
            obj.rotation_euler[2] = angle
        scene.render.filepath = str(root / f"proof_{label}.png")
        bpy.ops.render.render(write_still=True)


def main():
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    clear_scene()
    meshes = import_glb(pathlib.Path(args.input))
    if not meshes:
        raise RuntimeError("Generated GLB contains no mesh objects")
    normalize(meshes)
    rig = create_rig(args.character)
    bind(meshes, rig)
    create_animations(rig)
    lod1 = duplicate_lod(meshes, "_LOD1", 0.62)
    lod2 = duplicate_lod(meshes, "_LOD2", 0.34)
    export_selected(root / f"{args.character}_production.glb", meshes + [rig], "GLB")
    export_selected(root / f"{args.character}_production.fbx", meshes + [rig], "FBX")
    export_selected(root / f"{args.character}_LOD1.glb", lod1 + [rig], "GLB")
    export_selected(root / f"{args.character}_LOD2.glb", lod2 + [rig], "GLB")
    render_proofs(root, args.character, meshes)


if __name__ == "__main__":
    main()
