#!/usr/bin/env python3
"""Final scene-readability pass for the HAVENLINE production vertical slice."""
from __future__ import annotations

import re
from pathlib import Path


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE final visual pass '{label}' expected one marker, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def replace_block(path: Path, pattern: str, replacement: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, source, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE final visual pass '{label}' expected one block, found {count}"
        )
    path.write_text(updated, encoding="utf-8")


def refine_character(project: Path) -> None:
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
    _modulate_materials(coat_color, 0.16)

    var shell_mesh := CapsuleMesh.new()
    shell_mesh.radius = 0.28
    shell_mesh.height = 0.64
    shell_mesh.radial_segments = 20
    shell_mesh.rings = 8
    var shell := MeshInstance3D.new()
    shell.name = "RoundedInsulatedParka"
    shell.mesh = shell_mesh
    shell.position = Vector3(0.0, 1.08, 0.0)
    shell.scale = Vector3(1.04, 1.0, 0.70)
    shell.material_override = HavenArtFactory.material(coat_color, 0.90)
    shell.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _winter_kit.add_child(shell)

    var yoke_mesh := SphereMesh.new()
    yoke_mesh.radius = 0.34
    yoke_mesh.height = 0.42
    yoke_mesh.radial_segments = 20
    yoke_mesh.rings = 10
    var yoke := MeshInstance3D.new()
    yoke.name = "RoundedParkaShoulders"
    yoke.mesh = yoke_mesh
    yoke.position = Vector3(0.0, 1.34, 0.0)
    yoke.scale = Vector3(1.05, 0.42, 0.68)
    yoke.material_override = HavenArtFactory.material(coat_color.lightened(0.06), 0.88)
    _winter_kit.add_child(yoke)

    var scarf_mesh := TorusMesh.new()
    scarf_mesh.inner_radius = 0.16
    scarf_mesh.outer_radius = 0.23
    scarf_mesh.rings = 24
    scarf_mesh.ring_segments = 10
    var scarf := MeshInstance3D.new()
    scarf.name = "ThermalScarf"
    scarf.mesh = scarf_mesh
    scarf.position = Vector3(0.0, 1.52, 0.0)
    scarf.scale = Vector3(1.0, 0.24, 0.76)
    scarf.material_override = HavenArtFactory.material(Color("#dce7e5"), 0.96)
    _winter_kit.add_child(scarf)

    var belt_mesh := TorusMesh.new()
    belt_mesh.inner_radius = 0.28
    belt_mesh.outer_radius = 0.32
    belt_mesh.rings = 24
    belt_mesh.ring_segments = 10
    var belt := MeshInstance3D.new()
    belt.name = "UtilityBelt"
    belt.mesh = belt_mesh
    belt.position = Vector3(0.0, 0.78, 0.0)
    belt.scale = Vector3(1.0, 0.16, 0.68)
    belt.material_override = HavenArtFactory.material(Color("#c96a39"), 0.70, 0.08)
    _winter_kit.add_child(belt)

