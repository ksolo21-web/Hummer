#!/usr/bin/env python3
"""Apply the release asset-composition fix to HAVENLINE's pinned fetcher."""
from __future__ import annotations

import sys
from pathlib import Path

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
source = source.replace(
    "from typing import Iterable, Sequence\n",
    "from typing import Iterable, Sequence\n\nfrom final_composites import write_final_composites\n",
)
for line in (
    '    Selection("crate", "survival", ("crate", "woodbox", "box"), ("crate",), ("ammo", "icon")),\n',
    '    Selection("fence", "survival", ("fence", "barricade"), ("fence",), ("icon",)),\n',
):
    if line not in source:
        raise SystemExit(f"HAVENLINE asset selection changed; missing expected line: {line.strip()}")
    source = source.replace(line, "", 1)
needle = "    copy_selected_pack_assets(extracted, selected, manifest)\n\n    furnace = "
replacement = (
    "    copy_selected_pack_assets(extracted, selected, manifest)\n"
    "    manifest.update(write_final_composites(FINAL, manifest[\"log\"], manifest[\"backpack\"]))\n"
    "    report[\"sources\"].append({\n"
    "        \"name\": \"HAVENLINE final supply-cache and log-barricade compositions\",\n"
    "        \"source\": \"Generated from the pinned CC0 survival pack assets\",\n"
    "        \"license\": \"CC0 1.0 source assets; HAVENLINE scene assembly\",\n"
    "    })\n\n"
    "    furnace = "
)
if needle not in source:
    raise SystemExit("HAVENLINE environment fetch layout changed; refusing an unsafe patch")
source = source.replace(needle, replacement, 1)
path.write_text(source, encoding="utf-8")
