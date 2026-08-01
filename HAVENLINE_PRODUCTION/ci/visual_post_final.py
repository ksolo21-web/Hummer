#!/usr/bin/env python3
"""Post-final polish for rounded HAVENLINE character and mobile readability."""
from __future__ import annotations

import re
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE post-final '{label}' expected one marker, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def replace_block(path: Path, pattern: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, new, source, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit(f"HAVENLINE post-final '{label}' expected one block, found {count}")
    path.write_text(updated, encoding="utf-8")


def polish_character(project: Path) -> None:
    path = project / "scripts" / "production_character.gd"
    replace_block(
        path,
        r"func _build_winter_kit\(role: String\) -> void:\n.*?\nfunc _attach_animation_libraries\(\) -> void:\n",
        '''func _build_winter_kit(role: String) -> void:
    _winter_kit = Node3D.new()
    _winter_kit.name = "HAVENLINEWinterFieldGear"
    _winter_kit.rotation.y = PI
    add_child(_winter_kit)
    var coat_color := Color("#24506a") if role == "player" else Color("#3b676c")
    _modulate_materials(coat_color, 0.14)

    var shell_mesh := CapsuleMesh.new()
    shell_mesh.radius = 0.27
    shell_mesh.height = 0.62
    shell_mesh.radial_segments = 20
    shell_mesh.rings = 8
    var shell := MeshInstance3D.new()
    shell.name = "RoundedInsulatedParka"
    shell.mesh = shell_mesh
    shell.position = Vector3(0.0, 1.07, 0.0)
    shell.scale = Vector3(1.02, 1.0, 0.68)
    shell.material_override = HavenArtFactory.material(coat_color, 0.90)
    shell.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _winter_kit.add_child(shell)

    var shoulder_mesh := SphereMesh.new()
    shoulder_mesh.radius = 0.32
    shoulder_mesh.height = 0.38
    shoulder_mesh.radial_segments = 20
    shoulder_mesh.rings = 10
    var shoulders := MeshInstance3D.new()
    shoulders.name = "RoundedParkaShoulders"
    shoulders.mesh = shoulder_mesh
    shoulders.position = Vector3(0.0, 1.33, 0.0)
    shoulders.scale = Vector3(1.04, 0.40, 0.66)
    shoulders.material_override = HavenArtFactory.material(coat_color.lightened(0.06), 0.88)
    _winter_kit.add_child(shoulders)

    var scarf_mesh := TorusMesh.new()
    scarf_mesh.inner_radius = 0.15
    scarf_mesh.outer_radius = 0.21
    scarf_mesh.rings = 24
    scarf_mesh.ring_segments = 10
    var scarf := MeshInstance3D.new()
    scarf.name = "ThermalScarf"
    scarf.mesh = scarf_mesh
    scarf.position = Vector3(0.0, 1.50, 0.0)
    scarf.scale = Vector3(1.0, 0.22, 0.74)
    scarf.material_override = HavenArtFactory.material(Color("#dce7e5"), 0.96)
    _winter_kit.add_child(scarf)

    var belt_mesh := TorusMesh.new()
    belt_mesh.inner_radius = 0.27
    belt_mesh.outer_radius = 0.31
    belt_mesh.rings = 24
    belt_mesh.ring_segments = 10
    var belt := MeshInstance3D.new()
    belt.name = "UtilityBelt"
    belt.mesh = belt_mesh
    belt.position = Vector3(0.0, 0.77, 0.0)
    belt.scale = Vector3(1.0, 0.15, 0.66)
    belt.material_override = HavenArtFactory.material(Color("#c96a39"), 0.70, 0.08)
    _winter_kit.add_child(belt)

func _attach_animation_libraries() -> void:
''',
        "rounded winter silhouette",
    )
    changes = [
        ("    _backpack.position = Vector3(0.0, -0.08, 0.17)\n", "    _backpack.position = Vector3(0.0, -0.08, 0.13)\n", "pack position"),
        ("    _backpack.scale = Vector3.ONE * 0.52\n", "    _backpack.scale = Vector3.ONE * 0.31\n", "pack base scale"),
        ("        axe.position = Vector3(0.18, -0.10, 0.20)\n", "        axe.position = Vector3(0.12, -0.08, 0.15)\n", "axe position"),
        ("        axe.scale = Vector3.ONE * 0.36\n", "        axe.scale = Vector3.ONE * 0.21\n", "axe scale"),
        (
            "        _backpack.scale = Vector3.ONE * lerpf(0.40, 0.58, clampf(ratio, 0.0, 1.0))\n",
            "        _backpack.scale = Vector3.ONE * lerpf(0.29, 0.42, clampf(ratio, 0.0, 1.0))\n",
            "carried pack growth",
        ),
    ]
    for old, new, label in changes:
        replace_once(path, old, new, label)


def polish_world(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    changes = [
        ("    for index in range(28):\n", "    for index in range(18):\n", "tree count"),
        ("        var angle := TAU * index / 28.0\n", "        var angle := TAU * index / 18.0\n", "tree distribution"),
        (
            "        var tree := HavenArtFactory.make_tree(randf_range(0.78, 1.18))\n",
            "        var tree := HavenArtFactory.make_tree(randf_range(0.52, 0.78))\n",
            "tree scale",
        ),
        (
            "            tree.position = Vector3(cos(angle) * randf_range(15.7, 18.1), 0, sin(angle) * randf_range(15.7, 18.1))\n",
            "            tree.position = Vector3(cos(angle) * randf_range(15.8, 17.4), 0, sin(angle) * randf_range(15.8, 17.4))\n",
            "tree radius",
        ),
        ("        campfire.position = Vector3(-2.15,0,1.15)\n", "        campfire.position = Vector3(-3.8,0,1.8)\n", "secondary fire position"),
        ("        campfire.scale = Vector3.ONE * 0.64\n", "        campfire.scale = Vector3.ONE * 0.34\n", "secondary fire scale"),
        ("    for index in range(12):\n", "    for index in range(8):\n", "resource count"),
        (
            "        resource.position = Vector3(cos(angle) * randf_range(4.8,9.2),0.0,sin(angle) * randf_range(4.8,9.2))\n",
            "        resource.position = Vector3(cos(angle) * randf_range(6.4,10.8),0.0,sin(angle) * randf_range(6.4,10.8))\n",
            "resource radius",
        ),
        ("    player.position = Vector3(0,0.08,4.5)\n", "    player.position = Vector3(0,0.08,4.15)\n", "player staging"),
        ("    camera.size = 10.2\n", "    camera.size = 9.55\n", "camera scale"),
    ]
    for old, new, label in changes:
        replace_once(path, old, new, label)


def polish_assets(project: Path) -> None:
    path = project / "scripts" / "art_factory.gd"
    changes = [
        (
            "                log.position = Vector3((index % 2 - 0.5) * 0.25, 0.08 + (index / 2) * 0.13, (index / 2 - 0.5) * 0.23)\n",
            "                log.position = Vector3((index % 2 - 0.5) * 0.16, 0.05 + (index / 2) * 0.09, (index / 2 - 0.5) * 0.15)\n",
            "wood spacing",
        ),
        ("                log.scale = Vector3.ONE * 0.40\n", "                log.scale = Vector3.ONE * 0.24\n", "wood scale"),
        (
            "                rock.position = Vector3((index - 1) * 0.23, 0.0, (index % 2) * 0.18)\n",
            "                rock.position = Vector3((index - 1) * 0.15, 0.0, (index % 2) * 0.12)\n",
            "scrap spacing",
        ),
        (
            "                rock.scale = Vector3.ONE * (0.25 + index * 0.025)\n",
            "                rock.scale = Vector3.ONE * (0.18 + index * 0.018)\n",
            "scrap scale",
        ),
        ("            cache.scale = Vector3.ONE * 0.44\n", "            cache.scale = Vector3.ONE * 0.34\n", "supply scale"),
        ("        wolf.scale = Vector3.ONE * 0.66\n", "        wolf.scale = Vector3.ONE * 0.62\n", "wolf scale"),
        ("    var body := cylinder(0.52, 0.62, 1.12, Color(\"#263640\"), \"FurnaceBody\", 0.34)\n", "    var body := cylinder(0.52, 0.62, 1.12, Color(\"#344a55\"), \"FurnaceBody\", 0.30)\n", "furnace body finish"),
        ("    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#52646d\"), \"FurnaceCap\", 0.42)\n", "    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#6a7e87\"), \"FurnaceCap\", 0.38)\n", "furnace cap finish"),
    ]
    for old, new, label in changes:
        replace_once(path, old, new, label)


def polish_hud(project: Path) -> None:
    path = project / "scripts" / "hud.gd"
    changes = [
        ("top.position=Vector2(24,20); top.size=Vector2(1872,64)", "top.position=Vector2(18,14); top.size=Vector2(1884,52)", "top bar"),
        ("_panel(Color(0.025,0.065,0.105,0.88),18)", "_panel(Color(0.025,0.065,0.105,0.82),14)", "top style"),
        ("resource_label.add_theme_font_size_override(\"font_size\",22)", "resource_label.add_theme_font_size_override(\"font_size\",16)", "resource type"),
        ("resource_label.size=Vector2(900,64)", "resource_label.size=Vector2(900,52)", "resource row"),
        ("helper_label.add_theme_font_size_override(\"font_size\",19)", "helper_label.add_theme_font_size_override(\"font_size\",15)", "helper type"),
        ("helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,64)", "helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,52)", "helper row"),
        ("objective_panel.position=Vector2(28,104); objective_panel.size=Vector2(430,100)", "objective_panel.position=Vector2(18,74); objective_panel.size=Vector2(330,70)", "objective panel"),
        ("_panel(Color(0.03,0.08,0.125,0.86),18)", "_panel(Color(0.03,0.08,0.125,0.80),13)", "objective style"),
        ("objective.position=Vector2(18,12); objective.size=Vector2(394,76)", "objective.position=Vector2(13,7); objective.size=Vector2(304,56)", "objective bounds"),
        ("objective.add_theme_font_size_override(\"font_size\",20)", "objective.add_theme_font_size_override(\"font_size\",15)", "objective type"),
        ("furnace_panel.position=Vector2(1462,104); furnace_panel.size=Vector2(430,100)", "furnace_panel.position=Vector2(1572,74); furnace_panel.size=Vector2(330,70)", "furnace panel"),
        ("_panel(Color(0.15,0.065,0.025,0.88),18)", "_panel(Color(0.15,0.065,0.025,0.82),13)", "furnace style"),
        ("furnace_label.position=Vector2(18,12); furnace_label.size=Vector2(394,76)", "furnace_label.position=Vector2(13,7); furnace_label.size=Vector2(304,56)", "furnace bounds"),
        ("furnace_label.add_theme_font_size_override(\"font_size\",20)", "furnace_label.add_theme_font_size_override(\"font_size\",15)", "furnace type"),
        ("joystick.position=Vector2(42,826); joystick.size=Vector2(210,210)", "joystick.position=Vector2(34,864); joystick.size=Vector2(162,162)", "joystick"),
        ("auto.position=Vector2(1672,864); auto.size=Vector2(170,120)", "auto.position=Vector2(1750,900); auto.size=Vector2(122,86)", "auto control"),
        ("auto.add_theme_font_size_override(\"font_size\",20)", "auto.add_theme_font_size_override(\"font_size\",14)", "auto type"),
        ("_panel(Color(0.03,0.12,0.18,0.70),60)", "_panel(Color(0.03,0.12,0.18,0.62),43)", "auto style"),
        ("toast_label.position=Vector2(560,890); toast_label.size=Vector2(800,58)", "toast_label.position=Vector2(650,926); toast_label.size=Vector2(620,40)", "toast"),
        ("toast_label.add_theme_font_size_override(\"font_size\",22)", "toast_label.add_theme_font_size_override(\"font_size\",16)", "toast type"),
    ]
    for old, new, label in changes:
        replace_once(path, old, new, label)


def apply(project: Path) -> None:
    polish_character(project)
    polish_world(project)
    polish_assets(project)
    polish_hud(project)
    print(
        "HAVENLINE post-final polish applied: rounded character, lower clutter, "
        "refined furnace, and compact mobile HUD"
    )
