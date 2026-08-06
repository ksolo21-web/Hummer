#!/usr/bin/env python3
"""Render HAVENLINE production-character proofs with Cycles on the CPU.

GitHub's Windows hosted runners do not expose a usable WGL/OpenGL context for EEVEE.
This wrapper preserves the exact final-GLB framing, lighting, FBX export, reports, and
validation from render_character_proofs_v4.py while selecting a display-independent
Cycles CPU render before any proof frame is produced.
"""

from __future__ import annotations

import json
import os
import pathlib
import sys

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import render_character_proofs_v4 as implementation

_original_configure_scene = implementation.configure_scene
_render_runtime = {
    "engine": "CYCLES",
    "device": "CPU",
    "samples": None,
    "blenderVersion": implementation.bpy.app.version_string,
    "displayIndependent": True,
    "cyclesAddonEnabled": False,
}


def enable_cycles() -> None:
    """Register Blender's built-in Cycles add-on under --factory-startup."""
    bpy = implementation.bpy
    if "cycles" not in bpy.context.preferences.addons:
        result = bpy.ops.preferences.addon_enable(module="cycles")
        if "FINISHED" not in result:
            raise RuntimeError(f"Unable to enable Blender Cycles add-on: {sorted(result)}")
    _render_runtime["cyclesAddonEnabled"] = "cycles" in bpy.context.preferences.addons
    if not _render_runtime["cyclesAddonEnabled"]:
        raise RuntimeError("Blender reported success but the Cycles add-on is not registered")


def configure_cpu_scene(center, size, minimum):
    scene, camera, target, radius = _original_configure_scene(center, size, minimum)
    enable_cycles()
    try:
        scene.render.engine = "CYCLES"
    except (TypeError, ValueError) as exc:
        available = [
            item.identifier
            for item in implementation.bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
        ]
        raise RuntimeError(
            f"Cycles could not be selected after add-on registration; available engines: {available}"
        ) from exc
    if scene.render.engine != "CYCLES":
        raise RuntimeError(f"Expected CYCLES, Blender selected {scene.render.engine}")
    scene.cycles.device = "CPU"
    scene.cycles.samples = max(8, int(os.environ.get("HAVENLINE_CYCLES_SAMPLES", "24")))
    scene.cycles.use_denoising = True
    scene.render.use_file_extension = True
    _render_runtime["samples"] = scene.cycles.samples
    print(
        "HAVENLINE proof renderer: Cycles CPU, "
        f"{scene.cycles.samples} samples, Blender {implementation.bpy.app.version_string}"
    )
    return scene, camera, target, radius


def write_runtime_to_report(output_directory: str) -> None:
    report_path = pathlib.Path(output_directory) / "proof-render-report.json"
    if not report_path.is_file():
        return
    report = json.loads(report_path.read_text(encoding="utf-8"))
    report["renderRuntime"] = dict(_render_runtime)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


implementation.configure_scene = configure_cpu_scene

if __name__ == "__main__":
    parsed = implementation.parse_args()
    exit_code = implementation.main()
    write_runtime_to_report(parsed.output)
    raise SystemExit(exit_code)
