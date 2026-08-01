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


# Godot 4.7 static typing repairs for the imported production character.
character_script = project / "scripts" / "production_character.gd"
repair_generated(
    character_script,
    "    if body_root is AnimationPlayer:\n        animation_players.append(body_root)\n",
    "",
    "impossible Node3D/AnimationPlayer branch",
)
repair_generated(
    character_script,
    "                var source := mesh_node.get_active_material(surface)\n",
    "                var source: Material = mesh_node.get_active_material(surface)\n",
    "material type inference",
)
print("HAVENLINE Godot 4.7 character typing repairs applied")

# Visual pass 2: remove the oversized canopy obstruction, tighten the camera,
# restore cold/warm contrast, and replace the opaque heat disc with a readable
# expanding perimeter ring. Every replacement is exact and fails closed.
main_script = project / "scripts" / "main.gd"
repair_generated(
    main_script,
    "func _build_world() -> void:\n    world = Node3D.new()\n",
    "func _build_world() -> void:\n    seed(44017)\n    world = Node3D.new()\n",
    "deterministic scene composition",
)
repair_generated(
    main_script,
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
    env.ambient_light_color = Color("#9db3c0")
    env.ambient_light_energy = 0.36
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.tonemap_exposure = 0.96
    env.fog_enabled = true
    env.fog_light_color = Color("#7894a7")
    env.fog_density = 0.006
    env.fog_height = 0.0
    env.fog_height_density = 0.12
''',
    "cold environment contrast",
)
repair_generated(
    main_script,
    '''    sun.light_color = Color("#d3e0e8")
    sun.light_energy = 1.08
''',
    '''    sun.light_color = Color("#e1edf3")
    sun.light_energy = 1.32
''',
    "crisper key light",
)
repair_generated(
    main_script,
    '''    for data in [
        [Vector3(-6.4,0,-4.0), deg_to_rad(24.0), 0.68],
        [Vector3(6.4,0,-4.0), deg_to_rad(-24.0), 0.68],
        [Vector3(-8.8,0,6.8), deg_to_rad(62.0), 0.54]
    ]:
''',
    '''    for data in [
        [Vector3(-7.2,0,-5.8), deg_to_rad(24.0), 0.34],
        [Vector3(7.2,0,-5.8), deg_to_rad(-24.0), 0.34]
    ]:
''',
    "scaled and repositioned insulated tents",
)
repair_generated(
    main_script,
    '''    var ring_mesh := CylinderMesh.new()
    ring_mesh.top_radius = 1.0
    ring_mesh.bottom_radius = 1.0
    ring_mesh.height = 0.025
    ring_mesh.radial_segments = 64
''',
    '''    var ring_mesh := TorusMesh.new()
    ring_mesh.inner_radius = 0.955
    ring_mesh.outer_radius = 1.0
    ring_mesh.rings = 64
    ring_mesh.ring_segments = 12
''',
    "heat-zone perimeter geometry",
)
repair_generated(
    main_script,
    '''    var ring_material := HavenArtFactory.material(Color(1.0,0.32,0.06,0.10),0.55,0,Color("#ff6b24"),1.2)
''',
    '''    var ring_material := HavenArtFactory.material(Color(1.0,0.30,0.04,0.58),0.48,0,Color("#ff6b24"),2.2)
''',
    "heat-zone glow material",
)
repair_generated(
    main_script,
    '''    camera.size = 11.6
''',
    '''    camera.size = 9.9
''',
    "closer readable camera",
)
repair_generated(
    main_script,
    '''    camera_rig.global_position = player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''',
    '''    camera_rig.global_position = player.global_position + Vector3(6.4,8.2,7.4)
    camera_rig.look_at(player.global_position + Vector3(0,0.75,0), Vector3.UP)
''',
    "initial three-quarter framing",
)
repair_generated(
    main_script,
    '''    var desired := player.global_position + Vector3(7.2,9.4,8.4)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.85,0), Vector3.UP)
''',
    '''    var desired := player.global_position + Vector3(6.4,8.2,7.4)
    camera_rig.global_position = camera_rig.global_position.lerp(desired, 1.0 - exp(-delta * 8.0))
    camera_rig.look_at(player.global_position + Vector3(0,0.75,0), Vector3.UP)
''',
    "follow-camera framing",
)
print("HAVENLINE visual pass 2 applied: camera, tents, contrast, and heat ring")

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print("HAVENLINE imported visual payloads verified and applied")
print(project)
