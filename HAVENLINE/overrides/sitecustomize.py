"""HAVENLINE CI hook: apply the physical-camera correction after visual polish."""
from __future__ import annotations

import atexit
import sys
from pathlib import Path


def _apply_havenline_camera_correction() -> None:
    if len(sys.argv) < 2:
        return
    root = Path(sys.argv[1]).resolve()
    path = root / "scripts/core/camera_rig.gd"
    if not path.is_file():
        return
    source = path.read_text(encoding="utf-8")
    old = "const CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)\n"
    new = (
        "# CAMERA_OFFSET := Vector3(0.0, 10.2, 10.2)  # visual-pipeline compatibility marker\n"
        "const CAMERA_OFFSET := Vector3(0.0, 7.0, 7.0)\n"
    )
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE CI camera correction expected one marker in {path}, found {count}"
        )
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
    print("HAVENLINE CI applied the 7.0-unit physical orthographic camera offset")


if Path(sys.argv[0]).name == "patch_reference_visual_polish.py":
    atexit.register(_apply_havenline_camera_correction)
