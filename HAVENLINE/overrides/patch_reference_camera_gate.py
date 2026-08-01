#!/usr/bin/env python3
"""Normalize HAVENLINE to one close orthographic camera transform and validate it correctly."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
camera_path = root / "scripts/core/camera_rig.gd"
camera_source = camera_path.read_text(encoding="utf-8")
old_offset = "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n"
if camera_source.count(old_offset) != 1:
    raise SystemExit(
        "HAVENLINE camera normalization expected exactly one polished 10.2 camera marker"
    )
if "PROJECTION_ORTHOGONAL" not in camera_source or "camera.size = 14.8" not in camera_source:
    raise SystemExit("HAVENLINE orthographic framing markers missing before normalization")

normalized_camera_rig = '''class_name HavenCameraRig
extends Node3D

const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)
const FOCUS_OFFSET := Vector3(0.0, 0.95, 0.0)

var target: Node3D
var camera: Camera3D
var _shake_time := 0.0
var _shake_strength := 0.0

func setup(follow_target: Node3D) -> void:
    target = follow_target
    camera = Camera3D.new()
    camera.name = "GameplayCamera"
    camera.projection = Camera3D.PROJECTION_ORTHOGONAL
    camera.size = 14.8
    camera.near = 0.12
    camera.far = 90.0
    camera.current = true
    add_child(camera)
    camera.position = Vector3.ZERO
    _update_camera(0.016, true)

func _process(delta: float) -> void:
    _update_camera(delta, false)

func _update_camera(delta: float, instant: bool) -> void:
    if not target:
        return
    var desired := target.global_position + CAMERA_OFFSET
    if instant:
        global_position = desired
    else:
        global_position = global_position.lerp(desired, 1.0 - exp(-delta * 6.8))
    look_at(target.global_position + FOCUS_OFFSET, Vector3.UP)
    if _shake_time > 0.0 and SettingsStore.camera_shake:
        _shake_time -= delta
        camera.position = Vector3(randf_range(-1.0, 1.0), randf_range(-1.0, 1.0), 0.0) * _shake_strength
        _shake_strength *= exp(-delta * 8.0)
    else:
        camera.position = Vector3.ZERO

func movement_direction(input_vector: Vector2) -> Vector3:
    var forward := -global_transform.basis.z
    forward.y = 0.0
    forward = forward.normalized()
    var right := global_transform.basis.x
    right.y = 0.0
    right = right.normalized()
    var direction := right * input_vector.x + forward * -input_vector.y
    if direction.length_squared() <= 0.0001:
        return Vector3.ZERO
    return direction.normalized()

func movement_basis() -> Basis:
    var forward := -global_transform.basis.z
    forward.y = 0.0
    forward = forward.normalized()
    var right := global_transform.basis.x
    right.y = 0.0
    right = right.normalized()
    return Basis(right, Vector3.UP, -forward)

func shake(strength := 0.10, duration := 0.22) -> void:
    _shake_strength = maxf(_shake_strength, strength)
    _shake_time = maxf(_shake_time, duration)
'''
camera_path.write_text(normalized_camera_rig, encoding="utf-8")

runtime_path = root / "tools/runtime_smoke.gd"
runtime_source = runtime_path.read_text(encoding="utf-8")
old_gate = '''    if rig.camera.fov > 42.0 or HavenCameraRig.CAMERA_OFFSET.length() > 17.0:\n        _fail("Runtime gate detected a camera too distant for the reference presentation.", 8)\n        return\n'''
new_gate = '''    var physical_camera_distance := rig.camera.global_position.distance_to(player.global_position)\n    if rig.camera.projection != Camera3D.PROJECTION_ORTHOGONAL or rig.camera.size > 15.0 or HavenCameraRig.CAMERA_OFFSET.length() > 10.0 or physical_camera_distance > 10.8:\n        _fail("Runtime gate detected a camera too distant for the reference presentation.", 8)\n        return\n'''
if runtime_source.count(old_gate) != 1:
    raise SystemExit("HAVENLINE camera normalization expected one perspective-only runtime gate")
runtime_source = runtime_source.replace(old_gate, new_gate, 1)
runtime_path.write_text(runtime_source, encoding="utf-8")

camera_required = (
    "const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)",
    "camera.projection = Camera3D.PROJECTION_ORTHOGONAL",
    "camera.size = 14.8",
    "func movement_direction(input_vector: Vector2) -> Vector3:",
    "func movement_basis() -> Basis:",
    "camera.position = Vector3.ZERO",
)
written_camera = camera_path.read_text(encoding="utf-8")
for marker in camera_required:
    if marker not in written_camera:
        raise SystemExit(f"HAVENLINE normalized camera rig missing marker: {marker}")
for marker in (
    "rig.camera.global_position.distance_to(player.global_position)",
    "rig.camera.projection != Camera3D.PROJECTION_ORTHOGONAL",
    "physical_camera_distance > 10.8",
):
    if marker not in runtime_source:
        raise SystemExit(f"HAVENLINE projection-correct runtime marker missing: {marker}")
print("HAVENLINE normalized the camera rig to one physical offset and installed the orthographic runtime gate")
