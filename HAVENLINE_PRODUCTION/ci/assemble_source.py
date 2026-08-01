#!/usr/bin/env python3
"""Reconstruct, verify, and patch the clean HAVENLINE production project."""
from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
import shutil
import sys
import zipfile
from pathlib import Path

repo = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
out = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else repo / ".havenline-production"
source_dir = repo / "HAVENLINE_PRODUCTION" / "source"
manifest = json.loads((source_dir / "manifest.json").read_text(encoding="utf-8"))
parts = sorted((source_dir / "parts").glob("part-*.b64"))

if len(parts) != int(manifest["part_count"]):
    raise SystemExit(
        f"HAVENLINE source part count mismatch: expected {manifest['part_count']}, found {len(parts)}"
    )
encoded = "".join(part.read_text(encoding="utf-8").strip() for part in parts)
if len(encoded) % 4 != 0:
    raise SystemExit(f"HAVENLINE combined base64 length is invalid: {len(encoded)}")
archive_bytes = base64.b64decode(encoded, validate=True)
actual_archive_sha = hashlib.sha256(archive_bytes).hexdigest()
if actual_archive_sha != manifest["archive_sha256"]:
    raise SystemExit(f"HAVENLINE source archive checksum mismatch: {actual_archive_sha}")

archive_path = repo / ".havenline-production-source.zip"
archive_path.write_bytes(archive_bytes)
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
with zipfile.ZipFile(archive_path) as archive:
    corrupt = archive.testzip()
    if corrupt:
        raise SystemExit(f"HAVENLINE source ZIP contains a corrupt entry: {corrupt}")
    archive.extractall(out)

project = out / "havenline_production"
for entry in manifest["files"]:
    path = project / entry["path"]
    if not path.is_file():
        raise SystemExit(f"HAVENLINE source is missing {entry['path']}")
    data = path.read_bytes()
    if len(data) != entry["bytes"]:
        raise SystemExit(f"HAVENLINE source byte count mismatch for {entry['path']}")
    digest = hashlib.sha256(data).hexdigest()
    if digest != entry["sha256"]:
        raise SystemExit(f"HAVENLINE source checksum mismatch for {entry['path']}: {digest}")

ci_dir = repo / "HAVENLINE_PRODUCTION" / "ci"
sys.path.insert(0, str(ci_dir))
from production_patches import apply  # noqa: E402

apply(project)

