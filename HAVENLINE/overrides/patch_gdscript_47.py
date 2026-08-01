#!/usr/bin/env python3
"""Patch HAVENLINE source for strict Godot 4.7 GDScript compatibility."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()


def replace_exact(relative: str, old: str, new: str) -> None:
    path = root / relative
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE compatibility patch refused: expected one match in {relative}, found {count}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


replace_exact(
    "scripts/core/asset_registry.gd",
    '''const REQUIRED_KEYS := PackedStringArray([\n    "player_character", "guard_character", "animation_library_1", "animation_library_2",\n    "wolf", "tent", "campfire", "furnace", "crate", "backpack", "axe", "log", "fence",\n    "pine_a", "pine_b", "rock_a", "rock_b"\n])''',
    '''const REQUIRED_KEYS := [\n    "player_character", "guard_character", "animation_library_1", "animation_library_2",\n    "wolf", "tent", "campfire", "furnace", "crate", "backpack", "axe", "log", "fence",\n    "pine_a", "pine_b", "rock_a", "rock_b"\n]''',
)

replace_exact(
    "scripts/actors/character_actor.gd",
    '''    if body_root is AnimationPlayer:\n        animation_players.append(body_root)\n''',
    "",
)

replace_exact(
    "scripts/actors/character_actor.gd",
    '''                var source := mesh_node.get_active_material(surface)\n                if source is BaseMaterial3D:\n                    var copy := source.duplicate() as BaseMaterial3D\n''',
    '''                var source: Material = mesh_node.get_active_material(surface)\n                if source is BaseMaterial3D:\n                    var copy := source.duplicate() as BaseMaterial3D\n''',
)

replace_exact(
    "scripts/actors/enemy.gd",
    '''    if creature_root is AnimationPlayer:\n        creature_animation_players.append(creature_root)\n''',
    "",
)

replace_exact(
    "project.godot",
    "scaling_3d/mode=1\n",
    "scaling_3d/mode=0\n",
)
