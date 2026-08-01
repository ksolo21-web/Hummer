"""Final-stage HAVENLINE production-patch wrapper.

Python resolves this package before the legacy sibling ``production_patches.py``.
The wrapper delegates the original source patch, then installs a fail-closed
finalization hook. The hook runs visual pass 4 only after the checksum-verified
source, imported asset scene, and all existing generated-source repairs finish.
"""
from __future__ import annotations

import builtins
import importlib.util
import re
from pathlib import Path
from types import ModuleType
from typing import Callable

_THIS_DIR = Path(__file__).resolve().parent
_LEGACY_PATH = _THIS_DIR.parent / "production_patches.py"
_LEGACY_SPEC = importlib.util.spec_from_file_location(
    "_havenline_legacy_production_patches", _LEGACY_PATH
)
if _LEGACY_SPEC is None or _LEGACY_SPEC.loader is None:
    raise ImportError(f"Unable to load HAVENLINE legacy patches: {_LEGACY_PATH}")
_LEGACY: ModuleType = importlib.util.module_from_spec(_LEGACY_SPEC)
_LEGACY_SPEC.loader.exec_module(_LEGACY)

_FINALIZED = False
_ORIGINAL_PRINT: Callable[..., None] = builtins.print


def _replace_once(path: Path, original: str, replacement: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(original)
    if count != 1:
        raise RuntimeError(
            f"HAVENLINE visual pass 4 target mismatch for {label}: "
            f"expected 1 occurrence, found {count}: {original!r}"
        )
    path.write_text(source.replace(original, replacement, 1), encoding="utf-8")


def _regex_once(path: Path, pattern: str, replacement: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, source, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(
            f"HAVENLINE visual pass 4 regex mismatch for {label}: "
            f"expected 1 occurrence, found {count}: {pattern!r}"
        )
    path.write_text(updated, encoding="utf-8")


def _polish_character(project: Path) -> None:
    path = project / "scripts" / "production_character.gd"
    replacements = [
        ("    coat_mesh.top_radius = 0.39\n", "    coat_mesh.top_radius = 0.31\n", "parka shoulder radius"),
        ("    coat_mesh.bottom_radius = 0.50\n", "    coat_mesh.bottom_radius = 0.39\n", "parka hem radius"),
        ("    coat_mesh.height = 0.90\n", "    coat_mesh.height = 0.72\n", "parka height"),
        ("    coat.position = Vector3(0.0, 1.18, 0.0)\n", "    coat.position = Vector3(0.0, 1.08, 0.0)\n", "parka position"),
        ("    coat.scale = Vector3(1.0, 1.0, 0.78)\n", "    coat.scale = Vector3(0.94, 1.0, 0.66)\n", "parka depth"),
        ("    collar_mesh.inner_radius = 0.26\n", "    collar_mesh.inner_radius = 0.18\n", "collar inner radius"),
        ("    collar_mesh.outer_radius = 0.43\n", "    collar_mesh.outer_radius = 0.29\n", "collar outer radius"),
        ("    collar.position = Vector3(0.0, 1.64, 0.0)\n", "    collar.position = Vector3(0.0, 1.51, 0.0)\n", "collar position"),
        ("    collar.scale = Vector3(1.0, 0.42, 0.88)\n", "    collar.scale = Vector3(1.0, 0.28, 0.76)\n", "collar profile"),
        ("    belt_mesh.inner_radius = 0.43\n", "    belt_mesh.inner_radius = 0.32\n", "belt inner radius"),
        ("    belt_mesh.outer_radius = 0.49\n", "    belt_mesh.outer_radius = 0.37\n", "belt outer radius"),
        ("    belt.position = Vector3(0.0, 0.78, 0.0)\n", "    belt.position = Vector3(0.0, 0.76, 0.0)\n", "belt position"),
        ("    belt.scale = Vector3(1.0, 0.26, 0.78)\n", "    belt.scale = Vector3(1.0, 0.18, 0.66)\n", "belt profile"),
        ("    _backpack.scale = Vector3.ONE * 0.64\n", "    _backpack.scale = Vector3.ONE * 0.46\n", "field pack base scale"),
        ("        axe.scale = Vector3.ONE * 0.46\n", "        axe.scale = Vector3.ONE * 0.32\n", "field axe scale"),
        ("        _backpack.visible = true\n", "        _backpack.visible = ratio > 0.01\n", "carrying visibility"),
        (
            "        _backpack.scale = Vector3.ONE * lerpf(0.60, 0.84, clampf(ratio, 0.0, 1.0))\n",
            "        _backpack.scale = Vector3.ONE * lerpf(0.42, 0.58, clampf(ratio, 0.0, 1.0))\n",
            "carried stack growth",
        ),
    ]
    for original, replacement, label in replacements:
        _replace_once(path, original, replacement, label)


def _polish_world(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    replacements = [
        ("    for index in range(28):\n", "    for index in range(20):\n", "perimeter tree count"),
        ("        var angle := TAU * index / 28.0\n", "        var angle := TAU * index / 20.0\n", "perimeter tree distribution"),
        (
            "        var tree := HavenArtFactory.make_tree(randf_range(0.78, 1.18))\n",
            "        var tree := HavenArtFactory.make_tree(randf_range(0.54, 0.82))\n",
            "perimeter tree scale",
        ),
        (
            "            tree.position = Vector3(cos(angle) * randf_range(15.7, 18.1), 0, sin(angle) * randf_range(15.7, 18.1))\n",
            "            tree.position = Vector3(cos(angle) * randf_range(15.6, 17.3), 0, sin(angle) * randf_range(15.6, 17.3))\n",
            "perimeter tree radius",
        ),
        ("        campfire.position = Vector3(-2.15,0,1.15)\n", "        campfire.position = Vector3(-3.7,0,1.75)\n", "secondary fire position"),
        ("        campfire.scale = Vector3.ONE * 0.64\n", "        campfire.scale = Vector3.ONE * 0.38\n", "secondary fire scale"),
        ("    for index in range(18):\n", "    for index in range(10):\n", "resource cluster count"),
        (
            "        resource.position = Vector3(cos(angle) * randf_range(3.8,8.4),0.0,sin(angle) * randf_range(3.8,8.4))\n",
            "        resource.position = Vector3(cos(angle) * randf_range(5.8,10.4),0.0,sin(angle) * randf_range(5.8,10.4))\n",
            "resource cluster radius",
        ),
        ("    camera.size = 10.2\n", "    camera.size = 9.65\n", "camera readability"),
    ]
    for original, replacement, label in replacements:
        _replace_once(path, original, replacement, label)


def _polish_assets(project: Path) -> None:
    path = project / "scripts" / "art_factory.gd"
    replacements = [
        (
            "                log.position = Vector3((index % 2 - 0.5) * 0.38, 0.10 + (index / 2) * 0.20, (index / 2 - 0.5) * 0.36)\n",
            "                log.position = Vector3((index % 2 - 0.5) * 0.24, 0.08 + (index / 2) * 0.13, (index / 2 - 0.5) * 0.22)\n",
            "wood stack spacing",
        ),
        ("                log.scale = Vector3.ONE * 0.68\n", "                log.scale = Vector3.ONE * 0.38\n", "wood stack scale"),
        (
            "                rock.position = Vector3((index - 1) * 0.35, 0.0, (index % 2) * 0.28)\n",
            "                rock.position = Vector3((index - 1) * 0.22, 0.0, (index % 2) * 0.17)\n",
            "scrap spacing",
        ),
        (
            "                rock.scale = Vector3.ONE * (0.42 + index * 0.04)\n",
            "                rock.scale = Vector3.ONE * (0.24 + index * 0.025)\n",
            "scrap scale",
        ),
        ("            cache.scale = Vector3.ONE * 0.70\n", "            cache.scale = Vector3.ONE * 0.44\n", "supply resource scale"),
        ("        wolf.scale = Vector3.ONE * 0.82\n", "        wolf.scale = Vector3.ONE * 0.64\n", "wolf scale"),
        ("    var body := cylinder(0.60, 0.72, 1.24, Color(\"#263640\"), \"FurnaceBody\", 0.34)\n", "    var body := cylinder(0.54, 0.64, 1.12, Color(\"#334852\"), \"FurnaceBody\", 0.30)\n", "furnace body proportion"),
        ("    body.position = Vector3(0.0, 0.64, 0.0)\n", "    body.position = Vector3(0.0, 0.58, 0.0)\n", "furnace body position"),
        ("    var cap := cylinder(0.76, 0.70, 0.18, Color(\"#4b5e68\"), \"FurnaceCap\", 0.42)\n", "    var cap := cylinder(0.65, 0.62, 0.16, Color(\"#60727a\"), \"FurnaceCap\", 0.38)\n", "furnace cap proportion"),
        ("    cap.position = Vector3(0.0, 1.29, 0.0)\n", "    cap.position = Vector3(0.0, 1.17, 0.0)\n", "furnace cap position"),
        ("    var chimney := cylinder(0.16, 0.22, 1.20, Color(\"#202d34\"), \"FurnaceChimney\", 0.52)\n", "    var chimney := cylinder(0.13, 0.18, 0.92, Color(\"#283941\"), \"FurnaceChimney\", 0.48)\n", "furnace chimney proportion"),
        ("    chimney.position = Vector3(0.0, 1.92, -0.10)\n", "    chimney.position = Vector3(0.0, 1.67, -0.08)\n", "furnace chimney position"),
    ]
    for original, replacement, label in replacements:
        _replace_once(path, original, replacement, label)



def _polish_hud(project: Path) -> None:
    path = project / "scripts" / "hud.gd"
    replacements = [
        ("top.position=Vector2(24,20); top.size=Vector2(1872,64)", "top.position=Vector2(18,14); top.size=Vector2(1884,54)", "top bar footprint"),
        ("_panel(Color(0.025,0.065,0.105,0.88),18)", "_panel(Color(0.025,0.065,0.105,0.82),15)", "top bar style"),
        ("resource_label.add_theme_font_size_override(\"font_size\",22)", "resource_label.add_theme_font_size_override(\"font_size\",17)", "resource typography"),
        ("resource_label.size=Vector2(900,64)", "resource_label.size=Vector2(900,54)", "resource row height"),
        ("helper_label.add_theme_font_size_override(\"font_size\",19)", "helper_label.add_theme_font_size_override(\"font_size\",16)", "helper typography"),
        ("helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,64)", "helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,54)", "helper row height"),
        ("objective_panel.position=Vector2(28,104); objective_panel.size=Vector2(430,100)", "objective_panel.position=Vector2(18,78); objective_panel.size=Vector2(348,76)", "objective panel footprint"),
        ("_panel(Color(0.03,0.08,0.125,0.86),18)", "_panel(Color(0.03,0.08,0.125,0.80),14)", "objective panel style"),
        ("objective.position=Vector2(18,12); objective.size=Vector2(394,76)", "objective.position=Vector2(14,8); objective.size=Vector2(320,60)", "objective bounds"),
        ("objective.add_theme_font_size_override(\"font_size\",20)", "objective.add_theme_font_size_override(\"font_size\",16)", "objective typography"),
        ("furnace_panel.position=Vector2(1462,104); furnace_panel.size=Vector2(430,100)", "furnace_panel.position=Vector2(1554,78); furnace_panel.size=Vector2(348,76)", "furnace panel footprint"),
        ("_panel(Color(0.15,0.065,0.025,0.88),18)", "_panel(Color(0.15,0.065,0.025,0.82),14)", "furnace panel style"),
        ("furnace_label.position=Vector2(18,12); furnace_label.size=Vector2(394,76)", "furnace_label.position=Vector2(14,8); furnace_label.size=Vector2(320,60)", "furnace label bounds"),
        ("furnace_label.add_theme_font_size_override(\"font_size\",20)", "furnace_label.add_theme_font_size_override(\"font_size\",16)", "furnace typography"),
        ("joystick.position=Vector2(42,826); joystick.size=Vector2(210,210)", "joystick.position=Vector2(34,858); joystick.size=Vector2(172,172)", "joystick footprint"),
        ("auto.position=Vector2(1672,864); auto.size=Vector2(170,120)", "auto.position=Vector2(1744,892); auto.size=Vector2(128,92)", "auto interaction footprint"),
        ("auto.add_theme_font_size_override(\"font_size\",20)", "auto.add_theme_font_size_override(\"font_size\",15)", "auto interaction typography"),
        ("_panel(Color(0.03,0.12,0.18,0.70),60)", "_panel(Color(0.03,0.12,0.18,0.62),46)", "auto interaction style"),
        ("toast_label.position=Vector2(560,890); toast_label.size=Vector2(800,58)", "toast_label.position=Vector2(630,920); toast_label.size=Vector2(660,44)", "toast footprint"),
        ("toast_label.add_theme_font_size_override(\"font_size\",22)", "toast_label.add_theme_font_size_override(\"font_size\",17)", "toast typography"),
    ]
    for original, replacement, label in replacements:
        _replace_once(path, original, replacement, label)


def _apply_visual_pass_4(project: Path) -> None:
    _polish_character(project)
    _polish_world(project)
    _polish_assets(project)
    _polish_hud(project)


def apply(project: Path) -> None:
    """Run the legacy patch and install the final visual-pass hook."""
    global _FINALIZED
    _LEGACY.apply(project)
    project = Path(project)

    def finalizing_print(*args: object, **kwargs: object) -> None:
        global _FINALIZED
        text = " ".join(str(arg) for arg in args)
        if not _FINALIZED and text.startswith("HAVENLINE production source verified:"):
            builtins.print = _ORIGINAL_PRINT
            _apply_visual_pass_4(project)
            _FINALIZED = True
            _ORIGINAL_PRINT(
                "HAVENLINE visual pass 4 applied: readable winter character, "
                "compact resources, refined furnace, and reduced HUD"
            )
        _ORIGINAL_PRINT(*args, **kwargs)

    builtins.print = finalizing_print
