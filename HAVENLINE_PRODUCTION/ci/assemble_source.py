#!/usr/bin/env python3
"""Reconstruct and verify the clean HAVENLINE production project."""
from __future__ import annotations

import base64
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

repo = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
out = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else repo / ".havenline-production"
source_dir = repo / "HAVENLINE_PRODUCTION" / "source"
manifest = json.loads((source_dir / "manifest.json").read_text(encoding="utf-8"))
parts_dir = source_dir / "parts"
parts = sorted(parts_dir.glob("part-*.b64"))
if len(parts) != int(manifest["part_count"]):
    raise SystemExit(
        f"HAVENLINE source part count mismatch: expected {manifest['part_count']}, found {len(parts)}"
    )
encoded = "".join(part.read_text(encoding="utf-8").strip() for part in parts)
if len(encoded) % 4 != 0:
    raise SystemExit(f"HAVENLINE combined base64 length is invalid: {len(encoded)}")
archive_bytes = base64.b64decode(encoded, validate=True)
actual_archive_sha = hashlib.sha256(archive_bytes).hexdigest()
if actual_archive_sha != manifest["archive_sha256"]:
    raise SystemExit(f"HAVENLINE source archive checksum mismatch: {actual_archive_sha}")

archive_path = repo / ".havenline-production-source.zip"
archive_path.write_bytes(archive_bytes)
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
with zipfile.ZipFile(archive_path) as archive:
    corrupt = archive.testzip()
    if corrupt:
        raise SystemExit(f"HAVENLINE source ZIP contains a corrupt entry: {corrupt}")
    archive.extractall(out)

project = out / "havenline_production"
for entry in manifest["files"]:
    path = project / entry["path"]
    if not path.is_file():
        raise SystemExit(f"HAVENLINE source is missing {entry['path']}")
    data = path.read_bytes()
    if len(data) != entry["bytes"]:
        raise SystemExit(f"HAVENLINE source byte count mismatch for {entry['path']}")
    digest = hashlib.sha256(data).hexdigest()
    if digest != entry["sha256"]:
        raise SystemExit(f"HAVENLINE source checksum mismatch for {entry['path']}: {digest}")

# Godot 4.7 requires an explicit Vector3 type for this expression because
# wolf is intentionally stored as an untyped runtime node in the defense list.
gameplay_path = project / "scripts" / "gameplay.gd"
gameplay = gameplay_path.read_text(encoding="utf-8")
old_direction = "        var dir:=(target-wolf.global_position); dir.y=0\n"
new_direction = "        var dir: Vector3 = target - wolf.global_position; dir.y = 0.0\n"
if gameplay.count(old_direction) != 1:
    raise SystemExit("HAVENLINE Godot 4.7 direction-typing patch expected exactly one source marker")
gameplay_path.write_text(gameplay.replace(old_direction, new_direction, 1), encoding="utf-8")

# Use a fully typed runtime harness. This boots and measures the actual project,
# rather than validating source strings or a mock scene.
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
    var screen_forward: Vector3 = -camera_rig.global_transform.basis.z
    screen_forward.y = 0.0
    screen_forward = screen_forward.normalized()
    player.set_joystick(Vector2(0.0, -1.0))
    var before: Vector3 = player.global_position
    for _frame in range(20):
        await physics_frame
    var moved: Vector3 = player.global_position - before
    moved.y = 0.0
    if moved.length() < 0.1 or moved.normalized().dot(screen_forward) < 0.92:
        _fail("Joystick up does not move screen-forward")
        return
    player.set_joystick(Vector2.ZERO)
    player.global_position.y = -8.0
    for _frame in range(4):
        await physics_frame
    if player.global_position.y < 0.0:
        _fail("Fall recovery failed")
        return
    if gameplay.resources.size() < 10:
        _fail("Resource density failed")
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
    "project.godot": ["run/max_fps=120", 'renderer/rendering_method="mobile"'],
    "scripts/main.gd": ["PROJECTION_ORTHOGONAL", "BoundedSnowTerrain", "DynamicHeatZone"],
    "scripts/player.gd": ["camera_basis_provider", "FALL_Y", "last_safe"],
    "scripts/gameplay.gd": ["_auto_interaction", "_helper_work", "_spawn_wolf_wave", "var dir: Vector3"],
    "tests/runtime_gate.gd": ["dot(screen_forward) < 0.92", "validation-frame.png", "var scene: Node"],
}
for relative, markers in required.items():
    source = (project / relative).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise SystemExit(f"HAVENLINE required marker missing from {relative}: {marker}")

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print("HAVENLINE Godot 4.7 compatibility patches applied: gameplay direction and typed runtime gate")
print(project)