encoded_dir = ci_dir / "encoded"
decoded_dir = out / ".decoded-ci"
decoded_dir.mkdir(parents=True, exist_ok=True)
payloads = {
    "production_visuals_assets": (
        "ab24b700cfe9f0bdd02597a0d265e5764ecd3ceb6a8190d8770502845a93a465",
        "apply_assets",
    ),
    "production_visuals_scene": (
        "86f09f4f993e2319045cf13d14bd353270fde1a069abbba9eb140e6c51f84d4f",
        "apply_scene",
    ),
}
for module_name, (expected_sha, function_name) in payloads.items():
    encoded_path = encoded_dir / f"{module_name}.py.b64"
    decoded_path = decoded_dir / f"{module_name}.py"
    decoded = base64.b64decode(encoded_path.read_text(encoding="utf-8").strip(), validate=True)
    actual_sha = hashlib.sha256(decoded).hexdigest()
    if actual_sha != expected_sha:
        raise SystemExit(f"HAVENLINE visual payload checksum mismatch for {module_name}: {actual_sha}")
    decoded_path.write_bytes(decoded)
    spec = importlib.util.spec_from_file_location(module_name, decoded_path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"HAVENLINE could not load visual payload: {module_name}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    getattr(module, function_name)(project)


def repair_generated(path: Path, original: str, repaired: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    occurrences = source.count(original)
    if occurrences != 1:
        raise SystemExit(
            f"HAVENLINE generated-source repair '{label}' expected 1 occurrence, found {occurrences}: {original!r}"
        )
    path.write_text(source.replace(original, repaired, 1), encoding="utf-8")


# Imported-character compatibility and field-gear pass.
character_script = project / "scripts" / "production_character.gd"
repair_generated(character_script, "    if body_root is AnimationPlayer:\n        animation_players.append(body_root)\n", "", "impossible Node3D/AnimationPlayer branch")
repair_generated(character_script, "                var source := mesh_node.get_active_material(surface)\n", "                var source: Material = mesh_node.get_active_material(surface)\n", "material type inference")
repair_generated(character_script, "var _backpack: Node3D\nvar _skeleton: Skeleton3D\n", "var _backpack: Node3D\nvar _skeleton: Skeleton3D\nvar _winter_kit: Node3D\n", "winter-kit state")
repair_generated(character_script, '''    _skeleton = body_root.find_child("Skeleton3D", true, false) as Skeleton3D
    _attach_animation_libraries()
''', '''    _skeleton = body_root.find_child("Skeleton3D", true, false) as Skeleton3D
    _build_winter_kit(role)
    _attach_animation_libraries()
''', "winter-kit setup")
repair_generated(character_script, "func _attach_animation_libraries() -> void:\n", '''func _build_winter_kit(role: String) -> void:
    _winter_kit = Node3D.new()
    _winter_kit.name = "HAVENLINEWinterFieldGear"
    _winter_kit.rotation.y = PI
    add_child(_winter_kit)
    var coat_color := Color("#17384d") if role == "player" else Color("#315c63")
    var coat_mesh := CylinderMesh.new()
    coat_mesh.top_radius = 0.39
    coat_mesh.bottom_radius = 0.50
    coat_mesh.height = 0.90
    coat_mesh.radial_segments = 20
    var coat := MeshInstance3D.new()
    coat.name = "InsulatedParka"
    coat.mesh = coat_mesh
    coat.position = Vector3(0.0, 1.18, 0.0)
    coat.scale = Vector3(1.0, 1.0, 0.78)
    coat.material_override = HavenArtFactory.material(coat_color, 0.88)
    coat.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _winter_kit.add_child(coat)
    var collar_mesh := TorusMesh.new()
    collar_mesh.inner_radius = 0.26
    collar_mesh.outer_radius = 0.43
    collar_mesh.rings = 32
    collar_mesh.ring_segments = 12
    var collar := MeshInstance3D.new()
    collar.name = "ThermalCollar"
    collar.mesh = collar_mesh
    collar.position = Vector3(0.0, 1.64, 0.0)
    collar.scale = Vector3(1.0, 0.42, 0.88)
    collar.material_override = HavenArtFactory.material(Color("#d5e1df"), 0.96)
    _winter_kit.add_child(collar)
    var belt_mesh := TorusMesh.new()
    belt_mesh.inner_radius = 0.43
    belt_mesh.outer_radius = 0.49
    belt_mesh.rings = 28
    belt_mesh.ring_segments = 10
    var belt := MeshInstance3D.new()
    belt.name = "UtilityBelt"
    belt.mesh = belt_mesh
    belt.position = Vector3(0.0, 0.78, 0.0)
    belt.scale = Vector3(1.0, 0.26, 0.78)
    belt.material_override = HavenArtFactory.material(Color("#d06b32"), 0.68, 0.08)
    _winter_kit.add_child(belt)

func _attach_animation_libraries() -> void:
''', "winter field gear construction")
repair_generated(character_script, '''    attachment.add_child(_backpack)
    _backpack.position = Vector3(0.0, -0.08, 0.20)
    _backpack.rotation_degrees = Vector3(0.0, 180.0, 0.0)
    _backpack.scale = Vector3.ONE * 0.72
''', '''    attachment.add_child(_backpack)
    _backpack.position = Vector3(0.0, -0.08, 0.20)
    _backpack.rotation_degrees = Vector3(0.0, 180.0, 0.0)
    _backpack.scale = Vector3.ONE * 0.64
    var axe := HavenArtFactory.instantiate("axe")
    if axe:
        axe.name = "FieldAxe"
        attachment.add_child(axe)
        axe.position = Vector3(0.22, -0.10, 0.23)
        axe.rotation_degrees = Vector3(18.0, 8.0, -18.0)
        axe.scale = Vector3.ONE * 0.46
''', "backpack field axe")
repair_generated(character_script, '''func set_backpack_fill(ratio: float) -> void:
    if _backpack:
        _backpack.visible = ratio > 0.01
        _backpack.scale = Vector3.ONE * lerpf(0.66, 0.84, clampf(ratio, 0.0, 1.0))
''', '''func set_backpack_fill(ratio: float) -> void:
    if _backpack:
        _backpack.visible = true
        _backpack.scale = Vector3.ONE * lerpf(0.60, 0.84, clampf(ratio, 0.0, 1.0))
''', "persistent visible field pack")
print("HAVENLINE character pass applied: winter field gear, pack, axe, and Godot 4.7 typing")

# Replace the oversized fireplace wall with a compact readable HAVENLINE furnace.
art_factory = project / "scripts" / "art_factory.gd"
repair_generated(art_factory, '''static func make_furnace() -> Node3D:
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
''', '''static func make_furnace() -> Node3D:
    var root := Node3D.new()
    root.name = "CentralFurnace"
    var body := cylinder(0.60, 0.72, 1.24, Color("#263640"), "FurnaceBody", 0.34)
    body.position = Vector3(0.0, 0.64, 0.0)
    root.add_child(body)
    var cap := cylinder(0.76, 0.70, 0.18, Color("#4b5e68"), "FurnaceCap", 0.42)
    cap.position = Vector3(0.0, 1.29, 0.0)
    root.add_child(cap)
    var chimney := cylinder(0.16, 0.22, 1.20, Color("#202d34"), "FurnaceChimney", 0.52)
    chimney.position = Vector3(0.0, 1.92, -0.10)
    root.add_child(chimney)
    var ember_mesh := SphereMesh.new()
    ember_mesh.radius = 0.31
    ember_mesh.height = 0.52
    var ember := mesh_node(ember_mesh, Color("#ff6b24"), "EmberCore", 0.38)
    ember.position = Vector3(0.0, 0.55, 0.60)
    ember.material_override = material(Color("#ff6b24"), 0.38, 0.0, Color("#ff5a16"), 4.2)
    root.add_child(ember)
    for index in range(3):
        var vent_mesh := BoxMesh.new()
        vent_mesh.size = Vector3(0.38, 0.075, 0.055)
        var vent := mesh_node(vent_mesh, Color("#10191f"), "FurnaceVent", 0.62, 0.18)
        vent.position = Vector3(0.0, 0.38 + index * 0.16, 0.67)
        root.add_child(vent)
    var campfire := instantiate("campfire")
    if campfire:
        campfire.name = "FurnaceFire"
        campfire.position = Vector3(0.0, 0.02, 0.54)
        campfire.scale = Vector3.ONE * 0.30
        root.add_child(campfire)
    var light := OmniLight3D.new()
    light.name = "FurnaceLight"
    light.light_color = Color("#ff8a42")
    light.light_energy = 4.8
    light.omni_range = 7.0
    light.position = Vector3(0.0, 0.92, 0.55)
    light.shadow_enabled = true
    root.add_child(light)
    return root
''', "compact furnace replacement")
print("HAVENLINE compact furnace pass applied")

# Compact outpost composition, camera, lighting, and readable warmth boundary.
main_script = project / "scripts" / "main.gd"
repair_generated(main_script, "func _build_world() -> void:\n    world = Node3D.new()\n", "func _build_world() -> void:\n    seed(44017)\n    world = Node3D.new()\n", "deterministic scene composition")
repair_generated(main_script, '''    env.background_color = Color("#536b7d")
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
''', '''    env.background_color = Color("#243846")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#93aab8")
    env.ambient_light_energy = 0.34
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.tonemap_exposure = 0.92
    env.fog_enabled = true
    env.fog_light_color = Color("#708da0")
    env.fog_density = 0.006
    env.fog_height = 0.0
    env.fog_height_density = 0.12
''', "cold environment contrast")
repair_generated(main_script, '''    sun.light_color = Color("#d3e0e8")
    sun.light_energy = 1.08
''', '''    sun.light_color = Color("#e1edf3")
    sun.light_energy = 1.28
''', "crisper key light")
repair_generated(main_script, '''        ground_body.add_child(collision)

    for index in range(28):
''', '''        ground_body.add_child(collision)

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
''', "packed snow outpost pad")
repair_generated(main_script, '''    for data in [
        [Vector3(-6.4,0,-4.0), deg_to_rad(24.0), 0.68],
        [Vector3(6.4,0,-4.0), deg_to_rad(-24.0), 0.68],
        [Vector3(-8.8,0,6.8), deg_to_rad(62.0), 0.54]
    ]:
''', '''    for data in [
        [Vector3(-6.2,0,-5.7), deg_to_rad(24.0), 0.27],
        [Vector3(6.2,0,-5.7), deg_to_rad(-24.0), 0.27]
    ]:
''', "scaled insulated tents")
repair_generated(main_script, '''    for data in [[Vector3(-4.5,0,1.6),0.68],[Vector3(4.7,0,1.1),0.62]]:
''', '''    for data in [[Vector3(-3.7,0,1.4),0.56],[Vector3(3.8,0,1.0),0.54]]:
''', "compact supply caches")
repair_generated(main_script, '''    for data in [[Vector3(-7.8,0,4.1),deg_to_rad(90.0)],[Vector3(7.8,0,4.1),deg_to_rad(90.0)],[Vector3(-4.8,0,-8.2),0.0],[Vector3(4.8,0,-8.2),0.0]]:
''', '''    for data in [[Vector3(-5.8,0,4.0),deg_to_rad(90.0)],[Vector3(5.8,0,4.0),deg_to_rad(90.0)],[Vector3(-4.3,0,-6.2),0.0],[Vector3(4.3,0,-6.2),0.0]]:
''', "compact barricade perimeter")
repair_generated(main_script, '''    var ring_mesh := CylinderMesh.new()
    ring_mesh.top_radius = 1.0
    ring_mesh.bottom_radius = 1.0
    ring_mesh.height = 0.025
    ring_mesh.radial_segments = 64
''', '''    var ring_mesh := TorusMesh.new()
    ring_mesh.inner_radius = 0.972
    ring_mesh.outer_radius = 1.0
    ring_mesh.rings = 64
    ring_mesh.ring_segments = 12
''', "heat-zone perimeter geometry")
repair_generated(main_script, '''    var ring_material := HavenArtFactory.material(Color(1.0,0.32,0.06,0.10),0.55,0,Color("#ff6b24"),1.2)
''', '''    var ring_material := HavenArtFactory.material(Color(1.0,0.30,0.04,0.40),0.48,0,Color("#ff6b24"),2.0)
''', "heat-zone glow material")
repair_generated(main_script, '''    for index in range(14):
''', '''    for index in range(18):
''', "resource density")
repair_generated(main_script, '''        resource.position = Vector3(cos(angle) * randf_range(6.0,12.8),0.0,sin(angle) * randf_range(6.0,12.8))
''', '''        resource.position = Vector3(cos(angle) * randf_range(3.8,8.4),0.0,sin(angle) * randf_range(3.8,8.4))
''', "compact resource placement")
repair_generated(main_script, '''    player.position = Vector3(0,0.08,5.0)
''', '''    player.position = Vector3(0,0.08,4.4)
''', "player staging position")
repair_generated(main_script, "    camera.size = 11.6\n", "    camera.size = 10.2\n", "close readable camera")
repair_generated(main_script, '''    camera_rig.global_position = player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''', '''    camera_rig.global_position = player.global_position + Vector3(6.6,8.4,7.6)
    camera_rig.look_at(player.global_position + Vector3(0,0.78,-0.85), Vector3.UP)
''', "initial three-quarter framing")
repair_generated(main_script, '''    var desired := player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''', '''    var desired := player.global_position + Vector3(6.6,8.4,7.6)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.78,-0.85), Vector3.UP)
''', "follow-camera framing")
print("HAVENLINE visual pass 3 applied: dense camp pad, compact props, readable camera, and warmth ring")

# Keep warmth progression visually useful without swallowing the compact camp.
gameplay_script = project / "scripts" / "gameplay.gd"
repair_generated(gameplay_script, "var heat_radius:=5.2\n", "var heat_radius:=3.6\n", "initial warmth radius")
repair_generated(gameplay_script, '''    heat_radius=lerpf(5.2,12.5,clampf((furnace_level-1)/4.0,0.0,1.0))*lerpf(0.72,1.0,clampf(furnace_fuel/20.0,0.0,1.0))
''', '''    heat_radius=lerpf(3.6,8.8,clampf((furnace_level-1)/4.0,0.0,1.0))*lerpf(0.82,1.0,clampf(furnace_fuel/20.0,0.0,1.0))
''', "warmth progression scale")
print("HAVENLINE compact warmth progression applied")

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print("HAVENLINE imported visual payloads verified and applied")
print(project)
