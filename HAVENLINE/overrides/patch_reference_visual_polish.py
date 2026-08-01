#!/usr/bin/env python3
"""Polish HAVENLINE's compact reference framing, HUD footprint, and validation capture."""
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
            f"HAVENLINE visual polish refused: expected {expected} match(es) in {relative}, "
            f"found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, expected), encoding="utf-8")


replace_exact(
    "scripts/core/camera_rig.gd",
    "const CAMERA_OFFSET := Vector3(0.0, 10.6, 11.2)\n",
    "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n",
)
replace_exact(
    "scripts/core/camera_rig.gd",
    "    camera.projection = Camera3D.PROJECTION_PERSPECTIVE\n    camera.fov = 38.0\n",
    "    camera.projection = Camera3D.PROJECTION_ORTHOGONAL\n    camera.size = 14.8\n",
)

replace_exact(
    "scripts/actors/player_controller.gd",
    "    actor.setup(HavenCharacterActor.Role.PLAYER)\n    actor.attach_backpack()\n",
    "    actor.setup(HavenCharacterActor.Role.PLAYER)\n    actor.scale = Vector3.ONE * 1.12\n    actor.attach_backpack()\n",
)

replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("tent", Vector3(-5.6, 0.0, -2.0), deg_to_rad(16.0), 0.82)\n',
    '    _add_asset("tent", Vector3(-6.6, 0.0, -3.8), deg_to_rad(20.0), 0.55)\n',
)
replace_exact(
    "scripts/world/environment_assembler.gd",
    '    _add_asset("tent", Vector3(5.6, 0.0, -2.0), deg_to_rad(-16.0), 0.82)\n',
    '    _add_asset("tent", Vector3(6.6, 0.0, -3.8), deg_to_rad(-20.0), 0.55)\n',
)

replace_exact(
    "scripts/ui/haven_hud.gd",
    '    _objective_card.name = "ObjectiveCard"\n',
    '    _objective_card.name = "ObjectiveCard"\n    _objective_card.clip_contents = true\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    var column := VBoxContainer.new()\n    column.add_theme_constant_override("separation", 2)\n    _objective_card.add_child(column)\n',
    '    var column := VBoxContainer.new()\n    column.custom_minimum_size = Vector2(286.0, 0.0)\n    column.add_theme_constant_override("separation", 1)\n    _objective_card.add_child(column)\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    objective_title.add_theme_font_size_override("font_size", 21)\n',
    '    objective_title.custom_minimum_size = Vector2(286.0, 24.0)\n    objective_title.add_theme_font_size_override("font_size", 17)\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    objective_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART\n',
    '    objective_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART\n    objective_title.max_lines_visible = 2\n    objective_title.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    objective_subtitle.add_theme_font_size_override("font_size", 13)\n',
    '    objective_subtitle.custom_minimum_size = Vector2(286.0, 28.0)\n    objective_subtitle.add_theme_font_size_override("font_size", 11)\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    objective_subtitle.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART\n',
    '    objective_subtitle.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART\n    objective_subtitle.max_lines_visible = 2\n    objective_subtitle.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '    _status_bar.size = Vector2(viewport_size.x - left - right, 58)\n',
    '    _status_bar.size = Vector2(viewport_size.x - left - right, 48)\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '        _objective_card.position = Vector2(left, top + 70)\n        _objective_card.size = Vector2(viewport_size.x - left - right, 104)\n        _furnace_card.position = Vector2(left, top + 184)\n',
    '        _objective_card.position = Vector2(left, top + 58)\n        _objective_card.size = Vector2(viewport_size.x - left - right, 86)\n        _furnace_card.position = Vector2(left, top + 154)\n',
)
replace_exact(
    "scripts/ui/haven_hud.gd",
    '        _objective_card.position = Vector2(left, top + 70)\n        _objective_card.size = Vector2(minf(440.0, viewport_size.x * 0.38), 100)\n        _furnace_card.size = Vector2(310, 86)\n        _furnace_card.position = Vector2(viewport_size.x - right - _furnace_card.size.x, top + 70)\n',
    '        _objective_card.position = Vector2(left, top + 58)\n        _objective_card.size = Vector2(minf(330.0, viewport_size.x * 0.29), 84)\n        _furnace_card.size = Vector2(270, 72)\n        _furnace_card.position = Vector2(viewport_size.x - right - _furnace_card.size.x, top + 58)\n',
)

replace_exact(
    "tools/runtime_smoke.gd",
    '    for _capture_frame in 10:\n        await get_tree().process_frame\n',
    '    player.actor.play_state("idle", true)\n    for _capture_frame in 150:\n        await get_tree().process_frame\n',
)

for relative, markers in {
    "scripts/core/camera_rig.gd": ["PROJECTION_ORTHOGONAL", "camera.size = 14.8"],
    "scripts/ui/haven_hud.gd": ["Vector2(minf(330.0", "max_lines_visible = 2"],
    "scripts/world/environment_assembler.gd": ["Vector3(-6.6, 0.0, -3.8)", "0.55"],
    "tools/runtime_smoke.gd": ["for _capture_frame in 150"],
}.items():
    source = (root / relative).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise SystemExit(f"HAVENLINE visual polish missing marker {marker!r} in {relative}")

print("HAVENLINE reference visual polish applied")
