#!/usr/bin/env python3
"""Generate HAVENLINE-owned final scenes from licensed imported assets."""
from __future__ import annotations

from pathlib import Path


def _node(name: str, resource_id: str, position: tuple[float, float, float], rotation: tuple[float, float, float], scale: tuple[float, float, float]) -> str:
    return (
        f'\n[node name="{name}" parent="." instance=ExtResource("{resource_id}")]\n'
        f'position = Vector3({position[0]:.3f}, {position[1]:.3f}, {position[2]:.3f})\n'
        f'rotation = Vector3({rotation[0]:.5f}, {rotation[1]:.5f}, {rotation[2]:.5f})\n'
        f'scale = Vector3({scale[0]:.3f}, {scale[1]:.3f}, {scale[2]:.3f})\n'
    )


def write_final_composites(final_root: Path, log_res: str, backpack_res: str) -> dict[str, str]:
    environment = final_root / "environment"
    environment.mkdir(parents=True, exist_ok=True)

    supply = [
        '[gd_scene load_steps=3 format=3]\n',
        f'[ext_resource type="PackedScene" path="{log_res}" id="1_log"]\n',
        f'[ext_resource type="PackedScene" path="{backpack_res}" id="2_pack"]\n',
        '\n[node name="SupplyCache_Final" type="Node3D"]\n',
    ]
    for index in range(8):
        x = (index % 4 - 1.5) * 0.38
        z = (index // 4 - 0.5) * 0.52
        supply.append(_node(f"FoundationLog{index:02d}", "1_log", (x, 0.16 + (index % 2) * 0.04, z), (0.0, 0.18 * (index % 3), 1.5708), (0.78, 0.78, 0.78)))
    pack_positions = [(-0.42, 0.42, -0.05), (0.08, 0.45, 0.12), (0.48, 0.43, -0.12), (-0.08, 0.72, -0.02)]
    for index, position in enumerate(pack_positions):
        supply.append(_node(f"SupplyPack{index:02d}", "2_pack", position, (0.0, -0.65 + index * 0.42, 0.0), (0.72, 0.72, 0.72)))
    supply.append('\n[node name="CacheLabelAnchor" type="Marker3D" parent="."]\nposition = Vector3(0, 1.25, 0)\n')
    supply_path = environment / "supply_cache.tscn"
    supply_path.write_text("".join(supply), encoding="utf-8")

    fence = [
        '[gd_scene load_steps=2 format=3]\n',
        f'[ext_resource type="PackedScene" path="{log_res}" id="1_log"]\n',
        '\n[node name="LogBarricade_Final" type="Node3D"]\n',
    ]
    for index in range(9):
        x = (index - 4) * 0.34
        y = 0.34 + (index % 2) * 0.23
        fence.append(_node(f"WallLog{index:02d}", "1_log", (x, y, 0.0), (0.0, 0.0, 1.5708), (0.76, 0.76, 0.76)))
    fence.append(_node("CrossBraceLeft", "1_log", (-0.95, 0.62, 0.05), (0.0, 0.0, 0.78), (0.82, 0.82, 0.82)))
    fence.append(_node("CrossBraceRight", "1_log", (0.95, 0.62, 0.05), (0.0, 0.0, -0.78), (0.82, 0.82, 0.82)))
    fence.append(_node("TopRail", "1_log", (0.0, 1.02, 0.02), (0.0, 0.0, 1.5708), (1.05, 1.05, 1.05)))
    fence_path = environment / "log_barricade.tscn"
    fence_path.write_text("".join(fence), encoding="utf-8")

    if supply_path.stat().st_size < 2000 or fence_path.stat().st_size < 2000:
        raise RuntimeError("Generated final asset compositions are unexpectedly small")
    return {
        "crate": "res://" + supply_path.relative_to(final_root.parents[1]).as_posix(),
        "fence": "res://" + fence_path.relative_to(final_root.parents[1]).as_posix(),
    }
