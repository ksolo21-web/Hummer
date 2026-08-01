#!/usr/bin/env python3
"""Apply HAVENLINE's release-only final asset fixes to the pinned fetcher."""
from __future__ import annotations

import subprocess
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
    'WOLF_WHEEL_URL = "https://files.pythonhosted.org/packages/16/a0/'
    '3a0a2b8ee12d27e64a898a6a5f08820029d12afa36b794e663cc53537c32/'
    'animasim-0.2.1-py3-none-any.whl"\n'
    'WOLF_WHEEL_SHA256 = "d111ffc9782f09872846b09ac7868d41e126ef72e3153d9d0173d401be3277a3"\n'
    'WOLF_WHEEL_BYTES = 13917433\n'
    'WOLF_ARCHIVE_PATH = "animasim/_assets/glb/wolf.glb"\n'
    'WOLF_SHA256 = "aa06297d0e66568711885178d1d35e2ca1e392dceb05f988df0497de0274a705"\n'
    'WOLF_BYTES = 1984192\n'
)
if constant_needle not in source:
    raise SystemExit("HAVENLINE furnace URL layout changed; refusing an unsafe patch")
source = source.replace(constant_needle, constant_replacement, 1)

needle = "    copy_selected_pack_assets(extracted, selected, manifest)\n\n    furnace = "
replacement = (
    "    copy_selected_pack_assets(extracted, selected, manifest)\n"
    "    manifest.update(write_final_composites(FINAL, manifest[\"log\"], manifest[\"backpack\"]))\n"
    "    report[\"sources\"].append({\n"
    "        \"name\": \"HAVENLINE final supply-cache and log-barricade compositions\",\n"
    "        \"source\": \"Generated from the pinned CC0 survival pack assets\",\n"
    "        \"license\": \"CC0 1.0 source assets; HAVENLINE scene assembly\",\n"
    "    })\n\n"
    "    wolf_wheel = download(WOLF_WHEEL_URL, CACHE / \"animasim-0.2.1-py3-none-any.whl\")\n"
    "    actual_wheel_sha = sha256(wolf_wheel)\n"
    "    if wolf_wheel.stat().st_size != WOLF_WHEEL_BYTES or actual_wheel_sha != WOLF_WHEEL_SHA256:\n"
    "        raise AssetError(\n"
    "            f\"Pinned AnimaSim wheel mismatch: expected {WOLF_WHEEL_BYTES} bytes/{WOLF_WHEEL_SHA256}, \"\n"
    "            f\"got {wolf_wheel.stat().st_size} bytes/{actual_wheel_sha}\"\n"
    "        )\n"
    "    wolf = FINAL / \"animals\" / \"wolf.glb\"\n"
    "    wolf.parent.mkdir(parents=True, exist_ok=True)\n"
    "    with zipfile.ZipFile(wolf_wheel) as archive:\n"
    "        try:\n"
    "            info = archive.getinfo(WOLF_ARCHIVE_PATH)\n"
    "        except KeyError as exc:\n"
    "            raise AssetError(f\"Pinned wolf path missing from AnimaSim wheel: {WOLF_ARCHIVE_PATH}\") from exc\n"
    "        if info.file_size != WOLF_BYTES:\n"
    "            raise AssetError(f\"Pinned wolf size mismatch in wheel: expected {WOLF_BYTES}, got {info.file_size}\")\n"
    "        with archive.open(info) as source_handle, wolf.open(\"wb\") as output_handle:\n"
    "            shutil.copyfileobj(source_handle, output_handle)\n"
    "    actual_wolf_sha = sha256(wolf)\n"
    "    if wolf.stat().st_size != WOLF_BYTES or actual_wolf_sha != WOLF_SHA256:\n"
    "        raise AssetError(\n"
    "            f\"Pinned Ultimate Animated Animals wolf mismatch: expected {WOLF_BYTES} bytes/{WOLF_SHA256}, \"\n"
    "            f\"got {wolf.stat().st_size} bytes/{actual_wolf_sha}\"\n"
    "        )\n"
    "    manifest[\"wolf\"] = \"res://assets/final/animals/wolf.glb\"\n"
    "    report[\"sources\"].append({\n"
    "        \"name\": \"Quaternius Ultimate Animated Animal Pack - Wolf\",\n"
    "        \"source\": WOLF_WHEEL_URL,\n"
    "        \"package\": \"animasim==0.2.1\",\n"
    "        \"wheel_sha256\": actual_wheel_sha,\n"
    "        \"model_sha256\": actual_wolf_sha,\n"
    "        \"animation_clips\": 12,\n"
    "        \"license\": \"CC0 1.0 model; redistributed by AnimaSim\",\n"
    "    })\n\n"
    "    furnace = "
)
if needle not in source:
    raise SystemExit("HAVENLINE environment fetch layout changed; refusing an unsafe patch")
source = source.replace(needle, replacement, 1)
path.write_text(source, encoding="utf-8")

preflight_patch = Path(__file__).with_name("patch_preflight.py")
preflight_path = path.with_name("preflight.py")
subprocess.run([sys.executable, str(preflight_patch), str(preflight_path)], check=True)
