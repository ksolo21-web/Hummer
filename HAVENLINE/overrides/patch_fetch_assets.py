#!/usr/bin/env python3
"""Apply HAVENLINE's release-only final asset fixes to the pinned fetcher."""
from __future__ import annotations

import sys
from pathlib import Path

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
source = source.replace(
    "from typing import Iterable, Sequence\n",
    "from typing import Iterable, Sequence\n\nfrom final_composites import write_final_composites\n",
)

animals_download = '    "animals": "https://opengameart.org/sites/default/files/Animals%20Pack%20by%20Quaternius.zip",\n'
if animals_download not in source:
    raise SystemExit("HAVENLINE animal download definition changed; refusing an unsafe patch")
source = source.replace(animals_download, "", 1)

for line in (
    '    Selection("crate", "survival", ("crate", "woodbox", "box"), ("crate",), ("ammo", "icon")),\n',
    '    Selection("fence", "survival", ("fence", "barricade"), ("fence",), ("icon",)),\n',
    '    Selection("wolf", "animals", ("wolf",), ("wolf",), ("icon", "texture")),\n',
):
    if line not in source:
        raise SystemExit(f"HAVENLINE asset selection changed; missing expected line: {line.strip()}")
    source = source.replace(line, "", 1)

constant_needle = 'FURNACE_URL = "https://raw.githubusercontent.com/ToxSam/cc0-models-Polygonal-Mind/main/projects/christmas/Fireplace.glb"\n'
constant_replacement = constant_needle + (
    'WOLF_URL = "https://raw.githubusercontent.com/DakotaRo/godotDemos/'
    '854fcf198867644b2f9854cf00bf40459bad892f/First3D/Animals_Pack/GLTF/Wolf.glb"\n'
    'WOLF_GIT_BLOB = "40254606da4f22c30c037c7fd195c6cbfa1ac834"\n'
)
if constant_needle not in source:
    raise SystemExit("HAVENLINE furnace URL layout changed; refusing an unsafe patch")
source = source.replace(constant_needle, constant_replacement, 1)

helper_needle = 'def download(url: str, destination: Path, retries: int = 4) -> Path:\n'
helper = '''def git_blob_sha1(path: Path) -> str:\n    payload = path.read_bytes()\n    digest = hashlib.sha1()  # Git object identity, not a security checksum.\n    digest.update(f"blob {len(payload)}\\0".encode("ascii"))\n    digest.update(payload)\n    return digest.hexdigest()\n\n\n'''
if helper_needle not in source:
    raise SystemExit("HAVENLINE download helper layout changed; refusing an unsafe patch")
source = source.replace(helper_needle, helper + helper_needle, 1)

needle = "    copy_selected_pack_assets(extracted, selected, manifest)\n\n    furnace = "
replacement = (
    "    copy_selected_pack_assets(extracted, selected, manifest)\n"
    "    manifest.update(write_final_composites(FINAL, manifest[\"log\"], manifest[\"backpack\"]))\n"
    "    report[\"sources\"].append({\n"
    "        \"name\": \"HAVENLINE final supply-cache and log-barricade compositions\",\n"
    "        \"source\": \"Generated from the pinned CC0 survival pack assets\",\n"
    "        \"license\": \"CC0 1.0 source assets; HAVENLINE scene assembly\",\n"
    "    })\n\n"
    "    wolf = download(WOLF_URL, FINAL / \"animals\" / \"wolf.glb\")\n"
    "    actual_wolf_blob = git_blob_sha1(wolf)\n"
    "    if actual_wolf_blob != WOLF_GIT_BLOB:\n"
    "        raise AssetError(f\"Pinned wolf Git blob mismatch: expected {WOLF_GIT_BLOB}, got {actual_wolf_blob}\")\n"
    "    manifest[\"wolf\"] = \"res://assets/final/animals/wolf.glb\"\n"
    "    report[\"sources\"].append({\n"
    "        \"name\": \"Quaternius Ultimate Animated Animal Pack - Wolf\",\n"
    "        \"source\": WOLF_URL,\n"
    "        \"mirror_commit\": \"854fcf198867644b2f9854cf00bf40459bad892f\",\n"
    "        \"git_blob\": actual_wolf_blob,\n"
    "        \"license\": \"CC0 1.0\",\n"
    "    })\n\n"
    "    furnace = "
)
if needle not in source:
    raise SystemExit("HAVENLINE environment fetch layout changed; refusing an unsafe patch")
source = source.replace(needle, replacement, 1)
path.write_text(source, encoding="utf-8")
