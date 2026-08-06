#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import shutil

from pygltflib import GLTF2


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Select, verify, and record a Stable Fast 3D HAVENLINE output."
    )
    parser.add_argument("--character", required=True)
    parser.add_argument("--sf3d-output", required=True)
    parser.add_argument("--prepared-input", required=True)
    parser.add_argument("--destination", required=True)
    parser.add_argument("--generator-commit", required=True)
    parser.add_argument("--gpu-name", required=True)
    parser.add_argument("--gpu-vram-mib", required=True, type=int)
    args = parser.parse_args()

    source_root = pathlib.Path(args.sf3d_output)
    prepared_input = pathlib.Path(args.prepared_input)
    destination_root = pathlib.Path(args.destination)
    destination_root.mkdir(parents=True, exist_ok=True)

    candidates = sorted(
        (path for path in source_root.rglob("mesh.glb") if path.stat().st_size > 10_000),
        key=lambda path: path.stat().st_size,
        reverse=True,
    )
    if not candidates:
        raise FileNotFoundError(f"Stable Fast 3D produced no valid mesh.glb under {source_root}")

    selected = candidates[0]
    payload = selected.read_bytes()
    if payload[:4] != b"glTF":
        raise RuntimeError(f"Stable Fast 3D output is not a binary GLB: {selected}")

    raw = destination_root / f"{args.character}_raw.glb"
    shutil.copy2(selected, raw)
    source_copy = destination_root / "sf3d_source"
    if source_copy.exists():
        shutil.rmtree(source_copy)
    shutil.copytree(source_root, source_copy)

    gltf = GLTF2().load_binary(str(raw))
    input_report_path = prepared_input.with_suffix(".json")
    input_report = (
        json.loads(input_report_path.read_text(encoding="utf-8"))
        if input_report_path.is_file()
        else None
    )
    report = {
        "schemaVersion": 6,
        "character": args.character,
        "generator": "Stability-AI/stable-fast-3d",
        "generatorCommit": args.generator_commit,
        "generatorLicense": "Stability AI Community License",
        "commercialRegistrationRequired": True,
        "sourceMode": "self-hosted-rtx-sf3d",
        "singleView": True,
        "preparedInput": str(prepared_input),
        "preparedInputSha256": sha256(prepared_input),
        "preparedInputReport": input_report,
        "gpu": {
            "name": args.gpu_name,
            "vramMiB": args.gpu_vram_mib,
            "minimumRequiredMiB": 6144,
        },
        "textureResolution": 2048,
        "remeshOption": "triangle",
        "targetVertexCount": 42000,
        "success": True,
        "selectedSourceGlb": str(selected),
        "selectedGlb": str(raw),
        "selectedBytes": raw.stat().st_size,
        "selectedSha256": sha256(raw),
        "materials": len(gltf.materials or []),
        "textures": len(gltf.textures or []),
        "images": len(gltf.images or []),
        "requiresMeshSanitization": True,
        "humanVisualApprovalRequired": True,
        "unityIntegrated": False,
    }
    (destination_root / "generation-report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
