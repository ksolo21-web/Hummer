#!/usr/bin/env python3
from __future__ import annotations

import base64
import gzip
import hashlib
import runpy
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
parts_dir = Path(__file__).resolve().parent / "reference_slice_parts"
part_paths = [parts_dir / f"part-{index:02d}.b64" for index in range(5)]
missing = [str(path) for path in part_paths if not path.is_file()]
if missing:
    raise SystemExit(f"HAVENLINE reference payload parts missing: {missing}")
encoded = "".join(path.read_text(encoding="utf-8") for path in part_paths)
if hashlib.sha256(encoded.encode("utf-8")).hexdigest() != "2934dd6fbf7fe16d72cb092a190995887764900fb30fa0af30a2f033cfd4bb00":
    raise SystemExit("HAVENLINE reference payload checksum mismatch")
script_bytes = gzip.decompress(base64.b64decode(encoded, validate=True))
if hashlib.sha256(script_bytes).hexdigest() != "236317e2f358a5d2a047972e082ef194d63c876d403f3d876e059e748b8a63b3":
    raise SystemExit("HAVENLINE reference patch checksum mismatch")
script_path = root / "build" / "patch_reference_vertical_slice.py"
script_path.parent.mkdir(parents=True, exist_ok=True)
script_path.write_bytes(script_bytes)
old_argv = sys.argv
try:
    sys.argv = [str(script_path), str(root)]
    runpy.run_path(str(script_path), run_name="__main__")
finally:
    sys.argv = old_argv
print("HAVENLINE reference vertical slice applied and checksum verified")
