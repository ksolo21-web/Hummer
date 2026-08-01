#!/usr/bin/env python3
"""Patch HAVENLINE runtime boot and smoke validation for Godot 4.7."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()


def replace_exact(relative: str, old: str, new: str) -> None:
    path = root / relative
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE runtime patch refused: expected one match in {relative}, found {count}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


replace_exact(
    "scripts/main.gd",
    '''    player = HavenPlayer.new()\n    player.setup()\n    player.position = Vector3(0.0, 0.05, 5.7)\n    add_child(player)\n    camera_rig = HavenCameraRig.new()\n    camera_rig.setup(player)\n    add_child(camera_rig)\n    player.camera_basis_provider = camera_rig\n    hud = HavenHUD.new()\n    hud.setup()\n    add_child(hud)\n''',
    '''    player = HavenPlayer.new()\n    player.position = Vector3(0.0, 0.05, 5.7)\n    add_child(player)\n    player.setup()\n    camera_rig = HavenCameraRig.new()\n    add_child(camera_rig)\n    camera_rig.setup(player)\n    player.camera_basis_provider = camera_rig\n    hud = HavenHUD.new()\n    add_child(hud)\n    hud.setup()\n''',
)

replace_exact(
    "shaders/snow_ground.gdshader",
    '''uniform float sparkle = 0.16;\n\nfloat hash(vec2 p) {''',
    '''uniform float sparkle = 0.16;\n\nvarying vec3 world_position;\n\nvoid vertex() {\n    world_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;\n}\n\nfloat hash(vec2 p) {''',
)
shader_path = root / "shaders/snow_ground.gdshader"
shader_source = shader_path.read_text(encoding="utf-8")
world_position_uses = shader_source.count("WORLD_POSITION.xz")
if world_position_uses != 4:
    raise SystemExit(
        f"HAVENLINE runtime patch refused: expected four WORLD_POSITION uses, found {world_position_uses}"
    )
shader_path.write_text(shader_source.replace("WORLD_POSITION.xz", "world_position.xz"), encoding="utf-8")

runtime_path = root / "tools/runtime_smoke.gd"
old_runtime = runtime_path.read_text(encoding="utf-8")
expected_runtime = '''extends SceneTree\n\nconst MAIN_SCENE := "res://scenes/main.tscn"\nconst CAPTURE_PATH := "res://build/validation-frame.png"\n\nfunc _init() -> void:\n    call_deferred("_run")\n\nfunc _run() -> void:\n    var packed := load(MAIN_SCENE) as PackedScene\n    if not packed:\n        push_error("Runtime gate could not load the HAVENLINE main scene.")\n        quit(2)\n        return\n    var game := packed.instantiate()\n    root.add_child(game)\n    for _frame in 180:\n        await process_frame\n    if root.has_meta("havenline_asset_failure"):\n        push_error(String(root.get_meta("havenline_asset_failure")))\n        quit(3)\n        return\n    var players := get_nodes_in_group("player")\n    var enemies := get_nodes_in_group("enemy")\n    if players.is_empty():\n        push_error("Runtime gate found no production player actor.")\n        quit(4)\n        return\n    var actor := game.find_child("CharacterVisual", true, false) as HavenCharacterActor\n    if not actor or actor.animation_players.is_empty():\n        push_error("Runtime gate found no imported character animation players.")\n        quit(5)\n        return\n    if actor.current_clip.is_empty():\n        push_error("Runtime gate could not resolve a locomotion/idle animation clip.")\n        quit(6)\n        return\n    var wolf := HavenEnemy.new()\n    wolf.setup("wolf", game)\n    game.add_child(wolf)\n    await process_frame\n    if wolf.creature_animation_players.is_empty() or wolf.creature_current_clip.is_empty():\n        push_error("Runtime gate found a wolf model without a resolved locomotion animation.")\n        quit(9)\n        return\n    wolf.queue_free()\n    await RenderingServer.frame_post_draw\n    var image := root.get_texture().get_image()\n    if image.is_empty() or image.get_width() < 1280 or image.get_height() < 720:\n        push_error("Runtime gate could not capture a valid production frame.")\n        quit(7)\n        return\n    DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build"))\n    var result := image.save_png(CAPTURE_PATH)\n    if result != OK:\n        push_error("Runtime gate could not save the production validation frame.")\n        quit(8)\n        return\n    print("HAVENLINE runtime gate passed: %s, %s" % [actor.animation_debug_summary(), CAPTURE_PATH])\n    quit(0)\n'''
if old_runtime != expected_runtime:
    raise SystemExit("HAVENLINE runtime patch refused: runtime_smoke.gd changed")
runtime_path.write_text('''extends Node\n\nconst MAIN_SCENE := "res://scenes/main.tscn"\nconst CAPTURE_PATH := "res://build/validation-frame.png"\n\nfunc _ready() -> void:\n    call_deferred("_run")\n\nfunc _run() -> void:\n    var packed := load(MAIN_SCENE) as PackedScene\n    if not packed:\n        push_error("Runtime gate could not load the HAVENLINE main scene.")\n        get_tree().quit(2)\n        return\n    var game := packed.instantiate()\n    add_child(game)\n    for _frame in 180:\n        await get_tree().process_frame\n    if get_tree().root.has_meta("havenline_asset_failure"):\n        push_error(String(get_tree().root.get_meta("havenline_asset_failure")))\n        get_tree().quit(3)\n        return\n    var players := get_tree().get_nodes_in_group("player")\n    if players.is_empty():\n        push_error("Runtime gate found no production player actor.")\n        get_tree().quit(4)\n        return\n    var actor := game.find_child("CharacterVisual", true, false) as HavenCharacterActor\n    if not actor or actor.animation_players.is_empty():\n        push_error("Runtime gate found no imported character animation players.")\n        get_tree().quit(5)\n        return\n    if actor.current_clip.is_empty():\n        push_error("Runtime gate could not resolve a locomotion/idle animation clip.")\n        get_tree().quit(6)\n        return\n    var wolf := HavenEnemy.new()\n    wolf.set_physics_process(false)\n    var director := get_tree().get_first_node_in_group("director")\n    wolf.setup("wolf", director if director else game)\n    game.add_child(wolf)\n    await get_tree().process_frame\n    if wolf.creature_animation_players.is_empty() or wolf.creature_current_clip.is_empty():\n        push_error("Runtime gate found a wolf model without a resolved locomotion animation.")\n        get_tree().quit(9)\n        return\n    wolf.queue_free()\n    await RenderingServer.frame_post_draw\n    var image := get_viewport().get_texture().get_image()\n    if image.is_empty() or image.get_width() < 1280 or image.get_height() < 720:\n        push_error("Runtime gate could not capture a valid production frame.")\n        get_tree().quit(7)\n        return\n    DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build"))\n    var result := image.save_png(CAPTURE_PATH)\n    if result != OK:\n        push_error("Runtime gate could not save the production validation frame.")\n        get_tree().quit(8)\n        return\n    print("HAVENLINE runtime gate passed: %s, %s" % [actor.animation_debug_summary(), CAPTURE_PATH])\n    get_tree().quit(0)\n''', encoding="utf-8")

scene_path = root / "tools/runtime_smoke.tscn"
if scene_path.exists():
    raise SystemExit("HAVENLINE runtime patch refused: runtime_smoke.tscn already exists")
scene_path.write_text('''[gd_scene load_steps=2 format=3]\n\n[ext_resource type="Script" path="res://tools/runtime_smoke.gd" id="1_smoke"]\n\n[node name="HAVENLINERuntimeSmoke" type="Node"]\nscript = ExtResource("1_smoke")\n''', encoding="utf-8")
