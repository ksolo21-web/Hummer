#!/usr/bin/env python3
"""Final readability polish for the HAVENLINE rendered production scene."""
from __future__ import annotations

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE final visual polish '{label}' expected one marker, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def apply(project: Path) -> None:
    character = project / "scripts" / "production_character.gd"
    character_changes = [
        (
            "    shell_mesh.size = Vector3(0.58, 0.74, 0.34)\n",
            "    shell_mesh.size = Vector3(0.50, 0.66, 0.30)\n",
            "fitted parka shell",
        ),
        (
            "    shell.position = Vector3(0.0, 1.15, 0.0)\n",
            "    shell.position = Vector3(0.0, 1.10, 0.0)\n",
            "parka shell position",
        ),
        (
            "    yoke_mesh.size = Vector3(0.70, 0.18, 0.38)\n",
            "    yoke_mesh.size = Vector3(0.58, 0.12, 0.32)\n",
            "shoulder yoke size",
        ),
        (
            "    yoke.position = Vector3(0.0, 1.46, 0.0)\n",
            "    yoke.position = Vector3(0.0, 1.40, 0.0)\n",
            "shoulder yoke position",
        ),
        (
            "    scarf_mesh.size = Vector3(0.46, 0.11, 0.36)\n",
            "    scarf_mesh.size = Vector3(0.30, 0.055, 0.24)\n",
            "thermal scarf size",
        ),
        (
            "    scarf.position = Vector3(0.0, 1.56, 0.0)\n",
            "    scarf.position = Vector3(0.0, 1.48, 0.0)\n",
            "thermal scarf position",
        ),
        (
            "    belt_mesh.size = Vector3(0.61, 0.08, 0.37)\n",
            "    belt_mesh.size = Vector3(0.52, 0.06, 0.31)\n",
            "utility belt size",
        ),
        (
            "        _backpack.visible = true\n",
            "        _backpack.visible = ratio > 0.01\n",
            "pack only while carrying",
        ),
        (
            "        _backpack.scale = Vector3.ONE * lerpf(0.48, 0.70, clampf(ratio, 0.0, 1.0))\n",
            "        _backpack.scale = Vector3.ONE * lerpf(0.40, 0.58, clampf(ratio, 0.0, 1.0))\n",
            "carried pack growth",
        ),
    ]
    for old, new, label in character_changes:
        replace_once(character, old, new, label)

    art = project / "scripts" / "art_factory.gd"
    art_changes = [
        (
            "                log.position = Vector3((index % 2 - 0.5) * 0.38, 0.10 + (index / 2) * 0.20, (index / 2 - 0.5) * 0.36)\n",
            "                log.position = Vector3((index % 2 - 0.5) * 0.25, 0.08 + (index / 2) * 0.13, (index / 2 - 0.5) * 0.23)\n",
            "wood stack spacing",
        ),
        (
            "                log.scale = Vector3.ONE * 0.68\n",
            "                log.scale = Vector3.ONE * 0.40\n",
            "wood stack scale",
        ),
        (
            "                rock.position = Vector3((index - 1) * 0.35, 0.0, (index % 2) * 0.28)\n",
            "                rock.position = Vector3((index - 1) * 0.23, 0.0, (index % 2) * 0.18)\n",
            "scrap spacing",
        ),
        (
            "                rock.scale = Vector3.ONE * (0.42 + index * 0.04)\n",
            "                rock.scale = Vector3.ONE * (0.25 + index * 0.025)\n",
            "scrap scale",
        ),
        (
            "            cache.scale = Vector3.ONE * 0.70\n",
            "            cache.scale = Vector3.ONE * 0.44\n",
            "supply node scale",
        ),
        (
            "        wolf.scale = Vector3.ONE * 0.82\n",
            "        wolf.scale = Vector3.ONE * 0.66\n",
            "wolf scale",
        ),
    ]
    for old, new, label in art_changes:
        replace_once(art, old, new, label)

    world = project / "scripts" / "main.gd"
    world_changes = [
        (
            "    for index in range(18):\n",
            "    for index in range(12):\n",
            "resource cluster count",
        ),
        (
            "        resource.position = Vector3(cos(angle) * randf_range(3.8,8.4),0.0,sin(angle) * randf_range(3.8,8.4))\n",
            "        resource.position = Vector3(cos(angle) * randf_range(4.8,9.2),0.0,sin(angle) * randf_range(4.8,9.2))\n",
            "resource cluster clearance",
        ),
    ]
    for old, new, label in world_changes:
        replace_once(world, old, new, label)

    required = {
        "scripts/production_character.gd": [
            "shell_mesh.size = Vector3(0.50, 0.66, 0.30)",
            "_backpack.visible = ratio > 0.01",
        ],
        "scripts/art_factory.gd": [
            "log.scale = Vector3.ONE * 0.40",
            "cache.scale = Vector3.ONE * 0.44",
        ],
        "scripts/main.gd": ["for index in range(12)", "randf_range(4.8,9.2)"],
    }
    for relative, markers in required.items():
        source = (project / relative).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                raise SystemExit(f"HAVENLINE final visual marker missing from {relative}: {marker}")

    print("HAVENLINE final visual polish applied: readable character silhouette and compact resource scale")
