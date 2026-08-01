#!/usr/bin/env python3
"""Align the decluttered HAVENLINE scene with the production runtime density gate."""
from __future__ import annotations

from pathlib import Path


def apply(project: Path) -> None:
    path = project / "scripts" / "main.gd"
    source = path.read_text(encoding="utf-8")
    old = "    for index in range(8):\n"
    new = "    for index in range(10):\n"
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            "HAVENLINE density alignment expected exactly one post-final resource loop, "
            f"found {count}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
    print("HAVENLINE runtime density aligned: 10 decluttered resource nodes")
