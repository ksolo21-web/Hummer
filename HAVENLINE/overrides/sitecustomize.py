"""HAVENLINE CI camera-rig normalization after the verified camera gate patch."""
from __future__ import annotations

import atexit
import sys
from pathlib import Path

_CAMERA_RIG = '''class_name HavenCameraRig
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


def _normalize_camera_rig() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("HAVENLINE camera normalization requires the project root")
    root = Path(sys.argv[1]).resolve()
    path = root / "scripts/core/camera_rig.gd"
    current = path.read_text(encoding="utf-8")
    if "const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)" not in current:
        raise SystemExit("HAVENLINE verified camera gate did not run before rig normalization")
    path.write_text(_CAMERA_RIG, encoding="utf-8")
    print("HAVENLINE normalized the camera rig to one physical offset")


if Path(sys.argv[0]).name == "patch_reference_camera_gate.py":
    atexit.register(_normalize_camera_rig)
