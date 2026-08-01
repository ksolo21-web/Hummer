#!/usr/bin/env python3
"""Patch HAVENLINE's Android export preset and mobile texture import settings."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
preset_path = root / "export_presets.cfg"
preset = preset_path.read_text(encoding="utf-8")

for forbidden in ('gradle_build/min_sdk="28"\n', 'gradle_build/target_sdk="35"\n'):
    count = preset.count(forbidden)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE Android export patch refused: expected one preset line {forbidden.strip()!r}, found {count}"
        )
    preset = preset.replace(forbidden, "", 1)

required_preset_lines = (
    'gradle_build/use_gradle_build=false',
    'gradle_build/export_format=0',
    'architectures/armeabi-v7a=false',
    'architectures/arm64-v8a=true',
    'architectures/x86=false',
    'architectures/x86_64=false',
    'package/unique_name="com.kaleb.havenline"',
)
for line in required_preset_lines:
    if preset.count(line) != 1:
        raise SystemExit(f"HAVENLINE Android export patch refused: missing or duplicated {line!r}")
preset_path.write_text(preset, encoding="utf-8")

project_path = root / "project.godot"
project = project_path.read_text(encoding="utf-8")
setting = "textures/vram_compression/import_etc2_astc=true"
if setting in project:
    raise SystemExit("HAVENLINE Android export patch refused: ETC2/ASTC setting already exists")
anchor = 'renderer/rendering_method.web="gl_compatibility"\n'
if project.count(anchor) != 1:
    raise SystemExit("HAVENLINE Android export patch refused: rendering settings anchor changed")
project = project.replace(anchor, anchor + setting + "\n", 1)
project_path.write_text(project, encoding="utf-8")
