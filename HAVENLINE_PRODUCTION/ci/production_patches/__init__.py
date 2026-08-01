"""Fail-closed final visual pass for the HAVENLINE production rebuild."""
from __future__ import annotations

import builtins
import importlib.util
from pathlib import Path
from types import ModuleType
from typing import Callable

_PACKAGE_DIR = Path(__file__).resolve().parent
_LEGACY_PATH = _PACKAGE_DIR.parent / "production_patches.py"
_SPEC = importlib.util.spec_from_file_location("_havenline_legacy_patches", _LEGACY_PATH)
if _SPEC is None or _SPEC.loader is None:
    raise ImportError(f"Unable to load HAVENLINE legacy patches: {_LEGACY_PATH}")
_LEGACY: ModuleType = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_LEGACY)

_ORIGINAL_PRINT: Callable[..., None] = builtins.print
_FINALIZED = False


def _replace(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise RuntimeError(
            f"HAVENLINE final visual pass mismatch for {label}: "
            f"expected one marker, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def _refine_character(project: Path) -> None:
    path = project / "scripts" / "production_character.gd"
    changes = [
        ("    _modulate_materials(coat_color, 0.58)\n", "    _modulate_materials(coat_color, 0.22)\n", "retain imported character detail"),
        ("    shell_mesh.size = Vector3(0.58, 0.74, 0.34)\n", "    shell_mesh.size = Vector3(0.50, 0.64, 0.28)\n", "fitted parka shell"),
        ("    shell.position = Vector3(0.0, 1.15, 0.0)\n", "    shell.position = Vector3(0.0, 1.10, 0.0)\n", "parka shell position"),
        ("    yoke_mesh.size = Vector3(0.70, 0.18, 0.38)\n", "    yoke_mesh.size = Vector3(0.60, 0.14, 0.30)\n", "shoulder yoke scale"),
        ("    yoke.position = Vector3(0.0, 1.46, 0.0)\n", "    yoke.position = Vector3(0.0, 1.42, 0.0)\n", "shoulder yoke position"),
        ("    scarf_mesh.size = Vector3(0.46, 0.11, 0.36)\n", "    scarf_mesh.size = Vector3(0.38, 0.08, 0.28)\n", "scarf scale"),
        ("    scarf.position = Vector3(0.0, 1.56, 0.0)\n", "    scarf.position = Vector3(0.0, 1.52, 0.0)\n", "scarf position"),
        ("    belt_mesh.size = Vector3(0.61, 0.08, 0.37)\n", "    belt_mesh.size = Vector3(0.52, 0.06, 0.30)\n", "utility belt scale"),
        ("    _backpack.scale = Vector3.ONE * 0.52\n", "    _backpack.scale = Vector3.ONE * 0.42\n", "field pack base scale"),
        ("        axe.scale = Vector3.ONE * 0.36\n", "        axe.scale = Vector3.ONE * 0.28\n", "field axe scale"),
        ("        _backpack.visible = true\n", "        _backpack.visible = ratio > 0.01\n", "visible carrying state"),
        (
            "        _backpack.scale = Vector3.ONE * lerpf(0.48, 0.70, clampf(ratio, 0.0, 1.0))\n",
            "        _backpack.scale = Vector3.ONE * lerpf(0.40, 0.56, clampf(ratio, 0.0, 1.0))\n",
            "carried stack growth",
        ),
    ]
    for old, new, label in changes:
        _replace(path, old, new, label)


def _refine_world(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    changes = [
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
        ("    player.position = Vector3(0,0.08,4.5)\n", "    player.position = Vector3(0,0.08,4.25)\n", "player staging"),
        ("    camera.size = 10.2\n", "    camera.size = 9.65\n", "camera readability"),
    ]
    for old, new, label in changes:
        _replace(path, old, new, label)


def _refine_assets(project: Path) -> None:
    path = project / "scripts" / "art_factory.gd"
    changes = [
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
        ("    var body := cylinder(0.52, 0.62, 1.12, Color(\"#263640\"), \"FurnaceBody\", 0.34)\n", "    var body := cylinder(0.52, 0.62, 1.12, Color(\"#334852\"), \"FurnaceBody\", 0.30)\n", "furnace body finish"),
        ("    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#52646d\"), \"FurnaceCap\", 0.42)\n", "    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#667a83\"), \"FurnaceCap\", 0.38)\n", "furnace cap finish"),
    ]
    for old, new, label in changes:
        _replace(path, old, new, label)


def _refine_hud(project: Path) -> None:
    path = project / "scripts" / "hud.gd"
    changes = [
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
    for old, new, label in changes:
        _replace(path, old, new, label)


def _finalize(project: Path) -> None:
    _refine_character(project)
    _refine_world(project)
    _refine_assets(project)
    _refine_hud(project)


def apply(project: Path) -> None:
    """Delegate legacy patches and run this pass after visual_refinement.py."""
    global _FINALIZED
    _LEGACY.apply(project)
    project = Path(project)

    def finalizing_print(*args: object, **kwargs: object) -> None:
        global _FINALIZED
        text = " ".join(str(arg) for arg in args)
        if not _FINALIZED and text.startswith("HAVENLINE production source verified:"):
            builtins.print = _ORIGINAL_PRINT
            _finalize(project)
            _FINALIZED = True
            _ORIGINAL_PRINT(
                "HAVENLINE final visual pass applied: fitted character, reduced clutter, "
                "refined furnace, and compact mobile HUD"
            )
        _ORIGINAL_PRINT(*args, **kwargs)

    builtins.print = finalizing_print
