#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
from typing import Any

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def load_json(path: pathlib.Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"Expected JSON object in {path}")
    return payload


def git_output(*arguments: str) -> str:
    completed = subprocess.run(
        ["git", *arguments],
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bind a generated SF3D package to the exact checked-out HAVENLINE commit."
    )
    parser.add_argument("--character", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()

    root = pathlib.Path(args.directory)
    status_path = root / "sf3d-pipeline-status.json"
    generation_path = root / "generation-report.json"
    if not status_path.is_file():
        raise FileNotFoundError(status_path)
    if not generation_path.is_file():
        raise FileNotFoundError(generation_path)

    commit = git_output("rev-parse", "HEAD").lower()
    branch = git_output("rev-parse", "--abbrev-ref", "HEAD")
    if not SHA_PATTERN.fullmatch(commit):
        raise RuntimeError(f"Git returned an invalid commit SHA: {commit!r}")
    if branch not in {"havenline-unity-reference-rebuild", "HEAD"}:
        raise RuntimeError(
            f"Character generation must check out the HAVENLINE branch or detached exact commit; found {branch!r}"
        )

    status = load_json(status_path)
    generation = load_json(generation_path)
    if status.get("character") != args.character:
        raise RuntimeError("SF3D status character does not match the binding request")
    if generation.get("character") != args.character:
        raise RuntimeError("Generation report character does not match the binding request")
    if status.get("machinePassed") is not True:
        raise RuntimeError("Cannot bind a HAVENLINE source commit to a failed SF3D package")
    if status.get("approved") is not False or status.get("unityIntegrated") is not False:
        raise RuntimeError("Cannot bind a package that was prematurely promoted")

    binding = {
        "repository": "ksolo21-web/Hummer",
        "branch": "havenline-unity-reference-rebuild",
        "commit": commit,
        "workingTreeClean": not bool(git_output("status", "--porcelain")),
    }
    if not binding["workingTreeClean"]:
        raise RuntimeError("HAVENLINE checkout changed during character generation")

    status["havenlineSource"] = binding
    generation["havenlineSource"] = binding
    status_path.write_text(json.dumps(status, indent=2) + "\n", encoding="utf-8")
    generation_path.write_text(json.dumps(generation, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(binding, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
