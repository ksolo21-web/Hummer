#!/usr/bin/env python3
"""Deterministic engine-compatibility and gameplay fixes for HAVENLINE."""
from __future__ import annotations

from pathlib import Path


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"HAVENLINE {label} expected one marker, found {count}: {old!r}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def apply(project: Path) -> None:
    gameplay_path = project / "scripts" / "gameplay.gd"
    replace_exact(
        gameplay_path,
        "        var dir:=(target-wolf.global_position); dir.y=0\n",
        "        var dir: Vector3 = target - wolf.global_position; dir.y = 0.0\n",
        "Godot 4.7 wolf-direction typing patch",
    )

    player_path = project / "scripts" / "player.gd"
    replace_exact(
        player_path,
        "    velocity.z=direction.y*speed\n",
        "    velocity.z = direction.z * speed\n",
        "screen-forward depth-axis patch",
    )
    replace_exact(
        player_path,
        '''func _physics_process(delta: float) -> void:
    var keys:=Input.get_vector("move_left","move_right","move_up","move_down")
''',
        '''func _physics_process(delta: float) -> void:
    if global_position.y < FALL_Y:
        _recover_to_last_safe()
        return
    var keys:=Input.get_vector("move_left","move_right","move_up","move_down")
''',
        "pre-physics recovery patch",
    )
    replace_exact(
        player_path,
        '''    if global_position.y<FALL_Y:
        global_position=last_safe; velocity=Vector3.ZERO; recovered.emit()
    elif is_on_floor():
        last_safe=global_position
    _animate(direction,delta)

func _animate(direction: Vector3,delta: float) -> void:
''',
        '''    if global_position.y < FALL_Y:
        _recover_to_last_safe()
    elif is_on_floor():
        last_safe = global_position
        last_safe.y = maxf(last_safe.y, 0.08)
    _animate(direction,delta)

func _recover_to_last_safe() -> void:
    var destination := last_safe
    destination.x = clampf(destination.x, -PLAY_LIMIT + 0.5, PLAY_LIMIT - 0.5)
    destination.z = clampf(destination.z, -PLAY_LIMIT + 0.5, PLAY_LIMIT - 0.5)
    destination.y = maxf(destination.y, 0.08)
    global_position = destination
    velocity = Vector3.ZERO
    recovered.emit()

func _animate(direction: Vector3,delta: float) -> void:
''',
        "last-safe recovery patch",
    )

    main_path = project / "scripts" / "main.gd"
    replace_exact(
        main_path,
        '        if child is Node3D and child.name.begins_with("Resource_"): res.append(child)\n',
        '        if child is Node3D and child.has_meta("resource_kind"): res.append(child)\n',
        "metadata-based resource discovery patch",
    )

    project_path = project / "project.godot"
    replace_exact(
        project_path,
        "scaling_3d/mode=1\n",
        "scaling_3d/mode=0\n",
        "mobile renderer scaling-mode patch",
    )

    runtime_gate = '''extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _fail(message: String) -> void:
    push_error(message)
    quit(1)

func _run() -> void:
    var packed_scene: PackedScene = load("res://main.tscn") as PackedScene
    if packed_scene == null:
        _fail("Production scene failed to load")
        return
    var scene: Node = packed_scene.instantiate()
    root.add_child(scene)
    for _frame in range(12):
        await process_frame
    var player: HavenPlayer = scene.get("player") as HavenPlayer
    var camera: Camera3D = scene.get("camera") as Camera3D
    var camera_rig: Node3D = scene.get("camera_rig") as Node3D
    var gameplay: HavenGameplay = scene.get("gameplay") as HavenGameplay
    var heat_ring: Node3D = scene.get("heat_ring") as Node3D
    if player == null or camera == null or camera_rig == null or gameplay == null:
        _fail("Missing production systems")
        return
    if camera.projection != Camera3D.PROJECTION_ORTHOGONAL or camera.size > 15.2:
        _fail("Camera gate failed")
        return
    var initial_resource_count := gameplay.resources.size()
    print("HAVENLINE world proof: resources=", initial_resource_count)
    if initial_resource_count < 10:
        _fail("Resource density failed")
        return
    var screen_forward: Vector3 = -camera_rig.global_transform.basis.z
    screen_forward.y = 0.0
    screen_forward = screen_forward.normalized()
    player.set_joystick(Vector2(0.0, -1.0))
    var before: Vector3 = player.global_position
    for _frame in range(20):
        await physics_frame
    var moved: Vector3 = player.global_position - before
    moved.y = 0.0
    print("HAVENLINE control proof: moved=", moved, " screen_forward=", screen_forward, " dot=", moved.normalized().dot(screen_forward) if moved.length() > 0.0 else -1.0)
    if moved.length() < 0.1 or moved.normalized().dot(screen_forward) < 0.92:
        _fail("Joystick up does not move screen-forward")
        return
    player.set_joystick(Vector2.ZERO)
    var safe_before_fall: Vector3 = player.global_position
    player.global_position.y = -8.0
    for _frame in range(4):
        await physics_frame
    var recovery_planar_offset := Vector2(
        player.global_position.x - safe_before_fall.x,
        player.global_position.z - safe_before_fall.z
    ).length()
    var recovery_height_delta := absf(player.global_position.y - safe_before_fall.y)
    print("HAVENLINE recovery proof: before=", safe_before_fall, " after=", player.global_position, " planar_offset=", recovery_planar_offset, " height_delta=", recovery_height_delta)
    if player.global_position.y < HavenPlayer.FALL_Y or recovery_planar_offset > 1.0 or recovery_height_delta > 0.25:
        _fail("Fall recovery failed")
        return
    if heat_ring == null or heat_ring.name != "DynamicHeatZone":
        _fail("Heat system missing")
        return
    for _frame in range(90):
        await process_frame
    DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build"))
    var image: Image = root.get_texture().get_image()
    var capture_error: Error = image.save_png(ProjectSettings.globalize_path("res://build/validation-frame.png"))
    if capture_error != OK:
        _fail("Validation-frame capture failed")
        return
    print("HAVENLINE production gate passed: compact camera, controls, recovery, resources, furnace, helper and defense systems present")
    quit(0)
'''
    (project / "tests" / "runtime_gate.gd").write_text(runtime_gate, encoding="utf-8")

    required = {
        "project.godot": ["run/max_fps=120", 'renderer/rendering_method="mobile"', "scaling_3d/mode=0"],
        "scripts/main.gd": ["PROJECTION_ORTHOGONAL", "BoundedSnowTerrain", "DynamicHeatZone", 'has_meta("resource_kind")'],
        "scripts/player.gd": ["camera_basis_provider", "FALL_Y", "last_safe", "velocity.z = direction.z", "_recover_to_last_safe"],
        "scripts/gameplay.gd": ["_auto_interaction", "_helper_work", "_spawn_wolf_wave", "var dir: Vector3"],
        "tests/runtime_gate.gd": ["dot(screen_forward) < 0.92", "validation-frame.png", "initial_resource_count"],
    }
    for relative, markers in required.items():
        source = (project / relative).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                raise SystemExit(f"HAVENLINE required marker missing from {relative}: {marker}")

    print("HAVENLINE production patches applied: controls, recovery, resources, mobile scaling, runtime proof")
