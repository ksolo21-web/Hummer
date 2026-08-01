#!/usr/bin/env python3
"""Align the decluttered HAVENLINE scene with the production runtime density gate."""
from __future__ import annotations

from pathlib import Path


def apply(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    source = path.read_text(encoding="utf-8")
    old = "    for index in range(8):\n"
    aligned = "    for index in range(10):\n"

    old_count = source.count(old)
    aligned_count = source.count(aligned)
    if old_count == 1 and aligned_count == 0:
        path.write_text(source.replace(old, aligned, 1), encoding="utf-8")
    elif old_count == 0 and aligned_count == 1:
        pass
    else:
        raise SystemExit(
            "HAVENLINE density alignment expected one 8-node loop to upgrade or one "
            f"already-aligned 10-node loop; found 8-node={old_count}, 10-node={aligned_count}"
        )

    print("HAVENLINE runtime density aligned: 10 decluttered resource nodes")
