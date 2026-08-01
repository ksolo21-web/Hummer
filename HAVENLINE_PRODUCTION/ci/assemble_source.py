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

# Godot 4.7 correctly rejects two impossible/ambiguous static-inference cases
# emitted by the imported-character payload. Apply exact, guarded source repairs
# after payload verification so any upstream drift fails closed instead of
# silently patching the wrong code.
character_script = project / "scripts" / "production_character.gd"
character_source = character_script.read_text(encoding="utf-8")
character_repairs = {
    "    if body_root is AnimationPlayer:\n        animation_players.append(body_root)\n": "",
    "                var source := mesh_node.get_active_material(surface)\n": (
        "                var source: Material = mesh_node.get_active_material(surface)\n"
    ),
}
for original, repaired in character_repairs.items():
    occurrences = character_source.count(original)
    if occurrences != 1:
        raise SystemExit(
            "HAVENLINE character integration repair target mismatch: "
            f"expected 1 occurrence, found {occurrences}: {original!r}"
        )
    character_source = character_source.replace(original, repaired, 1)
character_script.write_text(character_source, encoding="utf-8")
print("HAVENLINE Godot 4.7 character typing repairs applied")

# Temporary exact-scene audit output. It exposes the generated scene source in
# the CI artifact so the next visual pass can be corrected from the real build,
# not guessed from a screenshot or a throwaway mockup.
for audit_relative in ("scripts/main.gd", "scripts/art_factory.gd", "scripts/hud.gd"):
    audit_path = project / audit_relative
    if audit_path.is_file():
        print(f"===== HAVENLINE SOURCE AUDIT START {audit_relative} =====")
        print(audit_path.read_text(encoding="utf-8"))
        print(f"===== HAVENLINE SOURCE AUDIT END {audit_relative} =====")

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print("HAVENLINE imported visual payloads verified and applied")
print(project)
