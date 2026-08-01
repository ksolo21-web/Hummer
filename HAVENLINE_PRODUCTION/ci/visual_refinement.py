#!/usr/bin/env python3
"""Deterministic visual and production-quality refinements for HAVENLINE."""
from __future__ import annotations

from pathlib import Path


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE visual refinement '{label}' expected one marker, found {count}: {old!r}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


def refine_character(project: Path) -> None:
    path = project / "scripts" / "production_character.gd"
    replace_exact(
        path,
        "    if body_root is AnimationPlayer:\n        animation_players.append(body_root)\n",
        "",
        "Godot 4.7 impossible Node3D/AnimationPlayer branch",
    )
    replace_exact(
        path,
        "                var source := mesh_node.get_active_material(surface)\n",
        "                var source: Material = mesh_node.get_active_material(surface)\n",
        "Godot 4.7 material inference",
    )
    replace_exact(
        path,
        "var _backpack: Node3D\nvar _skeleton: Skeleton3D\n",
        "var _backpack: Node3D\nvar _skeleton: Skeleton3D\nvar _winter_kit: Node3D\n",
        "winter field-gear state",
    )
    replace_exact(
        path,
        '''    _skeleton = body_root.find_child("Skeleton3D", true, false) as Skeleton3D
    _attach_animation_libraries()
''',
        '''    _skeleton = body_root.find_child("Skeleton3D", true, false) as Skeleton3D
    _build_winter_kit(role)
    _attach_animation_libraries()
''',
        "winter field-gear setup",
    )
    replace_exact(
        path,
        "func _attach_animation_libraries() -> void:\n",
        '''func _build_winter_kit(role: String) -> void:
    _winter_kit = Node3D.new()
    _winter_kit.name = "HAVENLINEWinterFieldGear"
    _winter_kit.rotation.y = PI
    add_child(_winter_kit)
    var coat_color := Color("#17384d") if role == "player" else Color("#315c63")
    _modulate_materials(coat_color, 0.58)

    var shell_mesh := BoxMesh.new()
    shell_mesh.size = Vector3(0.58, 0.74, 0.34)
    var shell := MeshInstance3D.new()
    shell.name = "InsulatedParka"
    shell.mesh = shell_mesh
    shell.position = Vector3(0.0, 1.15, 0.0)
    shell.material_override = HavenArtFactory.material(coat_color, 0.86)
    shell.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _winter_kit.add_child(shell)

    var yoke_mesh := BoxMesh.new()
    yoke_mesh.size = Vector3(0.70, 0.18, 0.38)
    var yoke := MeshInstance3D.new()
    yoke.name = "ParkaShoulderYoke"
    yoke.mesh = yoke_mesh
    yoke.position = Vector3(0.0, 1.46, 0.0)
    yoke.material_override = HavenArtFactory.material(coat_color.lightened(0.08), 0.84)
    _winter_kit.add_child(yoke)

    var scarf_mesh := BoxMesh.new()
    scarf_mesh.size = Vector3(0.46, 0.11, 0.36)
    var scarf := MeshInstance3D.new()
    scarf.name = "ThermalScarf"
    scarf.mesh = scarf_mesh
    scarf.position = Vector3(0.0, 1.56, 0.0)
    scarf.material_override = HavenArtFactory.material(Color("#d8e2df"), 0.94)
    _winter_kit.add_child(scarf)

    var belt_mesh := BoxMesh.new()
    belt_mesh.size = Vector3(0.61, 0.08, 0.37)
    var belt := MeshInstance3D.new()
    belt.name = "UtilityBelt"
    belt.mesh = belt_mesh
    belt.position = Vector3(0.0, 0.78, 0.0)
    belt.material_override = HavenArtFactory.material(Color("#bd6132"), 0.66, 0.08)
    _winter_kit.add_child(belt)

func _attach_animation_libraries() -> void:
''',
        "fitted winter jacket construction",
    )
    replace_exact(
        path,
        '''    attachment.add_child(_backpack)
    _backpack.position = Vector3(0.0, -0.08, 0.20)
    _backpack.rotation_degrees = Vector3(0.0, 180.0, 0.0)
    _backpack.scale = Vector3.ONE * 0.72
''',
        '''    attachment.add_child(_backpack)
    _backpack.position = Vector3(0.0, -0.08, 0.17)
    _backpack.rotation_degrees = Vector3(0.0, 180.0, 0.0)
    _backpack.scale = Vector3.ONE * 0.52
    var axe := HavenArtFactory.instantiate("axe")
    if axe:
        axe.name = "FieldAxe"
        attachment.add_child(axe)
        axe.position = Vector3(0.18, -0.10, 0.20)
        axe.rotation_degrees = Vector3(16.0, 8.0, -16.0)
        axe.scale = Vector3.ONE * 0.36
''',
        "compact pack and field axe",
    )
    replace_exact(
        path,
        '''func set_backpack_fill(ratio: float) -> void:
    if _backpack:
        _backpack.visible = ratio > 0.01
        _backpack.scale = Vector3.ONE * lerpf(0.66, 0.84, clampf(ratio, 0.0, 1.0))
''',
        '''func set_backpack_fill(ratio: float) -> void:
    if _backpack:
        _backpack.visible = true
        _backpack.scale = Vector3.ONE * lerpf(0.48, 0.70, clampf(ratio, 0.0, 1.0))
''',
        "persistent readable field pack",
    )


