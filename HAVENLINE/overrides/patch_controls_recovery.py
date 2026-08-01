#!/usr/bin/env python3
"""Fix HAVENLINE camera-relative controls and guarantee bounded-map recovery."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()


def replace_exact(relative: str, old: str, new: str, expected: int = 1) -> None:
    path = root / relative
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != expected:
        raise SystemExit(
            f"HAVENLINE controls/recovery patch refused: expected {expected} match(es) in "
            f"{relative}, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, expected), encoding="utf-8")


replace_exact(
    "scripts/core/camera_rig.gd",
    "    return Basis(right, Vector3.UP, forward)\n",
    "    # Input.get_vector uses negative Y for screen-up/forward. Store camera backward\n"
    "    # on Basis.z so multiplying by that negative value produces camera-forward motion.\n"
    "    return Basis(right, Vector3.UP, -forward)\n",
)

replace_exact(
    "scripts/actors/player_controller.gd",
    "const CAPACITY := 8\n",
    "const CAPACITY := 8\n"
    "const PLAY_BOUNDS := 24.0\n"
    "const FALL_RECOVERY_Y := -2.5\n"
    "const DEFAULT_SPAWN := Vector3(0.0, 0.08, 7.0)\n",
)

replace_exact(
    "scripts/actors/player_controller.gd",
    "var _attack_cooldown := 0.0\nvar _focused: HavenInteractable\n",
    "var _attack_cooldown := 0.0\n"
    "var _focused: HavenInteractable\n"
    "var _last_safe_position := DEFAULT_SPAWN\n"
    "var _safe_position_initialized := false\n"
    "var _recovery_cooldown := 0.0\n",
)

replace_exact(
    "scripts/actors/player_controller.gd",
    "    move_and_slide()\n    if running:\n",
    "    move_and_slide()\n"
    "    _recovery_cooldown = maxf(0.0, _recovery_cooldown - delta)\n"
    "    if _needs_recovery():\n"
    "        _recover_to_playfield()\n"
    "        direction = Vector3.ZERO\n"
    "        running = false\n"
    "    elif is_on_floor() and _inside_play_bounds(global_position):\n"
    "        _last_safe_position = Vector3(global_position.x, maxf(global_position.y, 0.08), global_position.z)\n"
    "        _safe_position_initialized = true\n"
    "    if running:\n",
)

replace_exact(
    "scripts/actors/player_controller.gd",
    "func _update_interaction(delta: float) -> void:\n",
    "func _inside_play_bounds(point: Vector3) -> bool:\n"
    "    return absf(point.x) <= PLAY_BOUNDS and absf(point.z) <= PLAY_BOUNDS and point.y >= -0.75\n\n"
    "func _needs_recovery() -> bool:\n"
    "    return global_position.y < FALL_RECOVERY_Y or absf(global_position.x) > PLAY_BOUNDS + 2.0 or absf(global_position.z) > PLAY_BOUNDS + 2.0\n\n"
    "func _recover_to_playfield() -> void:\n"
    "    if _recovery_cooldown > 0.0:\n"
    "        return\n"
    "    _recovery_cooldown = 0.75\n"
    "    var destination := _last_safe_position if _safe_position_initialized else DEFAULT_SPAWN\n"
    "    destination.x = clampf(destination.x, -PLAY_BOUNDS + 1.0, PLAY_BOUNDS - 1.0)\n"
    "    destination.z = clampf(destination.z, -PLAY_BOUNDS + 1.0, PLAY_BOUNDS - 1.0)\n"
    "    destination.y = maxf(destination.y, 0.08)\n"
    "    global_position = destination\n"
    "    velocity = Vector3.ZERO\n"
    "    if actor:\n"
    "        actor.play_state(\"idle\", true)\n"
    "    if director and director.has_method(\"save_progress\"):\n"
    "        director.save_progress()\n\n"
    "func _update_interaction(delta: float) -> void:\n",
)

replace_exact(
    "scripts/world/environment_assembler.gd",
    "    collision.position.y = -0.22\n    body.add_child(collision)\n",
    "    collision.position.y = -0.22\n"
    "    body.add_child(collision)\n"
    "    # Invisible perimeter collisions keep the vertical slice physically bounded.\n"
    "    _add_boundary_wall(body, Vector3(0.0, 1.5, -26.6), Vector3(54.0, 3.0, 0.9))\n"
    "    _add_boundary_wall(body, Vector3(0.0, 1.5, 26.6), Vector3(54.0, 3.0, 0.9))\n"
    "    _add_boundary_wall(body, Vector3(-26.6, 1.5, 0.0), Vector3(0.9, 3.0, 54.0))\n"
    "    _add_boundary_wall(body, Vector3(26.6, 1.5, 0.0), Vector3(0.9, 3.0, 54.0))\n",
)

replace_exact(
    "scripts/world/environment_assembler.gd",
    "func _dress_outpost() -> void:\n",
    "func _add_boundary_wall(parent: StaticBody3D, position: Vector3, size: Vector3) -> void:\n"
    "    var boundary := CollisionShape3D.new()\n"
    "    var boundary_shape := BoxShape3D.new()\n"
    "    boundary_shape.size = size\n"
    "    boundary.shape = boundary_shape\n"
    "    boundary.position = position\n"
    "    parent.add_child(boundary)\n\n"
    "func _dress_outpost() -> void:\n",
)

replace_exact(
    "scripts/gameplay/gameplay_director.gd",
    "    if position is Array and position.size() == 3:\n        player.position = Vector3(float(position[0]), float(position[1]), float(position[2]))\n",
    "    if position is Array and position.size() == 3:\n"
    "        var loaded_position := Vector3(float(position[0]), float(position[1]), float(position[2]))\n"
    "        if absf(loaded_position.x) <= 24.0 and absf(loaded_position.z) <= 24.0 and loaded_position.y >= -0.75:\n"
    "            player.position = loaded_position\n"
    "        else:\n"
    "            player.position = Vector3(0.0, 0.08, 7.0)\n",
)

replace_exact(
    "tools/runtime_smoke.gd",
    '''    var actor := game.find_child("CharacterVisual", true, false) as HavenCharacterActor\n''',
    '''    var player := players[0] as HavenPlayer\n    var movement_provider := player.camera_basis_provider as HavenCameraRig\n    if not movement_provider:\n        push_error("Runtime gate found no camera-relative movement provider.")\n        get_tree().quit(10)\n        return\n    var camera_forward := -movement_provider.global_transform.basis.z\n    camera_forward.y = 0.0\n    camera_forward = camera_forward.normalized()\n    var mapped_forward := movement_provider.movement_basis() * Vector3(0.0, 0.0, -1.0)\n    mapped_forward.y = 0.0\n    mapped_forward = mapped_forward.normalized()\n    if mapped_forward.dot(camera_forward) < 0.98:\n        push_error("Runtime gate detected inverted forward controls.")\n        get_tree().quit(11)\n        return\n    var safe_before_fall := player.global_position\n    player.global_position = Vector3(safe_before_fall.x, -8.0, safe_before_fall.z)\n    for _recovery_frame in 5:\n        await get_tree().physics_frame\n    if player.global_position.y < -0.75 or absf(player.global_position.x) > 24.0 or absf(player.global_position.z) > 24.0:\n        push_error("Runtime gate detected failed out-of-bounds recovery.")\n        get_tree().quit(12)\n        return\n    var actor := game.find_child("CharacterVisual", true, false) as HavenCharacterActor\n''',
)
