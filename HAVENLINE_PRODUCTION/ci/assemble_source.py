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
archive_bytes = base64.b64decode((source_dir / "HAVENLINE-production-rebuild-source.zip.b64").read_text(encoding="utf-8"))
actual_archive_sha = hashlib.sha256(archive_bytes).hexdigest()
if actual_archive_sha != manifest["archive_sha256"]:
    raise SystemExit(f"HAVENLINE source archive checksum mismatch: {actual_archive_sha}")

archive_path = repo / ".havenline-production-source.zip"
archive_path.write_bytes(archive_bytes)
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
with zipfile.ZipFile(archive_path) as archive:
    archive.testzip()
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

# The visual gate captures the exact rendered project after movement/recovery tests.
runtime_gate = project / "tests" / "runtime_gate.gd"
text = runtime_gate.read_text(encoding="utf-8")
old = '    print("HAVENLINE production gate passed: compact camera, controls, recovery, resources, furnace, helper and defense systems present")\n    quit(0)\n'
new = '    for i in range(90): await process_frame\n    DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build"))\n    var image := root.get_texture().get_image()\n    var capture_error := image.save_png(ProjectSettings.globalize_path("res://build/validation-frame.png"))\n    if capture_error != OK: _fail("Validation-frame capture failed"); return\n    print("HAVENLINE production gate passed: compact camera, controls, recovery, resources, furnace, helper and defense systems present")\n    quit(0)\n'
if text.count(old) != 1:
    raise SystemExit("HAVENLINE visual-capture patch expected one runtime-gate marker")
runtime_gate.write_text(text.replace(old, new, 1), encoding="utf-8")

required = {
    "project.godot": ["run/max_fps=120", 'renderer/rendering_method="mobile"'],
    "scripts/main.gd": ["PROJECTION_ORTHOGONAL", "BoundedSnowTerrain", "DynamicHeatZone"],
    "scripts/player.gd": ["camera_basis_provider", "FALL_Y", "last_safe"],
    "scripts/gameplay.gd": ["_auto_interaction", "_helper_work", "_spawn_wolf_wave"],
    "tests/runtime_gate.gd": ["dot(screen_forward) < 0.92", "validation-frame.png"],
}
for relative, markers in required.items():
    source = (project / relative).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise SystemExit(f"HAVENLINE required marker missing from {relative}: {marker}")

print(f"HAVENLINE production source verified: {manifest['file_count']} files, {manifest['archive_sha256']}")
print(project)
