#!/usr/bin/env python3
"""Reconstruct, verify, and patch the clean HAVENLINE production project."""
from __future__ import annotations

import base64
import hashlib
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

sys.path.insert(0, str(repo / "HAVENLINE_PRODUCTION" / "ci"))
from production_patches import apply  # noqa: E402

apply(project)

print(
    f"HAVENLINE production source verified: {manifest['file_count']} files, "
    f"{len(parts)} parts, {manifest['archive_sha256']}"
)
print(project)