def refine_furnace(project: Path) -> None:
    path = project / "scripts" / "art_factory.gd"
    replace_exact(
        path,
        '''static func make_furnace() -> Node3D:
    var root := Node3D.new()
    root.name = "CentralFurnace"
    var furnace := instantiate("furnace")
    if furnace:
        furnace.name = "FurnaceModel"
        furnace.scale = Vector3.ONE * 0.72
        root.add_child(furnace)
    var campfire := instantiate("campfire")
    if campfire:
        campfire.name = "FurnaceFire"
        campfire.position = Vector3(0.0, 0.02, -0.62)
        campfire.scale = Vector3.ONE * 0.58
        root.add_child(campfire)
    var light := OmniLight3D.new()
    light.name = "FurnaceLight"
    light.light_color = Color("#ff9a50")
    light.light_energy = 5.5
    light.omni_range = 9.5
    light.position = Vector3(0.0, 1.15, -0.25)
    light.shadow_enabled = true
    root.add_child(light)
    return root
''',
        '''static func make_furnace() -> Node3D:
    var root := Node3D.new()
    root.name = "CentralFurnace"

    var base := cylinder(0.92, 1.02, 0.14, Color("#53636a"), "FurnaceFoundation", 0.72)
    base.position = Vector3(0.0, 0.07, 0.0)
    root.add_child(base)

    var body := cylinder(0.52, 0.62, 1.12, Color("#263640"), "FurnaceBody", 0.34)
    body.position = Vector3(0.0, 0.68, 0.0)
    root.add_child(body)

    var cap := cylinder(0.67, 0.62, 0.16, Color("#52646d"), "FurnaceCap", 0.42)
    cap.position = Vector3(0.0, 1.28, 0.0)
    root.add_child(cap)

    var chimney := cylinder(0.13, 0.19, 0.96, Color("#202d34"), "FurnaceChimney", 0.52)
    chimney.position = Vector3(0.0, 1.78, -0.08)
    root.add_child(chimney)

    var door_frame_mesh := BoxMesh.new()
    door_frame_mesh.size = Vector3(0.50, 0.52, 0.10)
    var door_frame := mesh_node(door_frame_mesh, Color("#111c22"), "FurnaceDoorFrame", 0.52, 0.16)
    door_frame.position = Vector3(0.0, 0.66, 0.57)
    root.add_child(door_frame)

    var ember_mesh := BoxMesh.new()
    ember_mesh.size = Vector3(0.34, 0.35, 0.07)
    var ember := mesh_node(ember_mesh, Color("#ff681f"), "EmberCore", 0.34)
    ember.position = Vector3(0.0, 0.67, 0.63)
    ember.material_override = material(Color("#ff681f"), 0.34, 0.0, Color("#ff4c12"), 4.4)
    root.add_child(ember)

    for index in range(3):
        var vent_mesh := BoxMesh.new()
        vent_mesh.size = Vector3(0.30, 0.045, 0.035)
        var vent := mesh_node(vent_mesh, Color("#2a1510"), "FurnaceVent", 0.58, 0.12)
        vent.position = Vector3(0.0, 0.56 + index * 0.10, 0.68)
        root.add_child(vent)

    var campfire := instantiate("campfire")
    if campfire:
        campfire.name = "FurnaceFire"
        campfire.position = Vector3(0.0, 0.18, 0.48)
        campfire.scale = Vector3.ONE * 0.23
        root.add_child(campfire)

    var light := OmniLight3D.new()
    light.name = "FurnaceLight"
    light.light_color = Color("#ff8a42")
    light.light_energy = 4.9
    light.omni_range = 7.2
    light.position = Vector3(0.0, 0.92, 0.58)
    light.shadow_enabled = true
    root.add_child(light)
    return root
''',
        "compact upgradeable furnace",
    )


