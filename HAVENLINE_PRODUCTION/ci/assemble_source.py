#!/usr/bin/env python3
"""Reconstruct, verify, and refine the clean HAVENLINE production project."""
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
from visual_refinement import apply as apply_visual_refinement  # noqa: E402
from visual_final import apply as apply_visual_final  # noqa: E402

legacy_path = ci_dir / "production_patches.py"
legacy_spec = importlib.util.spec_from_file_location("havenline_legacy_production_patches", legacy_path)
if legacy_spec is None or legacy_spec.loader is None:
    raise SystemExit(f"HAVENLINE could not load legacy production patches: {legacy_path}")
legacy_module = importlib.util.module_from_spec(legacy_spec)
legacy_spec.loader.exec_module(legacy_module)
legacy_module.apply(project)

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

apply_visual_refinement(project)
apply_visual_final(project)

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print("HAVENLINE checksum-pinned visual payloads verified and refined")
print(project)
