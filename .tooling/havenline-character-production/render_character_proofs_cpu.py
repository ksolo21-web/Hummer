#!/usr/bin/env python3
"""Render HAVENLINE production-character proofs with Cycles on the CPU.

GitHub's Windows hosted runners do not expose a usable WGL/OpenGL context for EEVEE.
This wrapper preserves the exact final-GLB framing, lighting, FBX export, reports, and
validation from render_character_proofs_v4.py while selecting a display-independent
Cycles CPU render before any proof frame is produced.
"""

from __future__ import annotations

import os
import pathlib
import sys

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import render_character_proofs_v4 as implementation

_original_configure_scene = implementation.configure_scene


def configure_cpu_scene(center, size, minimum):
    scene, camera, target, radius = _original_configure_scene(center, size, minimum)
    engines = {
        item.identifier
        for item in implementation.bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
    }
    if "CYCLES" not in engines:
        raise RuntimeError(f"Cycles is unavailable in this Blender runtime: {sorted(engines)}")
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = max(8, int(os.environ.get("HAVENLINE_CYCLES_SAMPLES", "24")))
    scene.cycles.use_denoising = True
    scene.render.use_file_extension = True
    print(
        "HAVENLINE proof renderer: Cycles CPU, "
        f"{scene.cycles.samples} samples, Blender {implementation.bpy.app.version_string}"
    )
    return scene, camera, target, radius


implementation.configure_scene = configure_cpu_scene

if __name__ == "__main__":
    raise SystemExit(implementation.main())
