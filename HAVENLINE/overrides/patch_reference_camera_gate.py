#!/usr/bin/env python3
"""Apply HAVENLINE's close orthographic camera and validate it with projection-correct runtime gates."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()

camera_path = root / "scripts/core/camera_rig.gd"
camera_source = camera_path.read_text(encoding="utf-8")
old_offset = "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n"
new_offset = "const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)\n"
count = camera_source.count(old_offset)
if count != 1:
    raise SystemExit(
        f"HAVENLINE camera gate patch expected one camera-offset match, found {count}: {old_offset!r}"
    )
camera_source = camera_source.replace(old_offset, new_offset, 1)
camera_path.write_text(camera_source, encoding="utf-8")

runtime_path = root / "tools/runtime_smoke.gd"
runtime_source = runtime_path.read_text(encoding="utf-8")
old_gate = '''    if rig.camera.fov > 42.0 or HavenCameraRig.CAMERA_OFFSET.length() > 17.0:\n        _fail("Runtime gate detected a camera too distant for the reference presentation.", 8)\n        return\n'''
new_gate = '''    var physical_camera_distance := rig.global_position.distance_to(player.global_position)\n    if rig.camera.projection != Camera3D.PROJECTION_ORTHOGONAL or rig.camera.size > 15.0 or HavenCameraRig.CAMERA_OFFSET.length() > 10.0 or physical_camera_distance > 10.8:\n        _fail("Runtime gate detected a camera too distant for the reference presentation.", 8)\n        return\n'''
count = runtime_source.count(old_gate)
if count != 1:
    raise SystemExit(
        f"HAVENLINE camera gate patch expected one perspective-only runtime gate, found {count}"
    )
runtime_source = runtime_source.replace(old_gate, new_gate, 1)
runtime_path.write_text(runtime_source, encoding="utf-8")

for marker in (
    "CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)",
    "PROJECTION_ORTHOGONAL",
    "camera.size = 14.8",
):
    if marker not in camera_source:
        raise SystemExit(f"HAVENLINE orthographic camera marker missing: {marker}")
for marker in (
    "physical_camera_distance",
    "rig.camera.projection != Camera3D.PROJECTION_ORTHOGONAL",
    "rig.camera.size > 15.0",
    "physical_camera_distance > 10.8",
):
    if marker not in runtime_source:
        raise SystemExit(f"HAVENLINE projection-correct runtime marker missing: {marker}")

print("HAVENLINE projection-correct physical camera gate applied")
