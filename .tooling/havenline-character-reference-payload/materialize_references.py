#!/usr/bin/env python3
"""Rebuild approved HAVENLINE character reference images from text-safe payloads.

The connector used to maintain this branch cannot write binary repository files directly.
This script turns the reviewed, checksum-pinned Base64 payloads back into the exact
binary inputs consumed by the character generation workflow.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path


SPECS = {
    "Character1": {
        "parts": 3,
        "output": "Character1.jpg",
        "bytes": 17937,
        "sha256": "36dd42d6d8f80651140958396742d37d14ae2c2676e167fe162a8d96b51df77f",
        "format": "JPEG",
    },
    "Character2": {
        "parts": 2,
        "output": "Character2.jpg",
        "bytes": 10762,
        "sha256": "a3b492768c5d0bf9fb8cfc9cc294500590987f51e9b4c0668b8125537e7d72b8",
        "format": "WEBP",
    },
    "Character3": {
        "parts": 2,
        "output": "Character3.jpg",
        "bytes": 11506,
        "sha256": "f51c249f4a1a904c40edb0cd35d1bfed0fbdfeb64c4f17dd052cc9b8a27c3d85",
        "format": "WEBP",
    },
    "Character4": {
        "parts": 2,
        "output": "Character4.jpg",
        "bytes": 11508,
        "sha256": "2341399aefa304670c90835dfb14e24e31997d51d5ba2ae90a755d6e1ffcb671",
        "format": "WEBP",
    },
}


def decode_character(payload_root: Path, output_root: Path, character: str, spec: dict) -> dict:
    encoded_parts: list[str] = []
    source_parts: list[str] = []
    for index in range(1, int(spec["parts"]) + 1):
        path = payload_root / f"{character}.part{index:02d}.b64"
        if not path.is_file():
            raise FileNotFoundError(f"Missing approved reference payload part: {path}")
        encoded_parts.append("".join(path.read_text(encoding="ascii").split()))
        source_parts.append(str(path))

    try:
        binary = base64.b64decode("".join(encoded_parts), validate=True)
    except Exception as exc:
        raise RuntimeError(f"Invalid Base64 payload for {character}: {exc}") from exc

    digest = hashlib.sha256(binary).hexdigest()
    expected_digest = str(spec["sha256"])
    expected_bytes = int(spec["bytes"])
    if len(binary) != expected_bytes:
        raise RuntimeError(
            f"{character} byte count mismatch: expected {expected_bytes}, got {len(binary)}"
        )
    if digest != expected_digest:
        raise RuntimeError(
            f"{character} SHA-256 mismatch: expected {expected_digest}, got {digest}"
        )

    output_root.mkdir(parents=True, exist_ok=True)
    destination = output_root / str(spec["output"])
    destination.write_bytes(binary)
    return {
        "character": character,
        "destination": str(destination),
        "format": spec["format"],
        "bytes": len(binary),
        "sha256": digest,
        "payloadParts": source_parts,
        "verified": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--payload-root",
        default=str(Path(__file__).resolve().parent),
        help="Directory containing CharacterN.partNN.b64 files",
    )
    parser.add_argument(
        "--output-root",
        default=".tooling/havenline-character-production/references",
        help="Directory where verified binary references are materialized",
    )
    args = parser.parse_args()

    payload_root = Path(args.payload_root)
    output_root = Path(args.output_root)
    results = [
        decode_character(payload_root, output_root, character, spec)
        for character, spec in SPECS.items()
    ]
    report = {
        "schemaVersion": 1,
        "approvedReferenceCount": len(results),
        "allVerified": all(item["verified"] for item in results),
        "references": results,
    }
    report_path = output_root / "materialization-report.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
