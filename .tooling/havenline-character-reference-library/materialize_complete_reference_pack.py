#!/usr/bin/env python3
"""Materialize HAVENLINE's canonical complete character-reference pack.

The four approved turnaround sheets already live in the repo. Additional generated
reference sheets are stored as checksum-pinned Base64 payloads because the connector
writes UTF-8 text. The output folder is the single Codex-facing source of truth.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import shutil
from pathlib import Path

TURNAROUNDS = [
    {"path": "Character1/Turnaround.jpg", "source": "HAVENLINE_UNITY/Reference/Characters/Approved/Character1.jpg", "identity": "Black woman with glasses and side-part bob"},
    {"path": "Character2/Turnaround.webp", "source": "HAVENLINE_UNITY/Reference/Characters/Approved/Character2.webp", "identity": "Black man with beard and glasses"},
    {"path": "Character3/Turnaround.webp", "source": "HAVENLINE_UNITY/Reference/Characters/Approved/Character3.webp", "identity": "Young woman with loose curls"},
    {"path": "Character4/Turnaround.webp", "source": "HAVENLINE_UNITY/Reference/Characters/Approved/Character4.webp", "identity": "Young woman with high curls/headband"},
]

PAYLOADS = [
    {"key":"extra01","path":"Character1/Expressions.webp","parts":2,"bytes":16226,"sha256":"3d32e633b8b5e167cb6940f73a13e1bb4c65c629fa9bdb7b64b1cd4b9a43b80f","width":600,"height":425},
    {"key":"extra02","path":"Character2/Expressions.webp","parts":2,"bytes":15430,"sha256":"b2aecc125f8a916e74ca6c974b3eadeb6e1bea7eb31470dcead1e557d582c8f6","width":600,"height":425},
    {"key":"extra03","path":"Character3/Expressions.webp","parts":2,"bytes":16550,"sha256":"914ad69a5e58cd0086df66153ce637fbfd5ada1a0866494f918e1a31dd11c37d","width":600,"height":425},
    {"key":"extra04","path":"Character4/Expressions.webp","parts":2,"bytes":16096,"sha256":"e2349a3e1603c4e9008566fd6b73553aa85bb52b03e58a915c5086695249ac92","width":600,"height":425},
    {"key":"extra05","path":"Shared/Gear_Outfit_Material_Callouts.webp","parts":3,"bytes":19488,"sha256":"f93db94e327f67d39244bebb69915be7d4e5d5f7dc04fc6c7a73e5c5aee9548c","width":600,"height":425},
    {"key":"extra06","path":"Shared/Gameplay_Camera_Readability.webp","parts":0,"bytes":8382,"sha256":"c951cb2fa74b70b66b0ceea5d1fbfeee4234ae32b15f0a0b09cf66261034f25f","width":360,"height":255},
    {"key":"extra07","path":"Shared/Character_Select_UI.webp","parts":0,"bytes":8184,"sha256":"525fb10a3124cbc887351c60f5c21b15afecdb8a6641c076ed6e0a4e0d367e2e","width":320,"height":569},
    {"key":"extra08","path":"Shared/Onboarding_Crew_UI.webp","parts":0,"bytes":8134,"sha256":"1dc8e002079be680d4b17e77e2b0431127f20bbc160fdddec3b81ce62efb1201","width":480,"height":340},
]


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _decode_payload(payload_root: Path, spec: dict) -> tuple[bytes, list[str]]:
    if spec["parts"]:
        paths = [payload_root / f'{spec["key"]}.part{n:02d}.b64' for n in range(1, spec["parts"] + 1)]
    else:
        paths = [payload_root / f'{spec["key"]}.b64']
    for path in paths:
        if not path.is_file():
            raise SystemExit(f"missing payload: {path}")
    encoded = "".join("".join(path.read_text(encoding="ascii").split()) for path in paths)
    return base64.b64decode(encoded, validate=True), [str(path) for path in paths]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--payload-root", default=str(Path(__file__).resolve().parent))
    ap.add_argument("--output-root", default="HAVENLINE_UNITY/Reference/Characters/Complete")
    args = ap.parse_args()
    payload_root = Path(args.payload_root)
    output_root = Path(args.output_root)
    results = []

    for spec in TURNAROUNDS:
        src = Path(spec["source"])
        if not src.is_file():
            raise SystemExit(f"missing approved turnaround: {src}")
        dest = output_root / spec["path"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        data = dest.read_bytes()
        results.append({
            "path": spec["path"],
            "kind": "turnaround",
            "identity": spec["identity"],
            "source": spec["source"],
            "bytes": len(data),
            "sha256": _sha256(data),
            "verified": True,
        })

    for spec in PAYLOADS:
        data, payload_paths = _decode_payload(payload_root, spec)
        digest = _sha256(data)
        if len(data) != spec["bytes"]:
            raise SystemExit(f'byte mismatch for {spec["path"]}: {len(data)} != {spec["bytes"]}')
        if digest != spec["sha256"]:
            raise SystemExit(f'checksum mismatch for {spec["path"]}: {digest}')
        if not (data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
            raise SystemExit(f'not a WebP payload: {spec["path"]}')
        dest = output_root / spec["path"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        results.append({**spec, "kind": "reference", "payloadFiles": payload_paths, "verified": True})

    report = {
        "schemaVersion": 3,
        "referenceAssetCount": len(results),
        "heroCharacterCount": 4,
        "allVerified": len(results) == 12 and all(item["verified"] for item in results),
        "canonicalIdentityMapping": {
            "Character1": "Black woman with glasses and side-part bob",
            "Character2": "Black man with beard and glasses",
            "Character3": "Young woman with loose curls",
            "Character4": "Young woman with high curls/headband",
        },
        "correctionNote": "Legacy generated expression-sheet title text for Characters 1 and 2 was swapped. Canonical identity mapping, not the old embedded title, controls folder assignment.",
        "assets": results,
    }
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "materialization-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    if not report["allVerified"]:
        raise SystemExit("complete reference pack verification failed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