def refine_world(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    replace_exact(
        path,
        "func _build_world() -> void:\n    world = Node3D.new()\n",
        "func _build_world() -> void:\n    seed(44017)\n    world = Node3D.new()\n",
        "deterministic scene composition",
    )
    replace_exact(
        path,
        '''    env.background_color = Color("#536b7d")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#8da6b8")
    env.ambient_light_energy = 0.48
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.tonemap_exposure = 0.82
    env.fog_enabled = true
    env.fog_light_color = Color("#91a8b8")
    env.fog_density = 0.013
    env.fog_height = 0.0
    env.fog_height_density = 0.20
''',
        '''    env.background_color = Color("#243846")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#93aab8")
    env.ambient_light_energy = 0.34
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.tonemap_exposure = 0.94
    env.fog_enabled = true
    env.fog_light_color = Color("#708da0")
    env.fog_density = 0.006
    env.fog_height = 0.0
    env.fog_height_density = 0.12
''',
        "cold environment contrast",
    )
    replace_exact(
        path,
        '''    sun.light_color = Color("#d3e0e8")
    sun.light_energy = 1.08
''',
        '''    sun.light_color = Color("#e1edf3")
    sun.light_energy = 1.28
''',
        "crisp key light",
    )
    replace_exact(
        path,
        '''        ground_body.add_child(collision)

    for index in range(28):
''',
        '''        ground_body.add_child(collision)

    var camp_pad_mesh := CylinderMesh.new()
    camp_pad_mesh.top_radius = 6.25
    camp_pad_mesh.bottom_radius = 6.25
    camp_pad_mesh.height = 0.035
    camp_pad_mesh.radial_segments = 64
    var camp_pad := MeshInstance3D.new()
    camp_pad.name = "PackedSnowOutpostPad"
    camp_pad.mesh = camp_pad_mesh
    camp_pad.position = Vector3(0.0, 0.012, -0.35)
    camp_pad.material_override = HavenArtFactory.material(Color("#bfd6df"), 0.98)
    camp_pad.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    world.add_child(camp_pad)

    for index in range(28):
''',
        "packed snow outpost pad",
    )
    replace_exact(
        path,
        '''    for data in [
        [Vector3(-6.4,0,-4.0), deg_to_rad(24.0), 0.68],
        [Vector3(6.4,0,-4.0), deg_to_rad(-24.0), 0.68],
        [Vector3(-8.8,0,6.8), deg_to_rad(62.0), 0.54]
    ]:
''',
        '''    for data in [
        [Vector3(-6.2,0,-5.7), deg_to_rad(24.0), 0.27],
        [Vector3(6.2,0,-5.7), deg_to_rad(-24.0), 0.27]
    ]:
''',
        "scaled insulated tents",
    )
    replace_exact(
        path,
        '''    for data in [[Vector3(-4.5,0,1.6),0.68],[Vector3(4.7,0,1.1),0.62]]:
''',
        '''    for data in [[Vector3(-3.7,0,1.4),0.47],[Vector3(3.8,0,1.0),0.46]]:
''',
        "compact supply caches",
    )
    replace_exact(
        path,
        '''    for data in [[Vector3(-7.8,0,4.1),deg_to_rad(90.0)],[Vector3(7.8,0,4.1),deg_to_rad(90.0)],[Vector3(-4.8,0,-8.2),0.0],[Vector3(4.8,0,-8.2),0.0]]:
''',
        '''    for data in [[Vector3(-5.8,0,4.0),deg_to_rad(90.0)],[Vector3(5.8,0,4.0),deg_to_rad(90.0)],[Vector3(-4.3,0,-6.2),0.0],[Vector3(4.3,0,-6.2),0.0]]:
''',
        "compact barricade perimeter",
    )
    replace_exact(
        path,
        '''    var ring_mesh := CylinderMesh.new()
    ring_mesh.top_radius = 1.0
    ring_mesh.bottom_radius = 1.0
    ring_mesh.height = 0.025
    ring_mesh.radial_segments = 64
''',
        '''    var ring_mesh := TorusMesh.new()
    ring_mesh.inner_radius = 0.976
    ring_mesh.outer_radius = 1.0
    ring_mesh.rings = 64
    ring_mesh.ring_segments = 12
''',
        "warmth perimeter geometry",
    )
    replace_exact(
        path,
        '''    var ring_material := HavenArtFactory.material(Color(1.0,0.32,0.06,0.10),0.55,0,Color("#ff6b24"),1.2)
''',
        '''    var ring_material := HavenArtFactory.material(Color(1.0,0.30,0.04,0.36),0.48,0,Color("#ff6b24"),1.9)
''',
        "warmth perimeter material",
    )
    replace_exact(path, "    for index in range(14):\n", "    for index in range(18):\n", "resource density")
    replace_exact(
        path,
        '''        resource.position = Vector3(cos(angle) * randf_range(6.0,12.8),0.0,sin(angle) * randf_range(6.0,12.8))
''',
        '''        resource.position = Vector3(cos(angle) * randf_range(3.8,8.4),0.0,sin(angle) * randf_range(3.8,8.4))
''',
        "compact resource placement",
    )
    replace_exact(path, "    player.position = Vector3(0,0.08,5.0)\n", "    player.position = Vector3(0,0.08,4.5)\n", "player staging")
    replace_exact(path, "    camera.size = 11.6\n", "    camera.size = 10.2\n", "close readable camera")
    replace_exact(
        path,
        '''    camera_rig.global_position = player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''',
        '''    camera_rig.global_position = player.global_position + Vector3(6.6,8.4,7.6)
    camera_rig.look_at(player.global_position + Vector3(0,0.78,-0.85), Vector3.UP)
''',
        "initial three-quarter framing",
    )
    replace_exact(
        path,
        '''    var desired := player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''',
        '''    var desired := player.global_position + Vector3(6.6,8.4,7.6)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.78,-0.85), Vector3.UP)
''',
        "follow-camera framing",
    )


def refine_gameplay(project: Path) -> None:
    path = project / "scripts" / "gameplay.gd"
    replace_exact(path, "var heat_radius:=5.2\n", "var heat_radius:=3.6\n", "initial warmth radius")
    replace_exact(
        path,
        '''    heat_radius=lerpf(5.2,12.5,clampf((furnace_level-1)/4.0,0.0,1.0))*lerpf(0.72,1.0,clampf(furnace_fuel/20.0,0.0,1.0))
''',
        '''    heat_radius=lerpf(3.6,8.8,clampf((furnace_level-1)/4.0,0.0,1.0))*lerpf(0.82,1.0,clampf(furnace_fuel/20.0,0.0,1.0))
''',
        "warmth progression scale",
    )


def apply(project: Path) -> None:
    refine_character(project)
    refine_furnace(project)
    refine_world(project)
    refine_gameplay(project)

    required = {
        "scripts/production_character.gd": [
            "InsulatedParka",
            "ParkaShoulderYoke",
            "ThermalScarf",
            "FieldAxe",
        ],
        "scripts/art_factory.gd": ["FurnaceFoundation", "FurnaceDoorFrame", "EmberCore"],
        "scripts/main.gd": ["PackedSnowOutpostPad", "TorusMesh", "camera.size = 10.2"],
        "scripts/gameplay.gd": ["var heat_radius:=3.6", "lerpf(3.6,8.8"],
    }
    for relative, markers in required.items():
        source = (project / relative).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                raise SystemExit(f"HAVENLINE visual refinement marker missing from {relative}: {marker}")

    print("HAVENLINE visual refinement applied: fitted winter character, compact furnace, dense outpost, and readable warmth")
