#!/usr/bin/env python3
"""Apply the reviewed native integration only to the exact frozen source inputs.

Patch header whitespace/counts are transport metadata; Git recalculates counts.
All source files must match the independently recorded before AND after SHA-256.
No installed catalog, approved PDF, source image, or signing configuration changes.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply.py <recovered-source-directory>")
    target = Path(sys.argv[1]).resolve(strict=True)
    here = Path(__file__).resolve().parent
    manifest = json.loads((here / "manifest.json").read_text())
    assert manifest["schema"] == 1
    patches = []
    for part in manifest["patches"]:
        assert Path(part["name"]).name == part["name"]
        data = (here / part["name"]).read_bytes()
        assert sha(data) == part["sha256"], f"Patch bytes changed: {part['name']}"
        patches.append(data)
    raw = b"".join(patches)
    assert sha(raw) == manifest["patchSha256"], "Combined patch changed"
    for row in manifest["files"]:
        path = (target / row["path"]).resolve()
        assert target in path.parents, "Integration path escapes recovered source"
        if row["before"] is None:
            assert not path.exists(), f"New source already exists: {row['path']}"
        else:
            assert path.is_file() and sha(path.read_bytes()) == row["before"], f"Source baseline changed: {row['path']}"
    # Normalize only file-header transport whitespace. Code bytes stay hash-locked.
    patch = raw.replace(b"\n diff --git ", b"\ndiff --git ")
    with tempfile.NamedTemporaryFile(suffix=".patch") as temp:
        temp.write(patch)
        temp.flush()
        command = ["git", "apply", "--recount", "--whitespace=error", "--directory=" + target.name]
        subprocess.run(command + ["--check", temp.name], cwd=target.parent, check=True)
        subprocess.run(command + [temp.name], cwd=target.parent, check=True)
    for row in manifest["files"]:
        assert sha((target / row["path"]).read_bytes()) == row["after"], f"Integrated source mismatch: {row['path']}"
    print(json.dumps({"schema": 1, "filesVerified": len(manifest["files"]),
                      "patchSha256": manifest["patchSha256"], "phase7Complete": False}, indent=2))


if __name__ == "__main__":
    main()