func _attach_animation_libraries() -> void:
''',
        "rounded winter-character silhouette",
    )
    changes = [
        ("    _backpack.position = Vector3(0.0, -0.08, 0.17)\n", "    _backpack.position = Vector3(0.0, -0.08, 0.14)\n", "pack position"),
        ("    _backpack.scale = Vector3.ONE * 0.52\n", "    _backpack.scale = Vector3.ONE * 0.32\n", "pack base scale"),
        ("        axe.position = Vector3(0.18, -0.10, 0.20)\n", "        axe.position = Vector3(0.13, -0.08, 0.16)\n", "field axe position"),
        ("        axe.scale = Vector3.ONE * 0.36\n", "        axe.scale = Vector3.ONE * 0.22\n", "field axe scale"),
        ("        _backpack.visible = true\n", "        _backpack.visible = ratio > 0.01\n", "carrying visibility"),
        (
            "        _backpack.scale = Vector3.ONE * lerpf(0.48, 0.70, clampf(ratio, 0.0, 1.0))\n",
            "        _backpack.scale = Vector3.ONE * lerpf(0.30, 0.44, clampf(ratio, 0.0, 1.0))\n",
            "carried-pack growth",
        ),
    ]
    for old, new, label in changes:
        replace_exact(path, old, new, label)


def refine_world(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    changes = [
        ("    for index in range(28):\n", "    for index in range(18):\n", "perimeter tree count"),
        ("        var angle := TAU * index / 28.0\n", "        var angle := TAU * index / 18.0\n", "perimeter tree distribution"),
        (
            "        var tree := HavenArtFactory.make_tree(randf_range(0.78, 1.18))\n",
            "        var tree := HavenArtFactory.make_tree(randf_range(0.52, 0.78))\n",
            "perimeter tree scale",
        ),
        (
            "            tree.position = Vector3(cos(angle) * randf_range(15.7, 18.1), 0, sin(angle) * randf_range(15.7, 18.1))\n",
            "            tree.position = Vector3(cos(angle) * randf_range(15.8, 17.4), 0, sin(angle) * randf_range(15.8, 17.4))\n",
            "perimeter tree radius",
        ),
        ("        campfire.position = Vector3(-2.15,0,1.15)\n", "        campfire.position = Vector3(-3.8,0,1.8)\n", "secondary fire position"),
        ("        campfire.scale = Vector3.ONE * 0.64\n", "        campfire.scale = Vector3.ONE * 0.34\n", "secondary fire scale"),
        ("    for index in range(18):\n", "    for index in range(8):\n", "resource cluster count"),
        (
            "        resource.position = Vector3(cos(angle) * randf_range(3.8,8.4),0.0,sin(angle) * randf_range(3.8,8.4))\n",
            "        resource.position = Vector3(cos(angle) * randf_range(6.4,10.8),0.0,sin(angle) * randf_range(6.4,10.8))\n",
            "resource cluster radius",
        ),
        ("    player.position = Vector3(0,0.08,4.5)\n", "    player.position = Vector3(0,0.08,4.15)\n", "player staging"),
        ("    camera.size = 10.2\n", "    camera.size = 9.55\n", "camera readability"),
    ]
    for old, new, label in changes:
        replace_exact(path, old, new, label)


def refine_assets(project: Path) -> None:
    path = project / "scripts" / "art_factory.gd"
    changes = [
        (
            "                log.position = Vector3((index % 2 - 0.5) * 0.38, 0.10 + (index / 2) * 0.20, (index / 2 - 0.5) * 0.36)\n",
            "                log.position = Vector3((index % 2 - 0.5) * 0.16, 0.05 + (index / 2) * 0.09, (index / 2 - 0.5) * 0.15)\n",
            "wood-stack spacing",
        ),
        ("                log.scale = Vector3.ONE * 0.68\n", "                log.scale = Vector3.ONE * 0.24\n", "wood-stack scale"),
        (
            "                rock.position = Vector3((index - 1) * 0.35, 0.0, (index % 2) * 0.28)\n",
            "                rock.position = Vector3((index - 1) * 0.15, 0.0, (index % 2) * 0.12)\n",
            "scrap spacing",
        ),
        (
            "                rock.scale = Vector3.ONE * (0.42 + index * 0.04)\n",
            "                rock.scale = Vector3.ONE * (0.18 + index * 0.018)\n",
            "scrap scale",
        ),
        ("            cache.scale = Vector3.ONE * 0.70\n", "            cache.scale = Vector3.ONE * 0.34\n", "supply-resource scale"),
        ("        wolf.scale = Vector3.ONE * 0.82\n", "        wolf.scale = Vector3.ONE * 0.62\n", "wolf scale"),
        ("    var body := cylinder(0.52, 0.62, 1.12, Color(\"#263640\"), \"FurnaceBody\", 0.34)\n", "    var body := cylinder(0.52, 0.62, 1.12, Color(\"#344a55\"), \"FurnaceBody\", 0.30)\n", "furnace body finish"),
        ("    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#52646d\"), \"FurnaceCap\", 0.42)\n", "    var cap := cylinder(0.67, 0.62, 0.16, Color(\"#6a7e87\"), \"FurnaceCap\", 0.38)\n", "furnace cap finish"),
    ]
    for old, new, label in changes:
        replace_exact(path, old, new, label)


def refine_hud(project: Path) -> None:
    path = project / "scripts" / "hud.gd"
    changes = [
        ("top.position=Vector2(24,20); top.size=Vector2(1872,64)", "top.position=Vector2(18,14); top.size=Vector2(1884,52)", "top bar footprint"),
        ("_panel(Color(0.025,0.065,0.105,0.88),18)", "_panel(Color(0.025,0.065,0.105,0.82),14)", "top bar style"),
        ("resource_label.add_theme_font_size_override(\"font_size\",22)", "resource_label.add_theme_font_size_override(\"font_size\",16)", "resource typography"),
        ("resource_label.size=Vector2(900,64)", "resource_label.size=Vector2(900,52)", "resource row height"),
        ("helper_label.add_theme_font_size_override(\"font_size\",19)", "helper_label.add_theme_font_size_override(\"font_size\",15)", "helper typography"),
        ("helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,64)", "helper_label.position=Vector2(1050,0); helper_label.size=Vector2(780,52)", "helper row height"),
        ("objective_panel.position=Vector2(28,104); objective_panel.size=Vector2(430,100)", "objective_panel.position=Vector2(18,74); objective_panel.size=Vector2(330,70)", "objective panel footprint"),
        ("_panel(Color(0.03,0.08,0.125,0.86),18)", "_panel(Color(0.03,0.08,0.125,0.80),13)", "objective panel style"),
        ("objective.position=Vector2(18,12); objective.size=Vector2(394,76)", "objective.position=Vector2(13,7); objective.size=Vector2(304,56)", "objective bounds"),
        ("objective.add_theme_font_size_override(\"font_size\",20)", "objective.add_theme_font_size_override(\"font_size\",15)", "objective typography"),
        ("furnace_panel.position=Vector2(1462,104); furnace_panel.size=Vector2(430,100)", "furnace_panel.position=Vector2(1572,74); furnace_panel.size=Vector2(330,70)", "furnace panel footprint"),
        ("_panel(Color(0.15,0.065,0.025,0.88),18)", "_panel(Color(0.15,0.065,0.025,0.82),13)", "furnace panel style"),
        ("furnace_label.position=Vector2(18,12); furnace_label.size=Vector2(394,76)", "furnace_label.position=Vector2(13,7); furnace_label.size=Vector2(304,56)", "furnace label bounds"),
        ("furnace_label.add_theme_font_size_override(\"font_size\",20)", "furnace_label.add_theme_font_size_override(\"font_size\",15)", "furnace typography"),
        ("joystick.position=Vector2(42,826); joystick.size=Vector2(210,210)", "joystick.position=Vector2(34,864); joystick.size=Vector2(162,162)", "joystick footprint"),
        ("auto.position=Vector2(1672,864); auto.size=Vector2(170,120)", "auto.position=Vector2(1750,900); auto.size=Vector2(122,86)", "auto interaction footprint"),
        ("auto.add_theme_font_size_override(\"font_size\",20)", "auto.add_theme_font_size_override(\"font_size\",14)", "auto interaction typography"),
        ("_panel(Color(0.03,0.12,0.18,0.70),60)", "_panel(Color(0.03,0.12,0.18,0.62),43)", "auto interaction style"),
        ("toast_label.position=Vector2(560,890); toast_label.size=Vector2(800,58)", "toast_label.position=Vector2(650,926); toast_label.size=Vector2(620,40)", "toast footprint"),
        ("toast_label.add_theme_font_size_override(\"font_size\",22)", "toast_label.add_theme_font_size_override(\"font_size\",16)", "toast typography"),
    ]
    for old, new, label in changes:
        replace_exact(path, old, new, label)


def apply(project: Path) -> None:
    refine_character(project)
    refine_world(project)
    refine_assets(project)
    refine_hud(project)

    required = {
        "scripts/production_character.gd": ["RoundedInsulatedParka", "RoundedParkaShoulders"],
        "scripts/main.gd": ["for index in range(8)", "camera.size = 9.55"],
        "scripts/art_factory.gd": ["Vector3.ONE * 0.24", "Color(\"#344a55\")"],
        "scripts/hud.gd": ["Vector2(330,70)", "Vector2(162,162)"],
    }
    for relative, markers in required.items():
        source = (project / relative).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                raise SystemExit(f"HAVENLINE final visual marker missing from {relative}: {marker}")

    print(
        "HAVENLINE final visual pass applied: rounded winter character, peripheral resources, "
        "refined furnace, and compact mobile HUD"
    )
